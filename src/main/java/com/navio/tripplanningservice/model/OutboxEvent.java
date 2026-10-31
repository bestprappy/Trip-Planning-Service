package com.navio.tripplanningservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox row for the {@code trip.events.v1} topic.
 *
 * <p>The id is the event id consumers deduplicate on. It is assigned when the
 * row is staged and never changes, so a relay retry or an operator replay sends
 * the same identity again rather than a new event.
 */
@Entity
@Table(name = "outbox", schema = "trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** Versioned name, for example {@code SharedPlanListed.v1}. */
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    /** Kafka record key: every event for one entity lands on one partition, in order. */
    @Column(name = "partition_key", nullable = false, updatable = false, length = 100)
    private String partitionKey;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_jsonb", nullable = false, updatable = false)
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Null until the broker acknowledged the record. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    /** Failed sends back off; the relay skips a row until this time. */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (nextAttemptAt == null) nextAttemptAt = createdAt;
        if (payload == null) payload = new HashMap<>();
    }
}
