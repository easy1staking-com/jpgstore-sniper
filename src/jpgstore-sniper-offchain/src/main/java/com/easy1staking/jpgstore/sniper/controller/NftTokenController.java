package com.easy1staking.jpgstore.sniper.controller;

import com.easy1staking.jpgstore.sniper.model.entity.NftToken;
import com.easy1staking.jpgstore.sniper.service.NftTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${apiPrefix}/tokens")
@Slf4j
@RequiredArgsConstructor
public class NftTokenController {

    private final NftTokenService nftTokenService;

    @GetMapping("/{assetId}")
    public ResponseEntity<NftToken> getToken(@PathVariable String assetId) {
        return nftTokenService.getToken(assetId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
