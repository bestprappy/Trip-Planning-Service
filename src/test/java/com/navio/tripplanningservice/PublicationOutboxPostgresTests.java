package com.navio.tripplanningservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navio.tripplanningservice.integration.mobility.MobilityPlaceClient;
import com.navio.tripplanningservice.integration.mobility.MobilityPlaceDetail;
import com.navio.tripplanningservice.model.OutboxEvent;
import com.navio.tripplanningservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * The trip.events.v1 outbox over real HTTP and PostgreSQL: exactly one
 * SharedPlanListed event per newly announced plan, none for link-only,
 * anonymous or repeated updates, and none at all for a rolled-back transaction.
 * The relay is disabled so rows stay where the assertions can see them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "navio.outbox.relay.enabled=false")
@EnabledIfEnvironmentVariable(named = "NAVIO_TEST_DB_URL", matches = ".+")
class PublicationOutboxPostgresTests {

    @Value("${local.server.port}") int port;
    @MockitoBean MobilityPlaceClient places;
    @Autowired OutboxEventRepository outbox;
    @Autowired TransactionTemplate transactions;
    @Autowired javax.sql.DataSource dataSource;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> tripOf = new HashMap<>();

    @BeforeEach
    void stubPlaces() {
        lenient().when(places.getDetail(anyString())).thenReturn(new MobilityPlaceDetail("Kyoto",
                new MobilityPlaceDetail.Coordinates(35.01, 135.76), new MobilityPlaceDetail.PlaceLocation("Kyoto", "Kyoto", "JP", "Japan")));
    }

    @Test
    void onlyANewlyNamedListingStagesAnEventAndRepeatsStaySilent() throws Exception {
        String owner = UUID.randomUUID().toString();

        String named = publish(owner, "Named plan", "Kanya", true, null);
        String anonymous = publish(owner, "Anonymous plan", null, true, null);
        String linkOnly = publish(owner, "Link only", "Kanya", false, null);

        assertThat(eventsFor(named)).isEqualTo(1);
        assertThat(eventsFor(anonymous)).isZero();
        assertThat(eventsFor(linkOnly)).isZero();

        // Updating a plan that stays listed is silent, and so is unlisting it.
        publish(owner, "Named plan, revised", "Kanya", true, named);
        listing(named, false, null);
        assertThat(eventsFor(named)).isEqualTo(1);
        // Relisting is a new transition and stages a new event for the same link;
        // community's plan:<token> dedupe key keeps followers from hearing twice.
        listing(named, true, null);
        assertThat(eventsFor(named)).isEqualTo(2);

        // Naming an anonymous listing, or listing a link-only plan, announces it once.
        listing(anonymous, true, "Kanya");
        listing(linkOnly, true, null);
        assertThat(eventsFor(anonymous)).isEqualTo(1);
        assertThat(eventsFor(linkOnly)).isEqualTo(1);

        OutboxEvent event = outbox.findAll().stream()
                .filter(row -> named.equals(row.getPayload().get("token"))).findFirst().orElseThrow();
        assertThat(event.getEventType()).isEqualTo("SharedPlanListed.v1");
        assertThat(event.getPayload()).containsEntry("ownerId", owner).doesNotContainKey("title");
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void aRolledBackTransactionLeavesNoEventBehind() {
        long before = outbox.count();

        transactions.executeWithoutResult(status -> {
            outbox.saveAndFlush(OutboxEvent.builder().aggregateType("TRIP_PUBLICATION").aggregateId(UUID.randomUUID())
                    .eventType("SharedPlanListed.v1").partitionKey("rollback").payload(Map.of("version", 1)).build());
            status.setRollbackOnly();
        });

        assertThat(outbox.count()).isEqualTo(before);
    }

    @Test
    void anEventCannotBeStagedOutsideTheChangingTransaction(@Autowired com.navio.tripplanningservice.service.publication.PublicationEvents events) {
        var publication = com.navio.tripplanningservice.model.TripPublication.builder()
                .id(UUID.randomUUID()).tripId(UUID.randomUUID()).token("outside")
                .status(com.navio.tripplanningservice.model.TripPublication.PublicationStatus.ACTIVE)
                .authorDisplayName("Kanya").revision(1).build();
        publication.applyListing(true, java.time.Instant.now());

        assertThatThrownBy(() -> events.recordChange(
                com.navio.tripplanningservice.service.publication.PublicationEvents.Before.NONE, publication, UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private long eventsFor(String token) {
        return JdbcClient.create(dataSource)
                .sql("SELECT count(*) FROM trip.outbox WHERE event_type = 'SharedPlanListed.v1' AND payload_jsonb ->> 'token' = :token")
                .param("token", token).query(Long.class).single();
    }

    private String publish(String owner, String title, String byline, boolean listed, String existingToken) throws Exception {
        String id;
        long version;
        Integer revision = null;
        if (existingToken == null) {
            var create = json.createObjectNode().put("displayName", title).put("startDate", "2030-04-01")
                    .put("endDate", "2030-04-02").put("destinationId", "review-kyoto");
            JsonNode trip = request("POST", "/v1/trips", owner, create, 201);
            id = trip.path("id").asText();
            version = trip.path("version").asLong();
        } else {
            id = tripOf.get(existingToken);
            JsonNode trip = request("GET", "/v1/trips/" + id, owner, null, 200);
            version = trip.path("version").asLong();
            revision = request("GET", "/v1/trips/" + id + "/publication", owner, null, 200).path("revision").asInt();
        }
        ObjectNode publication = json.createObjectNode().put("expectedTripVersion", version)
                .put("listInExplore", listed).put("title", title);
        if (revision != null) publication.put("expectedRevision", revision);
        if (byline != null) publication.put("authorDisplayName", byline);
        publication.set("options", json.createObjectNode().put("includeDates", false).put("includeNotes", false)
                .put("includeBudget", false));
        String token = request("PUT", "/v1/trips/" + id + "/publication", owner, publication, 200).path("token").asText();
        tripOf.put(token, id);
        ownerOf.put(token, owner);
        return token;
    }

    private final Map<String, String> ownerOf = new HashMap<>();

    private void listing(String token, boolean listed, String byline) throws Exception {
        ObjectNode body = json.createObjectNode().put("listedInExplore", listed);
        if (byline != null) body.put("authorDisplayName", byline);
        request("PATCH", "/v1/trips/" + tripOf.get(token) + "/publication", ownerOf.get(token), body, 200);
    }

    private JsonNode request(String method, String path, String user, JsonNode body, int status) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (user != null) builder.header("X-User-Id", user);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path + " " + response.body()).isEqualTo(status);
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }
}
