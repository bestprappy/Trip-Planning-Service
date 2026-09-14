package com.navio.tripplanningservice.config;

import com.navio.tripplanningservice.service.TripLocationBackfill;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "navio.trip-location-backfill.enabled", havingValue = "true")
public class TripLocationBackfillRunner implements ApplicationRunner {
    private final TripLocationBackfill backfill;
    private final int batchSize;
    private final int maxTrips;
    private final long delayMillis;

    public TripLocationBackfillRunner(TripLocationBackfill backfill,
            @Value("${navio.trip-location-backfill.batch-size:100}") int batchSize,
            @Value("${navio.trip-location-backfill.max-trips:100000}") int maxTrips,
            @Value("${navio.trip-location-backfill.delay-millis:250}") long delayMillis) {
        this.backfill = backfill;
        this.batchSize = batchSize;
        this.maxTrips = maxTrips;
        if (delayMillis < 100) throw new IllegalArgumentException("Backfill delay must be at least 100ms");
        this.delayMillis = delayMillis;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        try {
            backfill.run(batchSize, maxTrips, () -> Thread.sleep(delayMillis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
}
