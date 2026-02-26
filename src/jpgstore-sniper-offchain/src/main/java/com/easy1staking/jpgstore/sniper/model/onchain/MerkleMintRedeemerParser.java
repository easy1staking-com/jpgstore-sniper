package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MerkleMintRedeemerParser {

    private final ObjectMapper objectMapper;

    public Optional<MerkleMintRedeemer> parse(String redeemerHex) {
        return Optional.ofNullable(redeemerHex)
                .flatMap(hex -> {
                    try {
                        return Optional.of(PlutusData.deserialize(HexUtil.decodeHexString(hex)));
                    } catch (Exception e) {
                        log.warn("error", e);
                        return Optional.empty();
                    }
                })
                .flatMap(this::parse);
    }

    public Optional<MerkleMintRedeemer> parse(PlutusData plutusData) {
        try {
            var jsonData = objectMapper.readTree(objectMapper.writeValueAsString(plutusData));

            var constructor = jsonData.path("constructor").asInt(-1);
            if (constructor != 0) {
                log.debug("Not a MerkleMint redeemer, constructor: {}", constructor);
                return Optional.empty();
            }

            var listNode = jsonData.path("fields").get(0).path("list");
            var nfts = new ArrayList<String>();
            for (var element : listNode) {
                nfts.add(element.path("bytes").asText());
            }

            return Optional.of(new MerkleMintRedeemer(List.copyOf(nfts)));

        } catch (JsonProcessingException e) {
            log.warn("error", e);
            return Optional.empty();
        }
    }

}
