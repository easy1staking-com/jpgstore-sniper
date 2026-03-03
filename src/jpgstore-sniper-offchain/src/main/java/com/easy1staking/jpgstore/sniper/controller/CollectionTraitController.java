package com.easy1staking.jpgstore.sniper.controller;

import com.easy1staking.jpgstore.sniper.model.TraitSearchResult;
import com.easy1staking.jpgstore.sniper.model.entity.CollectionTrait;
import com.easy1staking.jpgstore.sniper.service.CollectionTraitService;
import com.easy1staking.jpgstore.sniper.service.RefreshMode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${apiPrefix}/collections/{policyId}/traits")
@Slf4j
@RequiredArgsConstructor
public class CollectionTraitController {

    private final CollectionTraitService collectionTraitService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<List<CollectionTrait>> getTraits(@PathVariable String policyId) {
        List<CollectionTrait> traits = collectionTraitService.getTraits(policyId);
        if (traits.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(traits);
    }

    @GetMapping("/{category}")
    public ResponseEntity<List<CollectionTrait>> getTraitValues(
            @PathVariable String policyId,
            @PathVariable String category) {
        List<CollectionTrait> values = collectionTraitService.getTraitValues(policyId, category);
        if (values.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(values);
    }

    @PostMapping("/refresh")
    public ResponseEntity<List<CollectionTrait>> refreshTraits(
            @PathVariable String policyId,
            @RequestParam(defaultValue = "REPLACE") RefreshMode mode) {
        List<CollectionTrait> traits = collectionTraitService.refreshTraits(policyId, mode);
        return ResponseEntity.ok(traits);
    }

    @GetMapping("/search")
    public ResponseEntity<TraitSearchResult> searchByTraits(
            @PathVariable String policyId,
            @RequestParam String traits,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Map<String, List<String>> traitFilters = objectMapper.readValue(
                    traits, new TypeReference<>() {});
            TraitSearchResult result = collectionTraitService.searchByTraits(policyId, traitFilters, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to parse traits filter: {}", traits, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
