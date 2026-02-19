package com.easy1staking.jpgstore.sniper.service;

import com.easy1staking.jpgstore.sniper.model.entity.NftCollection;
import com.easy1staking.jpgstore.sniper.repository.NftCollectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NftCollectionService {

    private final NftCollectionRepository nftCollectionRepository;
    private final JpgStoreApiClient jpgStoreApiClient;

    public Optional<NftCollection> getCollection(String policyId) {
        Optional<NftCollection> cached = nftCollectionRepository.findById(policyId);
        if (cached.isPresent()) {
            log.debug("Collection {} found in DB", policyId);
            return cached;
        }

        log.info("Collection {} not in DB, fetching from jpg.store API", policyId);
        return jpgStoreApiClient.fetchCollection(policyId)
                .map(json -> json.path("collection"))
                .filter(collection -> !collection.isMissingNode())
                .map(collection -> {
                    NftCollection entity = NftCollection.builder()
                            .policyId(policyId)
                            .displayName(textOrDefault(collection, "display_name", policyId))
                            .description(textOrNull(collection, "description"))
                            .build();
                    return nftCollectionRepository.save(entity);
                });
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
