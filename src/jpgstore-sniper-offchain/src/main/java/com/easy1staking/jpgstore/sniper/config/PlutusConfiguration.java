package com.easy1staking.jpgstore.sniper.config;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultScriptSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.easy1staking.jpgstore.sniper.mempool.service.HybridUtxoSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import scalus.bloxbean.ScalusTransactionEvaluator;

//@Configuration
@Slf4j
public class PlutusConfiguration {

    @Bean
    public HybridUtxoSupplier hybridUtxoSupplier(BFBackendService bfBackendService) {
        return new HybridUtxoSupplier(bfBackendService.getUtxoService());
    }

    @Bean
    public ProtocolParamsSupplier protocolParamsSupplier(BFBackendService bfBackendService) {
        return new DefaultProtocolParamsSupplier(bfBackendService.getEpochService());
    }

    @Bean
    public ScriptSupplier scriptSupplier(BFBackendService bfBackendService) {
        return new DefaultScriptSupplier(bfBackendService.getScriptService());
    }

    @Bean
    public TransactionEvaluator transactionEvaluator(HybridUtxoSupplier hybridUtxoSupplier,
                                                     ProtocolParamsSupplier protocolParamsSupplier,
                                                     ScriptSupplier scriptSupplier) {

        var scalusScriptSupplier = new scalus.bloxbean.ScriptSupplier() {
            @Override
            public PlutusScript getScript(String scriptHash) {
                var scriptOpt = scriptSupplier.getScript(scriptHash);
                if (scriptOpt.isEmpty()) {
                    log.warn("could not find script for {}", scriptHash);
                }
                return scriptOpt.orElse(null);
            }
        };

        return new ScalusTransactionEvaluator(protocolParamsSupplier.getProtocolParams(),
                hybridUtxoSupplier,
                scalusScriptSupplier);

    }



}
