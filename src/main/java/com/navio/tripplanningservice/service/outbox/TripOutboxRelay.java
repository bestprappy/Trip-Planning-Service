package com.navio.tripplanningservice.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.model.OutboxEvent;
import com.navio.tripplanningservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import com.navio.tripplanningservice.config.OutboxConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes staged {@code trip.events.v1} rows to Kafka.
 *
 * <p>Rows are claimed with {@code SKIP LOCKED}, so replicas never send the same
 * row concurrently. A row is stamped published only after the broker
 * acknowledges it. Delivery is at-least-once: an acknowledgement that arrives
 * after the transaction fails is re-sent with the same event id, which consumers
 * deduplicate.
 *
 * <p>A failed send backs off exponentially and ends the cycle, because the next
 * row would almost certainly fail the same way while holding row locks. After
 * {@link #MAX_ATTEMPTS} a row stops retrying and is counted by
 * {@code navio.outbox.stuck} for an operator, who can reset it to replay with its
 * original id.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "navio.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class TripOutboxRelay {

    static final int BATCH_SIZE = 50;
    static final int MAX_ATTEMPTS = 12;
    static final String PRODUCER = "trip-planning-service";
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String topic;
    private final Duration retention;
    private final Clock clock;

    public TripOutboxRelay(OutboxEventRepository outbox,
                           @Qualifier(OutboxConfiguration.TRIP_EVENTS_TEMPLATE) KafkaTemplate<String, String> kafka,
                           ObjectMapper json,
                           @Value("${navio.outbox.topic:trip.events.v1}") String topic,
                           @Value("${navio.outbox.retention:P14D}") Duration retention,
                           Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
        this.topic = topic;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${navio.outbox.relay.interval-ms:2000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outbox.claimDue(clock.instant(), MAX_ATTEMPTS, PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            if (!publish(event)) return;
        }
    }

    /** Acknowledged rows are kept long enough to replay after a consumer outage, then removed. */
    @Scheduled(cron = "${navio.outbox.cleanup-cron:0 17 3 * * *}")
    @Transactional
    public void purgePublished() {
        int removed = outbox.deletePublishedBefore(clock.instant().minus(retention));
        if (removed > 0) log.info("Removed {} published trip outbox rows older than {}", removed, retention);
    }

    /** @return false when the send failed and the cycle should stop */
    boolean publish(OutboxEvent event) {
        String body;
        try {
            body = json.writeValueAsString(envelope(event));
        } catch (JsonProcessingException exception) {
            // A payload that cannot serialise never will; park it for an operator.
            event.setAttemptCount(MAX_ATTEMPTS);
            event.setLastError(truncate("Payload could not be serialised: " + exception.getOriginalMessage()));
            outbox.save(event);
            log.error("Trip outbox event {} ({}) is unserialisable and was parked", event.getId(), event.getEventType());
            return true;
        }
        try {
            kafka.send(topic, event.getPartitionKey(), body).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(event, "Interrupted while waiting for the broker");
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                    ? exception.getCause() : exception;
            recordFailure(event, cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return false;
        }
        event.setPublishedAt(clock.instant());
        event.setLastError(null);
        outbox.save(event);
        return true;
    }

    private Map<String, Object> envelope(OutboxEvent event) {
        Object trace = event.getPayload().get("traceId");
        return Map.of(
                "eventId", event.getId().toString(),
                "eventType", event.getEventType(),
                "occurredAt", event.getCreatedAt().toString(),
                "producer", PRODUCER,
                "partitionKey", event.getPartitionKey(),
                "traceId", trace instanceof String value ? value : event.getId().toString(),
                "payload", event.getPayload());
    }

    private void recordFailure(OutboxEvent event, String message) {
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        event.setLastError(truncate(message));
        event.setNextAttemptAt(clock.instant().plus(backoff(attempts)));
        outbox.save(event);
        log.warn("Trip outbox event {} ({}) failed to publish, attempt {}/{}",
                event.getId(), event.getEventType(), attempts, MAX_ATTEMPTS);
    }

    static Duration backoff(int attempts) {
        long seconds = FIRST_BACKOFF.toSeconds() << Math.min(attempts - 1, 16);
        return seconds >= MAX_BACKOFF.toSeconds() ? MAX_BACKOFF : Duration.ofSeconds(seconds);
    }

    private static String truncate(String message) {
        if (message == null) return "unknown error";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
