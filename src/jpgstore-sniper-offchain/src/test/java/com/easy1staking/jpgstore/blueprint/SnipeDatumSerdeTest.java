package com.easy1staking.jpgstore.blueprint;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Credential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.PolicyId;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ReferencedCredential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.sniper.blueprint.types.model.PolicySnipeDatum;
import com.easy1staking.jpgstore.sniper.blueprint.types.model.impl.PolicySnipeDatumData;
import com.easy1staking.jpgstore.sniper.model.onchain.Address;
import com.easy1staking.jpgstore.sniper.model.onchain.SnipeDatum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;

@Slf4j
public class SnipeDatumSerdeTest {

    @Test
    public void testSnipeDatumSerialization() {
        var ownerAccount = new Account();
        var ownerAddress = ownerAccount.getBaseAddress();
        var ownerPkh = HexUtil.encodeHexString(ownerAddress.getPaymentCredentialHash().get());
        var collectionPolicyId = "a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559";
        var maxPrice = 9_000_000L;
        var protocolFee = 1_000_000L;

        var nftDestination = Address.fromAddress(ownerAddress).get();

        // Internal serialisation
        var internalDatum = SnipeDatum.builder()
                .ownerPkh(ownerPkh)
                .nftDestination(nftDestination)
                .targetHash(collectionPolicyId)
                .maxPrice(maxPrice)
                .protocolFee(protocolFee)
                .build();

        var internalPlutusData = internalDatum.toPlutusData();

        // CCL blueprint serialisation
        var cclDatum = new PolicySnipeDatumData();
        cclDatum.setOwnerPkh(VerificationKeyHash.of(ownerAddress.getPaymentCredentialHash().get()));
        cclDatum.setNftDestination(toCclAddress(ownerAddress));
        cclDatum.setPolicyId(PolicyId.of(HexUtil.decodeHexString(collectionPolicyId)));
        cclDatum.setMaxPrice(BigInteger.valueOf(maxPrice));
        cclDatum.setProtocolFee(BigInteger.valueOf(protocolFee));

        var cclPlutusData = cclDatum.toPlutusData();

        log.info("Internal: {}", internalPlutusData);
        log.info("CCL:      {}", cclPlutusData);

        Assertions.assertEquals(internalPlutusData, cclPlutusData);
    }

    @Test
    public void testSnipeDatumDeserialization() {
        var ownerAccount = new Account();
        var ownerAddress = ownerAccount.getBaseAddress();
        var ownerPkh = HexUtil.encodeHexString(ownerAddress.getPaymentCredentialHash().get());
        var collectionPolicyId = "a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559";
        var maxPrice = 9_000_000L;
        var protocolFee = 1_000_000L;

        var nftDestination = Address.fromAddress(ownerAddress).get();

        // Build from internal model and serialise to CBOR hex
        var internalDatum = SnipeDatum.builder()
                .ownerPkh(ownerPkh)
                .nftDestination(nftDestination)
                .targetHash(collectionPolicyId)
                .maxPrice(maxPrice)
                .protocolFee(protocolFee)
                .build();

        var cborHex = internalDatum.toPlutusData().serializeToHex();

        // Deserialise via CCL blueprint
        var cclDatum = PolicySnipeDatumData.deserialize(cborHex);

        Assertions.assertArrayEquals(ownerAddress.getPaymentCredentialHash().get(), cclDatum.getOwnerPkh().bytes());
        Assertions.assertArrayEquals(HexUtil.decodeHexString(collectionPolicyId), cclDatum.getPolicyId().bytes());
        Assertions.assertEquals(BigInteger.valueOf(maxPrice), cclDatum.getMaxPrice());
        Assertions.assertEquals(BigInteger.valueOf(protocolFee), cclDatum.getProtocolFee());

        // Verify address round-trip
        var cclAddress = cclDatum.getNftDestination();
        Assertions.assertArrayEquals(
                ownerAddress.getPaymentCredentialHash().get(),
                cclAddress.getPaymentCredential().getHash());
        Assertions.assertTrue(cclAddress.getStakeCredential().isPresent());
    }

    @Test
    public void testSnipeDatumEnterpriseAddress() {
        // Enterprise address (no stake key)
        var ownerAccount = new Account();
        var ownerAddress = com.bloxbean.cardano.client.address.AddressProvider.getEntAddress(
                ownerAccount.getBaseAddress().getPaymentCredential().get(),
                com.bloxbean.cardano.client.common.model.Networks.testnet());
        var ownerPkh = HexUtil.encodeHexString(ownerAddress.getPaymentCredentialHash().get());
        var collectionPolicyId = "a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559";

        var nftDestination = Address.fromAddress(ownerAddress).get();

        var internalDatum = SnipeDatum.builder()
                .ownerPkh(ownerPkh)
                .nftDestination(nftDestination)
                .targetHash(collectionPolicyId)
                .maxPrice(5_000_000L)
                .protocolFee(500_000L)
                .build();

        var internalPlutusData = internalDatum.toPlutusData();

        var cclDatum = new PolicySnipeDatumData();
        cclDatum.setOwnerPkh(VerificationKeyHash.of(ownerAddress.getPaymentCredentialHash().get()));
        cclDatum.setNftDestination(toCclAddress(ownerAddress));
        cclDatum.setPolicyId(PolicyId.of(HexUtil.decodeHexString(collectionPolicyId)));
        cclDatum.setMaxPrice(BigInteger.valueOf(5_000_000L));
        cclDatum.setProtocolFee(BigInteger.valueOf(500_000L));

        Assertions.assertEquals(internalPlutusData, cclDatum.toPlutusData());

        // Verify no stake credential in the address
        var cborHex = internalPlutusData.serializeToHex();
        var deserialized = PolicySnipeDatumData.deserialize(cborHex);
        Assertions.assertTrue(deserialized.getNftDestination().getStakeCredential().isEmpty());
    }

    private com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address toCclAddress(
            com.bloxbean.cardano.client.address.Address address) {
        var pkh = Credential.verificationKey(address.getPaymentCredentialHash().get());
        Optional<ReferencedCredential> skhOpt = address.getDelegationCredentialHash()
                .map(hash -> new ReferencedCredential.Inline(Credential.verificationKey(hash)));
        return new com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address(pkh, skhOpt);
    }
}
