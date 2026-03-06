package com.easy1staking.jpgstore.sniper.controller;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import com.easy1staking.jpgstore.sniper.contract.PolicyIdSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("${apiPrefix}/contracts")
@Slf4j
@RequiredArgsConstructor
public class ContractsController {

    private final Network network;
    private final SettingsContract settingsContract;
    private final PolicyIdSnipeContract policyIdSnipeContract;
    private final UtxoRepository utxoRepository;

    @GetMapping
    public ResponseEntity<ContractsDto> getContracts() {
        var settingsScript = settingsContract.getPlutusScript();
        var listingScript = policyIdSnipeContract.getPlutusScript();
        var networkId = network.getNetworkId();

        // Look up the current settings UTxO on-chain
        var settingsUtxo = utxoRepository.findUnspentByOwnerPaymentCredential(
                        settingsContract.getScriptHash(), Pageable.unpaged())
                .stream()
                .flatMap(Collection::stream)
                .findFirst()
                .orElse(null);

        if (settingsUtxo == null) {
            log.error("Settings UTxO not found at script hash: {}", settingsContract.getScriptHash());
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(new ContractsDto(
                settingsScript.getCborHex(),
                settingsContract.getScriptHash(),
                listingScript.getCborHex(),
                policyIdSnipeContract.getScriptHash(),
                policyIdSnipeContract.getAddress(network),
                settingsUtxo.getTxHash(),
                settingsUtxo.getOutputIndex(),
                networkId
        ));
    }

    public record ContractsDto(
            String settingsNftPolicyCbor,
            String settingsNftPolicyId,
            String listingNftPolicyCbor,
            String listingNftPolicyId,
            String escrowScriptAddress,
            String settingsUtxoTxHash,
            int settingsUtxoOutputIndex,
            int networkId
    ) {}
}
