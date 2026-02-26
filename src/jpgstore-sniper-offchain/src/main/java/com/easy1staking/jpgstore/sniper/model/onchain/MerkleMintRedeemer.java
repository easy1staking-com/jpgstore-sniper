package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.List;

public record MerkleMintRedeemer(List<String> nfts) {

    public PlutusData toPlutusData() {
        var nftsByteList = nfts.stream().map(foo -> BytesPlutusData.of(HexUtil.decodeHexString(foo))).toList();
        return ConstrPlutusData.of(0,
                ListPlutusData.of(nftsByteList.toArray(new BytesPlutusData[]{}))
        );
    }


}
