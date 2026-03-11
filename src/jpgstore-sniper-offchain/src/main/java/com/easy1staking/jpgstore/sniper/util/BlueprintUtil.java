package com.easy1staking.jpgstore.sniper.util;

import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Credential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ReferencedCredential;

import java.util.Optional;

public class BlueprintUtil {

    public static Optional<com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address> toOnchainAddress(com.bloxbean.cardano.client.address.Address address) {
        return address.getPaymentCredentialHash()
                .map(bytes -> {
                    var pkh = Credential.verificationKey(bytes);
                    Optional<ReferencedCredential> skhOpt = address.getDelegationCredentialHash().map(hash -> new ReferencedCredential.Inline(Credential.verificationKey(hash)));
                    return new com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address(pkh, skhOpt);
                });
    }

}
