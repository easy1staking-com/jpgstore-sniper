package com.easy1staking.jpgstore.sniper.model;

import com.easy1staking.jpgstore.sniper.model.onchain.Address;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MerkleSnipe extends Snipe {

    private final List<String> nfts;

    public MerkleSnipe(String txHash,
                       int outputIndex,
                       String ownerPkh,
                       String nftDestination,
                       List<String> nfts,
                       String targetHash,
                       Long maxPrice,
                       Long protocolFee) {
        super(txHash, outputIndex, ownerPkh, nftDestination, targetHash, maxPrice, protocolFee);
        this.nfts = nfts;
    }

}
