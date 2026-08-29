package com.navio.tripplanningservice.integration.mobility;

import com.navio.tripplanningservice.service.MobilityOptimizationUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

@Component
public class RestMobilityEvOptimizationClient implements MobilityEvOptimizationClient {

    private static final String MOBILITY_SERVICE_ID = "mobility-and-ev-service";

    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;
    private final URI fallbackBaseUrl;

    public RestMobilityEvOptimizationClient(
            RestClient.Builder restClientBuilder,
            DiscoveryClient discoveryClient,
            @Value("${navio.mobility.base-url:http://localhost:8083}") URI fallbackBaseUrl
    ) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
        this.fallbackBaseUrl = fallbackBaseUrl;
    }

    @Override
    public MobilityEvOptimizationResponse optimize(MobilityEvOptimizationRequest request) {
        URI baseUrl = discoveryClient.getInstances(MOBILITY_SERVICE_ID).stream()
                .findFirst()
                .map(ServiceInstance::getUri)
                .orElse(fallbackBaseUrl);
        try {
            MobilityEvOptimizationResponse response = restClientBuilder.clone()
                    .baseUrl(baseUrl.toString())
                    .build()
                    .post()
                    .uri("/internal/v1/ev-route/optimize")
                    .body(request)
                    .retrieve()
                    .body(MobilityEvOptimizationResponse.class);
            if (response == null) {
                throw new MobilityOptimizationUnavailableException(
                        "Mobility returned an empty EV optimization response"
                );
            }
            return response;
        } catch (RestClientException exception) {
            throw new MobilityOptimizationUnavailableException(
                    "Mobility EV optimization is unavailable",
                    exception
            );
        }
    }
}
