package com.easy1staking.jpgstore.sniper.model;

import java.util.List;

public record TraitSearchResult(
        List<TraitSearchToken> tokens,
        int total,
        int page
) {}
