package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AddressParser {

    private final ObjectMapper objectMapper;

    public Optional<Address> parse(String inlineDatum) {

        return Optional.ofNullable(inlineDatum)
                .flatMap(datum -> {
                    try {
                        return Optional.of(PlutusData.deserialize(HexUtil.decodeHexString(datum)));
                    } catch (CborDeserializationException e) {
                        log.warn("error", e);
                        return Optional.empty();
                    }
                })
                .flatMap(this::parse);

    }

    public Optional<Address> parse(PlutusData addressPlutusData) {
        try {
            var jsonData = objectMapper.readTree(objectMapper.writeValueAsString(addressPlutusData));
            return parse(jsonData);
        } catch (JsonProcessingException e) {
            log.warn("error", e);
            return Optional.empty();
        }

    }

    public Optional<Address> parse(JsonNode jsonNode) {
        try {
            var root = jsonNode.path("fields");
            var paymentCredentials = root.get(0);

            var paymentKeyType = paymentCredentials.path("constructor").asInt();
            var paymentKeyHash = paymentCredentials.path("fields").get(0).path("bytes").asText();

            var paymentCredential = paymentKeyType == 0 ? Credential.fromKey(paymentKeyHash) : Credential.fromScript(paymentKeyHash);

            var stakingCredentials = root.get(1);
            Credential stakingCredential = null;
            if (stakingCredentials.path("constructor").asInt() == 0) {
                var actualStakeCredentials = stakingCredentials.path("fields")
                        .get(0)
                        .path("fields")
                        .get(0);
                var stakeKeyType = actualStakeCredentials.path("constructor").asInt();
                var stakeKeyHash = actualStakeCredentials.path("fields").get(0).path("bytes").asText();
                stakingCredential = stakeKeyType == 0 ? Credential.fromKey(stakeKeyHash) : Credential.fromScript(stakeKeyHash);
            }

            return Optional.of(new Address(paymentCredential, stakingCredential));
        } catch (Exception e) {
            log.warn("error parsing address from json node", e);
            return Optional.empty();
        }
    }

}
