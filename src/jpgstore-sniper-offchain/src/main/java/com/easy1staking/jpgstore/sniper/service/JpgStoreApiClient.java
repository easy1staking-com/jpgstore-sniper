package com.easy1staking.jpgstore.sniper.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

@Component
@Slf4j
public class JpgStoreApiClient {

    private static final String BASE_URL = "https://server.jpgstoreapis.com";

    private final WebClient webClient;

    public JpgStoreApiClient() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("x-jpgstore-csrf-protection", "1")
                .build();
    }

    public Optional<JsonNode> fetchCollection(String policyId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/collection/{policyId}", policyId)
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
}
