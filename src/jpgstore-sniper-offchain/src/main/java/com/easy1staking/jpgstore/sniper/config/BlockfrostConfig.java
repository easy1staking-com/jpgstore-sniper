package com.easy1staking.jpgstore.sniper.config;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.easy1staking.jpgstore.sniper.service.HybridUtxoSupplier;
import com.easy1staking.jpgstore.sniper.service.JpgstoreScriptProvider;
import io.blockfrost.sdk.api.AddressService;
import io.blockfrost.sdk.api.MetadataService;
import io.blockfrost.sdk.api.TransactionService;
import io.blockfrost.sdk.impl.AddressServiceImpl;
import io.blockfrost.sdk.impl.MetadataServiceImpl;
import io.blockfrost.sdk.impl.TransactionServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class BlockfrostConfig {

    @Value("${blockfrost.url}")
    private String blockfrostUrl;

    @Value("${blockfrost.key}")
    private String blockfrostKey;

    @Bean("BlockfrostAddressService")
    public AddressService blockfrostAddressService() {
        return new AddressServiceImpl(blockfrostUrl, "");
    }

    @Bean
    public TransactionService transactionService() {
        return new TransactionServiceImpl(blockfrostUrl, "");
    }

    @Bean
    public MetadataService metadataService() {
        return new MetadataServiceImpl(blockfrostUrl, "");
    }

    @Bean
    public BFBackendService bfBackendService() {
        log.info("blockfrostUrl: {}, blockfrostKey: {}", blockfrostUrl, blockfrostKey);
        return new BFBackendService( blockfrostUrl, blockfrostKey);
    }

    @Bean
    public AikenTransactionEvaluator aikenTransactionEvaluator(HybridUtxoSupplier hybridUtxoSupplier,
                                                               BFBackendService bfBackendService,
                                                               JpgstoreScriptProvider jpgstoreScriptProvider) {
        return new AikenTransactionEvaluator(hybridUtxoSupplier,
                new DefaultProtocolParamsSupplier(bfBackendService.getEpochService()),
                jpgstoreScriptProvider);
    }
}
