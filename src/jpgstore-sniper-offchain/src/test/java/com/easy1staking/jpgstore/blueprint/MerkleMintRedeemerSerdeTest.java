package com.easy1staking.jpgstore.blueprint;

import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.sniper.blueprint.types.model.MerkleMintRedeemer;
import com.easy1staking.jpgstore.sniper.blueprint.types.model.impl.MerkleMintRedeemerMerkleMintData;
import com.easy1staking.jpgstore.sniper.model.onchain.MerkleMintRedeemerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
public class MerkleMintRedeemerSerdeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testMerkleMintRedeemerSerialization() {
        var nfts = List.of(
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef01",
                "1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        );

        // Internal serialisation
        var internalRedeemer = new com.easy1staking.jpgstore.sniper.model.onchain.MerkleMintRedeemer(nfts);
        var internalPlutusData = internalRedeemer.toPlutusData();

        // CCL blueprint serialisation
        var cclRedeemer = new MerkleMintRedeemerMerkleMintData();
        cclRedeemer.setNfts(nfts.stream()
                .map(HexUtil::decodeHexString)
                .toList());

        var cclPlutusData = cclRedeemer.toPlutusData();

        log.info("Internal: {}", internalPlutusData);
        log.info("CCL:      {}", cclPlutusData);

        Assertions.assertEquals(internalPlutusData, cclPlutusData);
    }

    @Test
    public void testMerkleMintRedeemerDeserialization() {
        var nfts = List.of(
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef01",
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba98"
        );

        // Build from internal model and serialise to CBOR hex
        var internalRedeemer = new com.easy1staking.jpgstore.sniper.model.onchain.MerkleMintRedeemer(nfts);
        var cborHex = internalRedeemer.toPlutusData().serializeToHex();

        // Deserialise via CCL blueprint
        var cclRedeemer = MerkleMintRedeemerMerkleMintData.deserialize(cborHex);

        Assertions.assertNotNull(cclRedeemer.getNfts());
        Assertions.assertEquals(2, cclRedeemer.getNfts().size());
        Assertions.assertArrayEquals(HexUtil.decodeHexString(nfts.get(0)), cclRedeemer.getNfts().get(0));
        Assertions.assertArrayEquals(HexUtil.decodeHexString(nfts.get(1)), cclRedeemer.getNfts().get(1));

        // Also verify internal parser can parse the same CBOR
        var parser = new MerkleMintRedeemerParser(OBJECT_MAPPER);
        var internalParsed = parser.parse(cborHex);
        Assertions.assertTrue(internalParsed.isPresent());
        Assertions.assertEquals(nfts, internalParsed.get().nfts());
    }

    @Test
    public void testMerkleMintRedeemerSingleNft() {
        var nfts = List.of("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

        var internalRedeemer = new com.easy1staking.jpgstore.sniper.model.onchain.MerkleMintRedeemer(nfts);
        var internalPlutusData = internalRedeemer.toPlutusData();

        var cclRedeemer = new MerkleMintRedeemerMerkleMintData();
        cclRedeemer.setNfts(nfts.stream()
                .map(HexUtil::decodeHexString)
                .toList());

        Assertions.assertEquals(internalPlutusData, cclRedeemer.toPlutusData());
    }

    @Test
    public void testMerkleMintRedeemerEmptyList() {
        var nfts = List.<String>of();

        var internalRedeemer = new com.easy1staking.jpgstore.sniper.model.onchain.MerkleMintRedeemer(nfts);
        var internalPlutusData = internalRedeemer.toPlutusData();

        var cclRedeemer = new MerkleMintRedeemerMerkleMintData();
        cclRedeemer.setNfts(List.of());

        Assertions.assertEquals(internalPlutusData, cclRedeemer.toPlutusData());
    }
}
