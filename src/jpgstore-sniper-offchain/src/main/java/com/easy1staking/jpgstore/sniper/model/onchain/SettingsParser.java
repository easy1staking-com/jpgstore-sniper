package com.easy1staking.jpgstore.sniper.model.onchain;

import com.bloxbean.cardano.client.address.Credential;
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
public class SettingsParser {

    private final ObjectMapper objectMapper;
    private final AddressParser addressParser;

    public Optional<Settings> parse(String inlineDatum) {
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

    public Optional<Settings> parse(PlutusData plutusData) {
        try {
            var jsonData = objectMapper.readTree(objectMapper.writeValueAsString(plutusData));
            var fields = jsonData.path("fields");

            var operatorFeePct = fields.get(0).path("int").asInt();
            var protocolFeePct = fields.get(1).path("int").asInt();
            var minOperatorFee = fields.get(2).path("int").asLong();
            var minProtocolFee = fields.get(3).path("int").asLong();

            var protocolTreasuryNode = fields.get(4);
            var protocolTreasury = addressParser.parse(protocolTreasuryNode)
                    .orElseThrow(() -> new IllegalArgumentException("Failed to parse protocolTreasury"));

            var stakeCredentialNode = fields.get(5);
            var stakeKeyType = stakeCredentialNode.path("constructor").asInt();
            var stakeKeyHash = stakeCredentialNode.path("fields").get(0).path("bytes").asText();
            var stakeCredential = stakeKeyType == 0 ? Credential.fromKey(stakeKeyHash) : Credential.fromScript(stakeKeyHash);

            var txFeeBudget = fields.get(6).path("int").asLong();
            var adminPkh = fields.get(7).path("bytes").asText();

            return Optional.of(Settings.builder()
                    .operatorFeePct(operatorFeePct)
                    .protocolFeePct(protocolFeePct)
                    .minOperatorFee(minOperatorFee)
                    .minProtocolFee(minProtocolFee)
                    .protocolTreasury(protocolTreasury)
                    .stakeCredential(stakeCredential)
                    .txFeeBudget(txFeeBudget)
                    .adminPkh(adminPkh)
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("error", e);
            return Optional.empty();
        }
    }

}
