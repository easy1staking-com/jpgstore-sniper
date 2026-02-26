package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class SnipeDatumParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AddressParser addressParser = new AddressParser(MAPPER);
    private final SnipeDatumParser snipeDatumParser = new SnipeDatumParser(MAPPER, addressParser);

    private static final String OWNER_PKH = "274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26";
    private static final String TARGET_HASH = "a706fc87764cde4ac018c38bf61630c1065932db49e6f495be3b29f8";

    private static final Address NFT_DESTINATION = new Address(
            Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
            Credential.fromKey("a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc"));

    @Test
    void testSerialization() throws Exception {
        var snipeDatum = SnipeDatum.builder()
                .ownerPkh(OWNER_PKH)
                .nftDestination(NFT_DESTINATION)
                .targetHash(TARGET_HASH)
                .maxPrice(45_000_000L)
                .protocolFee(500_000L)
                .build();

        var datumHex = snipeDatum.toPlutusData().serializeToHex();

        // round-trip: serialize to hex, deserialize, serialize to JSON, compare with direct JSON
        var expectedNode = MAPPER.readTree(MAPPER.writeValueAsString(snipeDatum.toPlutusData()));
        var actualNode = MAPPER.readTree(MAPPER.writeValueAsString(PlutusData.deserialize(HexUtil.decodeHexString(datumHex))));

        Assertions.assertEquals(expectedNode, actualNode);
    }

    @Test
    void testDeserialization() {
        var expectedDatum = SnipeDatum.builder()
                .ownerPkh(OWNER_PKH)
                .nftDestination(NFT_DESTINATION)
                .targetHash(TARGET_HASH)
                .maxPrice(45_000_000L)
                .protocolFee(500_000L)
                .build();

        var datumHex = expectedDatum.toPlutusData().serializeToHex();

        var parsedOpt = snipeDatumParser.parse(datumHex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedDatum, parsedOpt.get());
    }

    @Test
    void testDeserializationWithScriptCredentials() {
        var scriptDestination = new Address(
                Credential.fromScript("abcdef0123456789abcdef0123456789abcdef0123456789abcdef01"),
                Credential.fromScript("1234567890abcdef1234567890abcdef1234567890abcdef12345678"));

        var expectedDatum = SnipeDatum.builder()
                .ownerPkh(OWNER_PKH)
                .nftDestination(scriptDestination)
                .targetHash(TARGET_HASH)
                .maxPrice(120_000_000L)
                .protocolFee(1_200_000L)
                .build();

        var datumHex = expectedDatum.toPlutusData().serializeToHex();

        var parsedOpt = snipeDatumParser.parse(datumHex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedDatum, parsedOpt.get());
    }

    @Test
    void testDeserializationWithNoStakeCredential() {
        var noStakeDestination = new Address(
                Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
                null);

        var expectedDatum = SnipeDatum.builder()
                .ownerPkh(OWNER_PKH)
                .nftDestination(noStakeDestination)
                .targetHash(TARGET_HASH)
                .maxPrice(10_000_000L)
                .protocolFee(500_000L)
                .build();

        var datumHex = expectedDatum.toPlutusData().serializeToHex();

        var parsedOpt = snipeDatumParser.parse(datumHex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedDatum, parsedOpt.get());
    }

    @Test
    void testParseNullReturnsEmpty() {
        var parsedOpt = snipeDatumParser.parse((String) null);
        Assertions.assertTrue(parsedOpt.isEmpty());
    }

    @Test
    void testParseInvalidHexReturnsEmpty() {
        var parsedOpt = snipeDatumParser.parse("not_valid_hex");
        Assertions.assertTrue(parsedOpt.isEmpty());
    }

}
