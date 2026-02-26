package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Builder;

@Builder(toBuilder = true)
public record SnipeDatum(String ownerPkh,
                         Address nftDestination,
                         // The PolicyId or MerkleTreeRootHash to match against
                         String targetHash,
                         Long maxPrice,
                         Long protocolFee) {

    public PlutusData toPlutusData() {
        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(ownerPkh)),
                nftDestination.toPlutusData(),
                BytesPlutusData.of(HexUtil.decodeHexString(targetHash)),
                BigIntPlutusData.of(maxPrice),
                BigIntPlutusData.of(protocolFee));
    }
}
