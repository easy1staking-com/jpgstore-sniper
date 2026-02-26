package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class MerkleMintRedeemerParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MerkleMintRedeemerParser parser = new MerkleMintRedeemerParser(MAPPER);

    private static final String NFT1 = "a706fc87764cde4ac018c38bf61630c1065932db49e6f495be3b29f8";
    private static final String NFT2 = "274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26";
    private static final String NFT3 = "a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc";

    @Test
    void testSerialization() throws Exception {
        var redeemer = new MerkleMintRedeemer(List.of(NFT1, NFT2, NFT3));

        var hex = redeemer.toPlutusData().serializeToHex();

        var expectedNode = MAPPER.readTree(MAPPER.writeValueAsString(redeemer.toPlutusData()));
        var actualNode = MAPPER.readTree(MAPPER.writeValueAsString(PlutusData.deserialize(HexUtil.decodeHexString(hex))));

        Assertions.assertEquals(expectedNode, actualNode);
    }

    @Test
    void testDeserialization() {
        var expected = new MerkleMintRedeemer(List.of(NFT1, NFT2, NFT3));

        var hex = expected.toPlutusData().serializeToHex();

        var parsedOpt = parser.parse(hex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expected, parsedOpt.get());
    }

    @Test
    void testDeserializationSingleNft() {
        var expected = new MerkleMintRedeemer(List.of(NFT1));

        var hex = expected.toPlutusData().serializeToHex();

        var parsedOpt = parser.parse(hex);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expected, parsedOpt.get());
    }

    @Test
    void testMerkleBurnReturnsEmpty() throws Exception {
        // MerkleBurn is constructor 1, no fields
        var burnPlutusData = ConstrPlutusData.of(1);
        var hex = burnPlutusData.serializeToHex();

        var parsedOpt = parser.parse(hex);

        Assertions.assertTrue(parsedOpt.isEmpty(), "MerkleBurn should return empty");
    }

    @Test
    void testParseNullReturnsEmpty() {
        var parsedOpt = parser.parse((String) null);
        Assertions.assertTrue(parsedOpt.isEmpty());
    }

    @Test
    void testParseInvalidHexReturnsEmpty() {
        var parsedOpt = parser.parse("not_valid_hex");
        Assertions.assertTrue(parsedOpt.isEmpty());
    }

}
