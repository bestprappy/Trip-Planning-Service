package com.navio.tripplanningservice.integration.mobility;

import com.navio.tripplanningservice.service.MobilityOptimizationUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestMobilityEvOptimizationClientTests {
    @Test void sendsWholeTripPrecisionContractToTheVersionedEndpoint() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var discovery = mock(DiscoveryClient.class);
        when(discovery.getInstances(anyString())).thenReturn(List.of());
        var client = new RestMobilityEvOptimizationClient(builder, discovery, URI.create("http://mobility.test"));
        var stop = new MobilityEvOptimizationRequest.Stop("charger", "Charger",13,100,null,false,null,null,null,5,5.125,68.125);
        var request = new MobilityEvOptimizationRequest("day", List.of(stop),null,80,12,80,20,List.of(),
                List.of(new MobilityEvOptimizationRequest.Day("next", List.of(stop))));
        server.expect(requestTo("http://mobility.test/internal/v1/ev-route/optimize-canonical-v2"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.followingDays[0].stops[0].chargingDurationMinutes").value(5.125))
                .andExpect(jsonPath("$.followingDays[0].stops[0].plannedDepartureSocPct").value(68.125))
                .andRespond(withSuccess("{\"blockId\":\"day\",\"feasible\":true,\"operations\":[],\"finalSocPct\":68.125,\"totalDrivingSeconds\":0,\"totalChargingMinutes\":5.125,\"message\":\"test\",\"warnings\":[]}",MediaType.APPLICATION_JSON));
        assertThat(client.optimize(request).finalSocPct()).isEqualTo(68.125);
        server.verify();
    }
    @Test void oldMobilityCannotSilentlyDropWholeTripContext() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var discovery = mock(DiscoveryClient.class);
        when(discovery.getInstances(anyString())).thenReturn(List.of());
        var client = new RestMobilityEvOptimizationClient(builder, discovery, URI.create("http://mobility.test"));
        server.expect(requestTo("http://mobility.test/internal/v1/ev-route/optimize-canonical-v2"))
                .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.optimize(new MobilityEvOptimizationRequest("day",List.of(),null,80,12,80,20)))
                .isInstanceOf(MobilityOptimizationUnavailableException.class);
        server.verify();
    }
}
