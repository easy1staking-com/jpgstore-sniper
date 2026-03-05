package com.easy1staking.jpgstore.sniper.model;

import lombok.Data;

@Data
public class Snipe {

    private final String txHash;

    private final int outputIndex;

    private final String ownerPkh;

    private final String nftDestination;

    // The PolicyId or MerkleTreeRootHash to match against
    private final String targetHash;

    private final Long maxPrice;

    private final Long protocolFee;

}
