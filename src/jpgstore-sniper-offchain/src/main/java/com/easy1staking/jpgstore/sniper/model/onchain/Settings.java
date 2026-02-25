package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Builder;

@Builder(toBuilder = true)
public record Settings(int operatorFeePct,
                       int protocolFeePct,
                       long minOperatorFee,
                       long minProtocolFee,
                       Address protocolTreasury,
                       Credential stakeCredential,
                       long txFeeBudget,
                       String adminPkh) {

    public PlutusData toPlutusData() {

        var stakeCredentialAlt = switch (stakeCredential.getType()) {
            case Key -> 0;
            case Script -> 1;
        };

        return ConstrPlutusData.of(0,
                BigIntPlutusData.of(operatorFeePct),
                BigIntPlutusData.of(protocolFeePct),
                BigIntPlutusData.of(minOperatorFee),
                BigIntPlutusData.of(minProtocolFee),
                protocolTreasury.toPlutusData(),
                ConstrPlutusData.of(stakeCredentialAlt, BytesPlutusData.of(stakeCredential.getBytes())),
                BigIntPlutusData.of(txFeeBudget),
                BytesPlutusData.of(HexUtil.decodeHexString(adminPkh)));

    }

}