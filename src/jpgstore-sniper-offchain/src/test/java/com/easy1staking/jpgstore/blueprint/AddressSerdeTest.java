package com.easy1staking.jpgstore.blueprint;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Credential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ReferencedCredential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.converter.AddressConverter;
import com.easy1staking.jpgstore.sniper.model.onchain.Address;
import com.easy1staking.jpgstore.sniper.model.onchain.AddressParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

@Slf4j
public class AddressSerdeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testAddressSerialization() {

        var address = new Account().getBaseAddress();

        var internalOnchainAddress = Address.fromAddress(address).get().toPlutusData();

        var cclOnchainAddress = toCclAddress(address);

        Assertions.assertEquals(internalOnchainAddress, cclOnchainAddress.toPlutusData());

    }

    public com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address toCclAddress(com.bloxbean.cardano.client.address.Address address) {
        var pkh = Credential.verificationKey(address.getPaymentCredentialHash().get());
        Optional<ReferencedCredential> skhOpt = address.getDelegationCredentialHash().map(hash -> new ReferencedCredential.Inline(Credential.verificationKey(hash)));
        return new com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address(pkh, skhOpt);
    }

    @Test
    public void testDeserialization() {
        // internal parser
        var addressParser = new AddressParser(OBJECT_MAPPER);

        // ccl parser
        var cclAddressConverter = new AddressConverter();

        var serialisedAddress = "d8799fd8799f581cd1a26a4fc6bcdab501f2cc7b65fa049e6571dc787d2fbc1a79ec3588ffd8799fd8799fd8799f581ca537bd090f63bfdd29aefb6b084ac75f18355224eb11742ac34261f5ffffffff";

        var internalAddressOpt = addressParser.parse(serialisedAddress);

        if (internalAddressOpt.isEmpty()) {
            Assertions.fail();
        }

        var internalAddress = internalAddressOpt.get();

        var cclOnchainAddress = cclAddressConverter.deserialize(serialisedAddress);

        Assertions.assertEquals(CredentialType.Key, internalAddress.paymentKeyHash().getType());
        Assertions.assertNotNull(internalAddress.stakeKeyHash());
        Assertions.assertEquals(CredentialType.Key, internalAddress.stakeKeyHash().getType());

        Assertions.assertInstanceOf(Credential.VerificationKeyCredential.class, cclOnchainAddress.getPaymentCredential());
        Assertions.assertTrue(cclOnchainAddress.getStakeCredential().isPresent());
        var cclAddressStakeCredentialsReference = cclOnchainAddress.getStakeCredential().get();
        Assertions.assertInstanceOf(ReferencedCredential.Inline.class, cclAddressStakeCredentialsReference);
        var cclInlineStakeCredentials = (ReferencedCredential.Inline) cclAddressStakeCredentialsReference;
        var cclAddressStakeCredentials = cclInlineStakeCredentials.getCredential();
        Assertions.assertInstanceOf(Credential.VerificationKeyCredential.class, cclAddressStakeCredentials);

        Assertions.assertArrayEquals(internalAddress.paymentKeyHash().getBytes(), cclOnchainAddress.getPaymentCredential().getHash());
        Assertions.assertArrayEquals(internalAddress.stakeKeyHash().getBytes(), cclAddressStakeCredentials.getHash());

    }


}
