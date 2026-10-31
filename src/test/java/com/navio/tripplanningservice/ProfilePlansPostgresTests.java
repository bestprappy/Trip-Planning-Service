package com.navio.tripplanningservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navio.tripplanningservice.integration.mobility.MobilityPlaceClient;
import com.navio.tripplanningservice.integration.mobility.MobilityPlaceDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Profile plan listings and pins over real HTTP and PostgreSQL: only plans listed
 * under the owner's name appear, pins are owner-only, and stopping sharing takes
 * a plan (and its pin) off the profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "NAVIO_TEST_DB_URL", matches = ".+")
class ProfilePlansPostgresTests {

    @Value("${local.server.port}") int port;
    @MockitoBean MobilityPlaceClient places;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void onlyNamedListingsAppearAndPinsFollowTheOwnersSharingChoices() throws Exception {
        lenient().when(places.getDetail(anyString())).thenReturn(new MobilityPlaceDetail("Kyoto",
                new MobilityPlaceDetail.Coordinates(35.01, 135.76), new MobilityPlaceDetail.PlaceLocation("Kyoto", "Kyoto", "JP", "Japan")));
        String owner = UUID.randomUUID().toString();
        String stranger = UUID.randomUUID().toString();

        String named = publish(owner, "Named plan", "Kanya", true);
        String anonymous = publish(owner, "Anonymous plan", null, true);
        String linkOnly = publish(owner, "Link only", "Kanya", false);

        var listed = request("GET", "/v1/shared-plans/by-author/" + owner, stranger, null, 200).path("content");
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).path("plan").path("token").asText()).isEqualTo(named);
        assertThat(listed.get(0).path("pinned").asBoolean()).isFalse();
        assertThat(listed.toString()).doesNotContain(anonymous).doesNotContain(linkOnly);

        assertThat(request("GET", "/v1/shared-plans/" + named + "/author", stranger, null, 200)
                .path("accountId").asText()).isEqualTo(owner);
        request("GET", "/v1/shared-plans/" + anonymous + "/author", stranger, null, 404);
        request("GET", "/v1/shared-plans/" + linkOnly + "/author", stranger, null, 404);

        request("PUT", "/v1/shared-plans/" + named + "/pin", stranger, null, 404);
        request("PUT", "/v1/shared-plans/" + linkOnly + "/pin", owner, null, 409);
        request("PUT", "/v1/shared-plans/" + named + "/pin", owner, null, 200);
        assertThat(request("GET", "/v1/shared-plans/by-author/" + owner, stranger, null, 200)
                .path("content").get(0).path("pinned").asBoolean()).isTrue();

        String tripId = tripOf.get(named);
        request("DELETE", "/v1/trips/" + tripId + "/publication", owner, null, 204);
        assertThat(request("GET", "/v1/shared-plans/by-author/" + owner, stranger, null, 200).path("content")).isEmpty();
        request("GET", "/v1/shared-plans/" + named + "/author", stranger, null, 404);
    }

    private final java.util.Map<String, String> tripOf = new java.util.HashMap<>();

    private String publish(String owner, String title, String byline, boolean listed) throws Exception {
        var create = json.createObjectNode().put("displayName", title).put("startDate", "2030-04-01")
                .put("endDate", "2030-04-02").put("destinationId", "review-kyoto");
        var trip = request("POST", "/v1/trips", owner, create, 201);
        String id = trip.path("id").asText();
        ObjectNode publication = json.createObjectNode().put("expectedTripVersion", trip.path("version").asLong())
                .put("listInExplore", listed).put("title", title);
        if (byline != null) publication.put("authorDisplayName", byline);
        publication.set("options", json.createObjectNode().put("includeDates", false).put("includeNotes", false)
                .put("includeBudget", false));
        String token = request("PUT", "/v1/trips/" + id + "/publication", owner, publication, 200).path("token").asText();
        tripOf.put(token, id);
        return token;
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
