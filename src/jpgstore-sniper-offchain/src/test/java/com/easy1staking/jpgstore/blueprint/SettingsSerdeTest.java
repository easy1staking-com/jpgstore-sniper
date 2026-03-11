package com.easy1staking.jpgstore.blueprint;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Credential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.PaymentCredential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ReferencedCredential;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.sniper.blueprint.types.model.SettingsDatum;
import com.easy1staking.jpgstore.sniper.blueprint.types.model.impl.SettingsDatumData;
import com.easy1staking.jpgstore.sniper.model.onchain.Address;
import com.easy1staking.jpgstore.sniper.model.onchain.Settings;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;

@Slf4j
public class SettingsSerdeTest {

    @Test
    public void testSettingsSerialization() {
        var treasuryAccount = new Account();
        var treasuryAddress = treasuryAccount.getBaseAddress();
        var adminAccount = new Account();
        var adminPkh = HexUtil.encodeHexString(adminAccount.getBaseAddress().getPaymentCredentialHash().get());

        var stakeCredential = treasuryAccount.getBaseAddress().getDelegationCredential().get();

        var protocolTreasury = Address.fromAddress(treasuryAddress).get();

        // Internal serialisation
        var internalSettings = Settings.builder()
                .operatorFeePct(1)
                .protocolFeePct(1)
                .minOperatorFee(1_000_000L)
                .minProtocolFee(1_000_000L)
                .protocolTreasury(protocolTreasury)
                .stakeCredential(stakeCredential)
                .txFeeBudget(1_000_000L)
                .adminPkh(adminPkh)
                .build();

        var internalPlutusData = internalSettings.toPlutusData();

        // CCL blueprint serialisation
        var cclSettings = new SettingsDatumData();
        cclSettings.setOperatorFeePct(BigInteger.valueOf(1));
        cclSettings.setProtocolFeePct(BigInteger.valueOf(1));
        cclSettings.setMinOperatorFee(BigInteger.valueOf(1_000_000L));
        cclSettings.setMinProtocolFee(BigInteger.valueOf(1_000_000L));
        cclSettings.setProtocolTreasury(toCclAddress(treasuryAddress));
        cclSettings.setStakeCredential(toCclPaymentCredential(stakeCredential));
        cclSettings.setTxFeeBudget(BigInteger.valueOf(1_000_000L));
        cclSettings.setAdminPkh(VerificationKeyHash.of(HexUtil.decodeHexString(adminPkh)));

        var cclPlutusData = cclSettings.toPlutusData();

        log.info("Internal: {}", internalPlutusData);
        log.info("CCL:      {}", cclPlutusData);

        Assertions.assertEquals(internalPlutusData, cclPlutusData);
    }

    @Test
    public void testSettingsDeserialization() {
        var treasuryAccount = new Account();
        var treasuryAddress = treasuryAccount.getBaseAddress();
        var adminAccount = new Account();
        var adminPkh = HexUtil.encodeHexString(adminAccount.getBaseAddress().getPaymentCredentialHash().get());

        var stakeCredential = treasuryAccount.getBaseAddress().getDelegationCredential().get();
        var protocolTreasury = Address.fromAddress(treasuryAddress).get();

        var internalSettings = Settings.builder()
                .operatorFeePct(2)
                .protocolFeePct(3)
                .minOperatorFee(500_000L)
                .minProtocolFee(750_000L)
                .protocolTreasury(protocolTreasury)
                .stakeCredential(stakeCredential)
                .txFeeBudget(2_000_000L)
                .adminPkh(adminPkh)
                .build();

        var cborHex = internalSettings.toPlutusData().serializeToHex();

        // Deserialise via CCL blueprint
        var cclSettings = SettingsDatumData.deserialize(cborHex);

        Assertions.assertEquals(BigInteger.valueOf(2), cclSettings.getOperatorFeePct());
        Assertions.assertEquals(BigInteger.valueOf(3), cclSettings.getProtocolFeePct());
        Assertions.assertEquals(BigInteger.valueOf(500_000L), cclSettings.getMinOperatorFee());
        Assertions.assertEquals(BigInteger.valueOf(750_000L), cclSettings.getMinProtocolFee());
        Assertions.assertEquals(BigInteger.valueOf(2_000_000L), cclSettings.getTxFeeBudget());
        Assertions.assertArrayEquals(HexUtil.decodeHexString(adminPkh), cclSettings.getAdminPkh().bytes());

        // Verify treasury address
        var cclTreasury = cclSettings.getProtocolTreasury();
        Assertions.assertArrayEquals(
                treasuryAddress.getPaymentCredentialHash().get(),
                cclTreasury.getPaymentCredential().getHash());
        Assertions.assertTrue(cclTreasury.getStakeCredential().isPresent());

        // Verify stake credential
        var cclStake = cclSettings.getStakeCredential();
        Assertions.assertArrayEquals(stakeCredential.getBytes(), cclStake.getHash());
    }

    @Test
    public void testSettingsRoundTrip() {
        var treasuryAccount = new Account();
        var treasuryAddress = treasuryAccount.getBaseAddress();
        var adminAccount = new Account();
        var adminPkh = HexUtil.encodeHexString(adminAccount.getBaseAddress().getPaymentCredentialHash().get());

        var stakeCredential = treasuryAccount.getBaseAddress().getDelegationCredential().get();

        // Build via CCL, serialize, deserialize via CCL, then compare PlutusData
        var cclSettings = new SettingsDatumData();
        cclSettings.setOperatorFeePct(BigInteger.valueOf(5));
        cclSettings.setProtocolFeePct(BigInteger.valueOf(2));
        cclSettings.setMinOperatorFee(BigInteger.valueOf(1_000_000L));
        cclSettings.setMinProtocolFee(BigInteger.valueOf(500_000L));
        cclSettings.setProtocolTreasury(toCclAddress(treasuryAddress));
        cclSettings.setStakeCredential(toCclPaymentCredential(stakeCredential));
        cclSettings.setTxFeeBudget(BigInteger.valueOf(1_500_000L));
        cclSettings.setAdminPkh(VerificationKeyHash.of(HexUtil.decodeHexString(adminPkh)));

        var cborHex = cclSettings.toPlutusData().serializeToHex();

        var deserialized = SettingsDatumData.deserialize(cborHex);

        Assertions.assertEquals(cclSettings.toPlutusData(), deserialized.toPlutusData());
    }

    private com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address toCclAddress(
            com.bloxbean.cardano.client.address.Address address) {
        var pkh = Credential.verificationKey(address.getPaymentCredentialHash().get());
        Optional<ReferencedCredential> skhOpt = address.getDelegationCredentialHash()
                .map(hash -> new ReferencedCredential.Inline(Credential.verificationKey(hash)));
        return new com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address(pkh, skhOpt);
    }

    private PaymentCredential toCclPaymentCredential(
            com.bloxbean.cardano.client.address.Credential credential) {
        return switch (credential.getType()) {
            case Key -> PaymentCredential.verificationKey(credential.getBytes());
            case Script -> PaymentCredential.script(credential.getBytes());
        };
    }
}
