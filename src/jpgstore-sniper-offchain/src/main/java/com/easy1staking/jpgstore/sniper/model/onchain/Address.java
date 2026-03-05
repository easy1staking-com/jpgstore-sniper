package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Optional;

public record Address(Credential paymentKeyHash, Credential stakeKeyHash) {

    public com.bloxbean.cardano.client.address.Address toAddress(Network network) {
        if (stakeKeyHash == null) {
            return AddressProvider.getEntAddress(paymentKeyHash, network);
        } else {
            return AddressProvider.getBaseAddress(paymentKeyHash, stakeKeyHash, network);
        }
    }

    public static Optional<Address> fromAddress(com.bloxbean.cardano.client.address.Address address) {
        return switch (address.getAddressType()) {
            case Base -> address.getPaymentCredential()
                    .flatMap(paymentCredential -> address.getDelegationCredential()
                            .map(stakingCredentials -> new Address(paymentCredential, stakingCredentials)));
            case Enterprise -> address.getPaymentCredential()
                    .map(paymentCredential -> new Address(paymentCredential, null));
            default -> Optional.empty();
        };
    }

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
