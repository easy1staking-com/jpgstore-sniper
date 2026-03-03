package com.easy1staking.jpgstore.sniper.service;

import com.easy1staking.jpgstore.sniper.model.TraitSearchResult;
import com.easy1staking.jpgstore.sniper.model.TraitSearchToken;
import com.easy1staking.jpgstore.sniper.model.entity.CollectionTrait;
import com.easy1staking.jpgstore.sniper.model.entity.NftTokenTrait;
import com.easy1staking.jpgstore.sniper.repository.CollectionTraitRepository;
import com.easy1staking.jpgstore.sniper.repository.NftTokenTraitRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CollectionTraitService {

    private final CollectionTraitRepository collectionTraitRepository;
    private final NftTokenTraitRepository nftTokenTraitRepository;
    private final JpgStoreApiClient jpgStoreApiClient;
    private final NftCollectionService nftCollectionService;
    private final NftTokenService nftTokenService;

    public List<CollectionTrait> getTraits(String policyId) {
        List<CollectionTrait> cached = collectionTraitRepository.findByPolicyId(policyId);
        if (!cached.isEmpty()) {
            log.debug("Traits for collection {} found in DB ({} entries)", policyId, cached.size());
            return cached;
        }

        log.info("Traits for collection {} not in DB, fetching from jpg.store API", policyId);
        return fetchAndSaveTraits(policyId);
    }

    public List<CollectionTrait> getTraitValues(String policyId, String category) {
        // Ensure traits are cached first
        getTraits(policyId);
        return collectionTraitRepository.findByPolicyIdAndCategory(policyId, category);
    }

    @Transactional
    public List<CollectionTrait> refreshTraits(String policyId, RefreshMode mode) {
        if (mode == RefreshMode.REPLACE) {
            log.info("Replacing all traits for collection {}", policyId);
            collectionTraitRepository.deleteByPolicyId(policyId);
            return fetchAndSaveTraits(policyId);
        }

        // ADDITIVE mode — upsert
        log.info("Additively refreshing traits for collection {}", policyId);
        List<CollectionTrait> existing = collectionTraitRepository.findByPolicyId(policyId);
        Map<String, CollectionTrait> existingMap = new HashMap<>();
        for (CollectionTrait trait : existing) {
            existingMap.put(trait.getCategory() + "|" + trait.getValue(), trait);
        }

        List<CollectionTrait> fetched = fetchTraitsFromApi(policyId);
        List<CollectionTrait> toSave = new ArrayList<>();

        for (CollectionTrait fetched_trait : fetched) {
            String key = fetched_trait.getCategory() + "|" + fetched_trait.getValue();
            CollectionTrait existingTrait = existingMap.get(key);
            if (existingTrait != null) {
                existingTrait.setNftCount(fetched_trait.getNftCount());
                toSave.add(existingTrait);
            } else {
                toSave.add(fetched_trait);
            }
        }

        return collectionTraitRepository.saveAll(toSave);
    }

    @Transactional
    public void saveNftTraits(String assetId, Map<String, String> traits) {
        if (nftTokenService.getToken(assetId).isEmpty()) {
            log.warn("Could not resolve token {} — skipping trait persistence", assetId);
            return;
        }

        nftTokenTraitRepository.deleteByAssetId(assetId);

        List<NftTokenTrait> entities = traits.entrySet().stream()
                .filter(e -> !"traitcount".equalsIgnoreCase(e.getKey()))
                .map(e -> NftTokenTrait.builder()
                        .assetId(assetId)
                        .category(e.getKey())
                        .value(e.getValue())
                        .build())
                .toList();

        nftTokenTraitRepository.saveAll(entities);
    }

    public List<NftTokenTrait> getNftTraits(String assetId) {
        return nftTokenTraitRepository.findByAssetId(assetId);
    }

    @Transactional
    public TraitSearchResult searchByTraits(String policyId, Map<String, List<String>> traitFilters, int page, int size) {
        return jpgStoreApiClient.searchTokensByTraits(policyId, traitFilters, page, size)
                .map(json -> parseSearchResponse(json, page))
                .orElse(new TraitSearchResult(List.of(), 0, page));
    }

    private List<CollectionTrait> fetchAndSaveTraits(String policyId) {
        List<CollectionTrait> traits = fetchTraitsFromApi(policyId);
        if (!traits.isEmpty()) {
            ensureCollectionExists(policyId);
            return collectionTraitRepository.saveAll(traits);
        }
        return traits;
    }

    private void ensureCollectionExists(String policyId) {
        nftCollectionService.getCollection(policyId);
    }

    private List<CollectionTrait> fetchTraitsFromApi(String policyId) {
        return jpgStoreApiClient.fetchCollectionTraits(policyId)
                .map(json -> parseCollectionTraits(policyId, json))
                .orElse(List.of());
    }

    private List<CollectionTrait> parseCollectionTraits(String policyId, JsonNode json) {
        List<CollectionTrait> traits = new ArrayList<>();

        Iterator<String> categories = json.fieldNames();
        while (categories.hasNext()) {
            String category = categories.next();
            if ("traitcount".equalsIgnoreCase(category)) {
                continue;
            }

            JsonNode valuesNode = json.get(category);
            Iterator<String> values = valuesNode.fieldNames();
            while (values.hasNext()) {
                String value = values.next();
                int count = valuesNode.get(value).asInt(0);

                traits.add(CollectionTrait.builder()
                        .policyId(policyId)
                        .category(category)
                        .value(value)
                        .nftCount(count)
                        .build());
            }
        }

        log.info("Parsed {} trait entries for collection {}", traits.size(), policyId);
        return traits;
    }

    private TraitSearchResult parseSearchResponse(JsonNode json, int page) {
        List<TraitSearchToken> tokens = new ArrayList<>();

        JsonNode tokensNode = json.path("tokens");
        if (tokensNode.isArray()) {
            for (JsonNode tokenNode : tokensNode) {
                String assetId = tokenNode.path("asset_id").asText(null);
                String displayName = tokenNode.path("display_name").asText(null);
                Long listingLovelace = tokenNode.has("listing_lovelace") && !tokenNode.get("listing_lovelace").isNull()
                        ? tokenNode.get("listing_lovelace").asLong()
                        : null;

                Map<String, String> traits = new HashMap<>();
                JsonNode traitsNode = tokenNode.path("traits");
                if (!traitsNode.isMissingNode()) {
                    Iterator<String> fieldNames = traitsNode.fieldNames();
                    while (fieldNames.hasNext()) {
                        String key = fieldNames.next();
                        if (!"traitcount".equalsIgnoreCase(key)) {
                            traits.put(key, traitsNode.get(key).asText());
                        }
                    }
                }

                tokens.add(new TraitSearchToken(assetId, displayName, listingLovelace, traits));

                // Persist per-NFT traits
                if (assetId != null && !traits.isEmpty()) {
                    saveNftTraits(assetId, traits);
                }
            }
        }

        int total = json.path("pagination").path("total").asInt(0);

        return new TraitSearchResult(tokens, total, page);
    }
}
