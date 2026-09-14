package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.integration.mobility.MobilityPlaceClient;
import com.navio.tripplanningservice.integration.mobility.MobilityPlaceDetail;
import com.navio.tripplanningservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/** One pass with a stable ID cursor; failed places are retried on the next invocation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripLocationBackfill {
    private final TripRepository repository;
    private final MobilityPlaceClient places;
    private final PlatformTransactionManager transactionManager;

    @FunctionalInterface
    public interface RateLimiter { void await() throws InterruptedException; }

    public int run(int batchSize, int maxTrips, RateLimiter limiter) throws InterruptedException {
        if (batchSize < 1 || batchSize > 1000 || maxTrips < 1 || maxTrips > 100000) {
            throw new IllegalArgumentException("Invalid backfill limits");
        }
        var cache = new HashMap<String, Optional<MobilityPlaceDetail>>();
        var transaction = new TransactionTemplate(transactionManager);
        UUID cursor = new UUID(0, 0);
        int visited = 0;
        int updated = 0;
        while (visited < maxTrips) {
            var batch = repository.findUnresolvedAfter(cursor, Math.min(batchSize, maxTrips - visited));
            if (batch.isEmpty()) break;
            for (var candidate : batch) {
                cursor = candidate.getId();
                visited++;
                String placeId = candidate.getDestinationId();
                if (placeId == null || placeId.isBlank() || placeId.startsWith("guest-")) continue;
                var detail = cache.get(placeId);
                if (detail == null) {
                    limiter.await();
                    try {
                        detail = Optional.of(places.getDetail(placeId));
                    } catch (PlaceResolutionUnavailableException ex) {
                        log.warn("Skipping unresolved trip destination for trip {}", candidate.getId());
                        detail = Optional.empty();
                    }
                    cache.put(placeId, detail);
                }
                if (detail.isEmpty()) continue;
                var resolved = detail.get();
                try {
                    Boolean changed = transaction.execute(status -> {
                        var current = repository.findById(candidate.getId()).orElse(null);
                        if (current == null || current.getDestinationCountryCode() != null
                                || !placeId.equals(current.getDestinationId())) return false;
                        TripService.applyDestination(current, resolved);
                        repository.saveAndFlush(current);
                        return true;
                    });
                    if (Boolean.TRUE.equals(changed)) updated++;
                } catch (ObjectOptimisticLockingFailureException ex) {
                    log.warn("Skipping concurrently edited trip {}", candidate.getId());
                }
            }
        }
        log.info("Trip location backfill visited {} trips and updated {}", visited, updated);
        return updated;
    }
}
