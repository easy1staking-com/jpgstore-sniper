package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

public record Address(Credential paymentKeyHash, Credential stakeKeyHash) {

    public PlutusData toPlutusData() {

        PlutusData stakeKey;
        if (stakeKeyHash == null) {
            stakeKey = ConstrPlutusData.of(1);
        } else {
            stakeKey = ConstrPlutusData.of(0,
                    ConstrPlutusData.of(0,
                            ConstrPlutusData.of(getAlternative(stakeKeyHash),
                                    BytesPlutusData.of(stakeKeyHash.getBytes())
                            )));
        }

        return ConstrPlutusData.of(0,
                ConstrPlutusData.of(getAlternative(paymentKeyHash),
                        BytesPlutusData.of(paymentKeyHash.getBytes())
                ),
                stakeKey
        );

    }

    private int getAlternative(Credential paymentKeyHash) {
        return switch (paymentKeyHash.getType()) {
            case Key -> 0;
            case Script -> 1;
        };
    }

}
