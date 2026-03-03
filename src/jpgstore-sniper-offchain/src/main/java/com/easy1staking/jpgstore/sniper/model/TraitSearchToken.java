package com.easy1staking.jpgstore.sniper.model;

import java.util.Map;

public record TraitSearchToken(
        String assetId,
        String displayName,
        Long listingLovelace,
        Map<String, String> traits
) {}
