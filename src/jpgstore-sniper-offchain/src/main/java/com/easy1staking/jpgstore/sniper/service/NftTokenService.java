package com.easy1staking.jpgstore.sniper.service;

import com.easy1staking.jpgstore.sniper.model.entity.NftCollection;
import com.easy1staking.jpgstore.sniper.model.entity.NftToken;
import com.easy1staking.jpgstore.sniper.repository.NftTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NftTokenService {

    private final NftTokenRepository nftTokenRepository;
    private final NftCollectionService nftCollectionService;
    private final JpgStoreApiClient jpgStoreApiClient;

    public Optional<NftToken> getToken(String assetId) {
        Optional<NftToken> cached = nftTokenRepository.findById(assetId);
        if (cached.isPresent()) {
            log.debug("Token {} found in DB", assetId);
            return cached;
        }

        log.info("Token {} not in DB, fetching from jpg.store API", assetId);
        return jpgStoreApiClient.fetchToken(assetId)
                .filter(json -> json.has("policy_id"))
                .flatMap(json -> {
                    String policyId = json.get("policy_id").asText();

                    Optional<NftCollection> collection = nftCollectionService.getCollection(policyId);
                    if (collection.isEmpty()) {
                        log.warn("Could not resolve collection {} for token {}", policyId, assetId);
                        return Optional.empty();
                    }

                    NftToken entity = NftToken.builder()
                            .assetId(assetId)
                            .collection(collection.get())
                            .displayName(textOrDefault(json, "display_name", assetId))
                            .image(extractImage(json))
                            .optimizedSource(textOrNull(json, "optimized_source"))
                            .mediatype(textOrNull(json, "mediatype"))
                            .build();
                    return Optional.of(nftTokenRepository.save(entity));
                });
    }

    private String extractImage(JsonNode json) {
        JsonNode metadata = json.get("onchain_metadata");
        if (metadata != null && !metadata.isNull()) {
            JsonNode image = metadata.get("image");
            if (image != null && !image.isNull()) {
                return image.asText();
            }
        }
        return null;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : defaultValue;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }
}
