package com.easy1staking.jpgstore.sniper.controller;

import com.easy1staking.jpgstore.sniper.model.entity.NftCollection;
import com.easy1staking.jpgstore.sniper.service.NftCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${apiPrefix}/collections")
@Slf4j
@RequiredArgsConstructor
public class NftCollectionController {

    private final NftCollectionService nftCollectionService;

    @GetMapping("/{policyId}")
    public ResponseEntity<NftCollection> getCollection(@PathVariable String policyId) {
        return nftCollectionService.getCollection(policyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
