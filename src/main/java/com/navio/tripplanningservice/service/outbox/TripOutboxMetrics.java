package com.navio.tripplanningservice.service.outbox;

import com.navio.tripplanningservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Outbox health for Prometheus: backlog size, rows that gave up retrying, and the
 * age of the oldest unsent event. A growing age means Kafka or the relay is down.
 */
@Component
public class TripOutboxMetrics {

    public TripOutboxMetrics(ObjectProvider<MeterRegistry> registries, OutboxEventRepository outbox, Clock clock) {
        MeterRegistry registry = registries.getIfAvailable();
        if (registry == null) return;
        Gauge.builder("navio.outbox.pending", outbox, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Trip events staged but not yet acknowledged by Kafka")
                .register(registry);
        Gauge.builder("navio.outbox.stuck", outbox,
                        repository -> repository.countByPublishedAtIsNullAndAttemptCountGreaterThanEqual(TripOutboxRelay.MAX_ATTEMPTS))
                .description("Trip events that stopped retrying and need an operator")
                .register(registry);
        Gauge.builder("navio.outbox.oldest.age.seconds", outbox, repository -> {
                    Instant oldest = repository.oldestUnpublished();
                    return oldest == null ? 0 : Duration.between(oldest, clock.instant()).toSeconds();
                })
                .description("Age of the oldest unsent trip event")
                .register(registry);
    }
}
