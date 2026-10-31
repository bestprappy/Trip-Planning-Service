package com.navio.tripplanningservice.service.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.model.OutboxEvent;
import com.navio.tripplanningservice.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripOutboxRelayTests {

    private static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");
    private static final String TOPIC = "trip.events.v1";

    @Mock OutboxEventRepository outbox;
    @Mock KafkaTemplate<String, String> kafka;

    private final ObjectMapper json = new ObjectMapper();
    private TripOutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new TripOutboxRelay(outbox, kafka, json, TOPIC, Duration.ofDays(14), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sendsTheDocumentedEnvelopeKeyedByThePartitionKeyAndStampsItPublished() throws Exception {
        OutboxEvent event = staged("pub-1");
        when(outbox.claimDue(eq(NOW), eq(TripOutboxRelay.MAX_ATTEMPTS), any())).thenReturn(List.of(event));
        when(kafka.send(eq(TOPIC), eq("pub-1"), anyString())).thenReturn(acknowledged());

        relay.relay();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPIC), eq("pub-1"), body.capture());
        JsonNode envelope = json.readTree(body.getValue());
        assertThat(envelope.get("eventId").asText()).isEqualTo(event.getId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("SharedPlanListed.v1");
        assertThat(envelope.get("producer").asText()).isEqualTo("trip-planning-service");
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("pub-1");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo(event.getCreatedAt().toString());
        assertThat(envelope.get("traceId").asText()).isNotBlank();
        assertThat(envelope.get("payload").get("token").asText()).isEqualTo("tok-a");
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void aBrokerFailureBacksOffKeepsTheRowUnpublishedAndEndsTheCycle() {
        OutboxEvent first = staged("pub-1");
        OutboxEvent second = staged("pub-2");
        when(outbox.claimDue(any(), anyInt(), any())).thenReturn(List.of(first, second));
        when(kafka.send(eq(TOPIC), eq("pub-1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.relay();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttemptCount()).isEqualTo(1);
        assertThat(first.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(first.getLastError()).contains("broker down");
        verify(kafka, never()).send(eq(TOPIC), eq("pub-2"), anyString());
        assertThat(second.getAttemptCount()).isZero();
    }

    @Test
    void aRetryKeepsTheOriginalEventId() throws Exception {
        OutboxEvent event = staged("pub-1");
        UUID original = event.getId();
        when(outbox.claimDue(any(), anyInt(), any())).thenReturn(List.of(event));
        when(kafka.send(eq(TOPIC), eq("pub-1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .thenReturn(acknowledged());

        relay.relay();
        relay.relay();

        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq(TOPIC), eq("pub-1"), bodies.capture());
        for (String body : bodies.getAllValues()) {
            assertThat(json.readTree(body).get("eventId").asText()).isEqualTo(original.toString());
        }
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void backoffDoublesFromTwoSecondsAndCapsAtTenMinutes() {
        assertThat(TripOutboxRelay.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(TripOutboxRelay.backoff(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(TripOutboxRelay.backoff(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(TripOutboxRelay.backoff(9)).isEqualTo(Duration.ofMinutes(8).plusSeconds(32));
        assertThat(TripOutboxRelay.backoff(10)).isEqualTo(Duration.ofMinutes(10));
        assertThat(TripOutboxRelay.backoff(40)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void anEmptyBacklogSendsNothing() {
        when(outbox.claimDue(any(), anyInt(), any())).thenReturn(List.of());

        relay.relay();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void theRetentionSweepOnlyRemovesRowsAcknowledgedBeforeTheWindow() {
        relay.purgePublished();

        verify(outbox).deletePublishedBefore(NOW.minus(Duration.ofDays(14)));
    }

    private static OutboxEvent staged(String partitionKey) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TRIP_PUBLICATION")
                .aggregateId(UUID.randomUUID())
                .eventType("SharedPlanListed.v1")
                .partitionKey(partitionKey)
                .payload(Map.of("version", 1, "token", "tok-a"))
                .createdAt(NOW.minusSeconds(5))
                .nextAttemptAt(NOW.minusSeconds(5))
                .build();
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>(TOPIC, "k", "v"), metadata));
    }
}
