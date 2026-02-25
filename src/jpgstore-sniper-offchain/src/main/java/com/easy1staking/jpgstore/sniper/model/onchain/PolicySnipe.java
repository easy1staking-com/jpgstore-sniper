package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Builder;

@Builder(toBuilder = true)
public record PolicySnipe(String ownerPkh,
                          Address nftDestination,
                          String policyId,
                          Long maxPrice,
                          Long protocolFee) {

    public PlutusData toPlutusData() {
        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(ownerPkh)),
                nftDestination.toPlutusData(),
                BytesPlutusData.of(HexUtil.decodeHexString(policyId)),
                BigIntPlutusData.of(maxPrice),
                BigIntPlutusData.of(protocolFee));
    }
}
