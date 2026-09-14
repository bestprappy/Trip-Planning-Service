package com.navio.tripplanningservice.integration.mobility;

import com.navio.tripplanningservice.service.PlaceResolutionUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

@Component
public class RestMobilityPlaceClient implements MobilityPlaceClient {

    private static final String MOBILITY_SERVICE_ID = "mobility-and-ev-service";

    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;
    private final URI fallbackBaseUrl;

    public RestMobilityPlaceClient(
            RestClient.Builder restClientBuilder,
            DiscoveryClient discoveryClient,
            @Value("${navio.mobility.base-url:http://localhost:8083}") URI fallbackBaseUrl
    ) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
        this.fallbackBaseUrl = fallbackBaseUrl;
    }

    @Override
    public MobilityPlaceDetail getDetail(String placeId) {
        URI baseUrl = discoveryClient.getInstances(MOBILITY_SERVICE_ID).stream()
                .findFirst().map(ServiceInstance::getUri).orElse(fallbackBaseUrl);
        try {
            var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                    java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build());
            factory.setReadTimeout(java.time.Duration.ofSeconds(15));
            var response = restClientBuilder.clone().baseUrl(baseUrl.toString()).requestFactory(factory)
                    .build().get().uri("/v1/geo/places/{id}", placeId)
                    .retrieve().body(MobilityPlaceDetail.class);
            if (response == null || response.name() == null || response.location() == null
                    || response.location().lat() == null || response.location().lng() == null
                    || response.placeLocation() == null) {
                throw new PlaceResolutionUnavailableException("Mobility returned incomplete place details");
            }
            return response;
        } catch (RestClientException exception) {
            throw new PlaceResolutionUnavailableException("Place resolution is unavailable", exception);
        }
    }
}
