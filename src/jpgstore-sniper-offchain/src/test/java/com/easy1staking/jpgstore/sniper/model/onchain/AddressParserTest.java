package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class AddressParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DATUM = """
                        {
                          "constructor": 0,
                          "fields": [
                            {
                              "constructor": 0,
                              "fields": [
                                {
                                  "bytes": "274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"
                                }
                              ]
                            },
                            {
                              "constructor": 0,
                              "fields": [
                                {
                                  "constructor": 0,
                                  "fields": [
                                    {
                                      "constructor": 0,
                                      "fields": [
                                        {
                                          "bytes": "a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc"
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        },
            """;

    private final AddressParser addressParser = new AddressParser(MAPPER);

    @Test
    public void testSerialization() throws Exception {
        var expectedAddress = new Address(Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
                Credential.fromKey("a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc"));

        var datumHex = expectedAddress.toPlutusData().serializeToHex();

        var expectedNode = MAPPER.readTree(DATUM);
        var actualNode = MAPPER.readTree(MAPPER.writeValueAsString(PlutusData.deserialize(HexUtil.decodeHexString(datumHex))));

        Assertions.assertEquals(expectedNode, actualNode);

    }

    @Test
    public void testDeserialization() {

        var expectedAddress = new Address(Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
                Credential.fromKey("a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc"));

        var datumHex = expectedAddress.toPlutusData().serializeToHex();

        var accountOpt = addressParser.parse(datumHex);
        if (accountOpt.isEmpty()) {
            Assertions.fail();
        }

        Assertions.assertEquals(expectedAddress, accountOpt.get());
    }

    @Test
    public void testParse() throws Exception {
        var expectedAddress = new Address(Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
                Credential.fromKey("a077ed7c066bcef137094dfa772e5cd7b8853eea4318720573d9bbbc"));

        var jsonNode = MAPPER.readTree(DATUM);

        var parsedOpt = addressParser.parse(jsonNode);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedAddress, parsedOpt.get());
    }

    @Test
    public void testParseNoStake() throws Exception {
        var expectedAddress = new Address(
                Credential.fromKey("274462804bd6f4ae5504de57c90693cb63857083d085d21393403f26"),
                null);

        // serialize and deserialize to get the JSON node
        var jsonNode = MAPPER.readTree(MAPPER.writeValueAsString(expectedAddress.toPlutusData()));

        var parsedOpt = addressParser.parse(jsonNode);

        Assertions.assertTrue(parsedOpt.isPresent(), "Parsing should succeed");
        Assertions.assertEquals(expectedAddress, parsedOpt.get());
    }

}
