package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class SettingsContractParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AddressParser addressParser = new AddressParser(MAPPER);
    private final SettingsParser settingsParser = new SettingsParser(MAPPER, addressParser);

    private static final String ADMIN_PKH = "274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26";

    private static final Address PROTOCOL_TREASURY = new Address(
            Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
            Credential.fromKey("a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc"));

    private static final Credential STAKE_CREDENTIAL =
            Credential.fromScript("abcdef0123456789abcdef0123456789abcdef0123456789abcdef01");

    @Test
    void testSerialization() throws Exception {
        var settings = Settings.builder()
                .operatorFeePct(2)
                .protocolFeePct(1)
                .minOperatorFee(1_000_000L)
                .minProtocolFee(500_000L)
                .protocolTreasury(PROTOCOL_TREASURY)
                .stakeCredential(STAKE_CREDENTIAL)
                .txFeeBudget(500_000L)
                .adminPkh(ADMIN_PKH)
                .build();

        var datumHex = settings.toPlutusData().serializeToHex();

        var expectedNode = MAPPER.readTree(MAPPER.writeValueAsString(settings.toPlutusData()));
        var actualNode = MAPPER.readTree(MAPPER.writeValueAsString(PlutusData.deserialize(HexUtil.decodeHexString(datumHex))));

        Assertions.assertEquals(expectedNode, actualNode);
    }

    @Test
    void testDeserialization() {
        var expectedSettings = Settings.builder()
                .operatorFeePct(2)
                .protocolFeePct(1)
                .minOperatorFee(1_000_000L)
                .minProtocolFee(500_000L)
                .protocolTreasury(PROTOCOL_TREASURY)
                .stakeCredential(STAKE_CREDENTIAL)
                .txFeeBudget(500_000L)
                .adminPkh(ADMIN_PKH)
                .build();

        var datumHex = expectedSettings.toPlutusData().serializeToHex();

        var parsedOpt = settingsParser.parse(datumHex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedSettings, parsedOpt.get());
    }

    @Test
    void testDeserializationWithKeyStakeCredential() {
        var keyStakeCredential = Credential.fromKey("a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc");

        var expectedSettings = Settings.builder()
                .operatorFeePct(5)
                .protocolFeePct(2)
                .minOperatorFee(2_000_000L)
                .minProtocolFee(1_000_000L)
                .protocolTreasury(PROTOCOL_TREASURY)
                .stakeCredential(keyStakeCredential)
                .txFeeBudget(750_000L)
                .adminPkh(ADMIN_PKH)
                .build();

        var datumHex = expectedSettings.toPlutusData().serializeToHex();

        var parsedOpt = settingsParser.parse(datumHex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedSettings, parsedOpt.get());
    }

    @Test
    void testDeserializationWithNoStakeOnTreasury() {
        var treasuryNoStake = new Address(
                Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
                null);

        var expectedSettings = Settings.builder()
                .operatorFeePct(2)
                .protocolFeePct(1)
                .minOperatorFee(1_000_000L)
                .minProtocolFee(500_000L)
                .protocolTreasury(treasuryNoStake)
                .stakeCredential(STAKE_CREDENTIAL)
                .txFeeBudget(500_000L)
                .adminPkh(ADMIN_PKH)
                .build();

        var datumHex = expectedSettings.toPlutusData().serializeToHex();

        var parsedOpt = settingsParser.parse(datumHex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedSettings, parsedOpt.get());
    }

    @Test
    void testParseNullReturnsEmpty() {
        var parsedOpt = settingsParser.parse((String) null);
        Assertions.assertTrue(parsedOpt.isEmpty());
    }

    @Test
    void testParseInvalidHexReturnsEmpty() {
        var parsedOpt = settingsParser.parse("not_valid_hex");
        Assertions.assertTrue(parsedOpt.isEmpty());
    }

}
