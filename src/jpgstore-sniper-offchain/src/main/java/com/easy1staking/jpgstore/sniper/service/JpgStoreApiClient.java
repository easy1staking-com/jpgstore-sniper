package com.easy1staking.jpgstore.sniper.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class JpgStoreApiClient {

    private static final String BASE_URL = "https://server.jpgstoreapis.com";

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JpgStoreApiClient() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("x-jpgstore-csrf-protection", "1")
                .build();
    }

    public Optional<JsonNode> fetchCollection(String policyId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/collection/{targetHash}", policyId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Failed to fetch collection {} from jpg.store API", policyId, e);
            return Optional.empty();
        }
    }

    public Optional<JsonNode> fetchToken(String assetId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/token/{assetId}", assetId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Failed to fetch token {} from jpg.store API", assetId, e);
            return Optional.empty();
        }
    }

    public Optional<JsonNode> fetchCollectionTraits(String policyId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/collection/{policyId}/traits", policyId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Failed to fetch traits for collection {} from jpg.store API", policyId, e);
            return Optional.empty();
        }
    }

    public Optional<JsonNode> searchTokensByTraits(String policyId, Map<String, List<String>> traits, int page, int size) {
        try {
            String traitsJson = objectMapper.writeValueAsString(traits);
            String traitsBase64 = Base64.getEncoder().encodeToString(traitsJson.getBytes());

            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/tokens")
                            .queryParam("policyIds", policyId)
                            .queryParam("traits", traitsBase64)
                            .queryParam("pagination", page)
                            .queryParam("size", size)
                            .queryParam("saleType", "default")
                            .queryParam("sortBy", "price-low-to-high")
                            .queryParam("listingTypes", "ALL_LISTINGS")
                            .queryParam("verified", "default")
                            .queryParam("onlyMainBundleAsset", "false")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Failed to search tokens by traits for collection {} from jpg.store API", policyId, e);
            return Optional.empty();
        }
    }
}
