package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SnipeDatumParser {

    private final ObjectMapper objectMapper;
    private final AddressParser addressParser;

    public Optional<SnipeDatum> parse(String inlineDatum) {
        return Optional.ofNullable(inlineDatum)
                .flatMap(datum -> {
                    try {
                        return Optional.of(PlutusData.deserialize(HexUtil.decodeHexString(datum)));
                    } catch (Exception e) {
                        log.warn("error", e);
                        return Optional.empty();
                    }
                })
                .flatMap(this::parse);
    }

    public Optional<SnipeDatum> parse(PlutusData plutusData) {
        try {
            var jsonData = objectMapper.readTree(objectMapper.writeValueAsString(plutusData));
            var fields = jsonData.path("fields");

            var ownerPkh = fields.get(0).path("bytes").asText();

            var nftDestinationNode = fields.get(1);
            return addressParser.parse(nftDestinationNode)
                    .map(address -> {
                        var targetHash = fields.get(2).path("bytes").asText();
                        var maxPrice = fields.get(3).path("int").asLong();
                        var protocolFee = fields.get(4).path("int").asLong();
                        return SnipeDatum.builder()
                                .ownerPkh(ownerPkh)
                                .nftDestination(address)
                                .targetHash(targetHash)
                                .maxPrice(maxPrice)
                                .protocolFee(protocolFee)
                                .build();
                    });

        } catch (JsonProcessingException e) {
            log.warn("error", e);
            return Optional.empty();
        }
    }

}
