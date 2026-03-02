package com.easy1staking.jpgstore.sniper.config;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultScriptSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
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

    @Bean
    public AddressService blockfrostAddressService() {
        return new AddressServiceImpl(blockfrostUrl, blockfrostKey);
    }

    @Bean
    public TransactionService transactionService() {
        return new TransactionServiceImpl(blockfrostUrl, blockfrostKey);
    }

    @Bean
    public MetadataService metadataService() {
        return new MetadataServiceImpl(blockfrostUrl, blockfrostKey);
    }

    @Bean
    public BFBackendService bfBackendService() {
        log.info("blockfrostUrl: {}, blockfrostKey: {}", blockfrostUrl, blockfrostKey);
        var blockfrost = new BFBackendService(blockfrostUrl, blockfrostKey);
        Block lastBlock = null;
        try {
            lastBlock = blockfrost.getBlockService().getLatestBlock().getValue();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        log.info("height: {}", lastBlock.getHeight());
        return blockfrost;
    }


//    @Bean
//    public AikenTransactionEvaluator aikenTransactionEvaluator(HybridUtxoSupplier hybridUtxoSupplier,
//                                                               BFBackendService bfBackendService,
//                                                               JpgstoreScriptProvider jpgstoreScriptProvider) {
//        return new AikenTransactionEvaluator(hybridUtxoSupplier,
//                new DefaultProtocolParamsSupplier(bfBackendService.getEpochService()),
//                jpgstoreScriptProvider);
//    }

//    @Bean
//    public QuickTxBuilder mempoolQuickTxBuilder(HybridUtxoSupplier hybridUtxoSupplier,
//                                                TransactionProcessor transactionProcessor,
//                                                ProtocolParamsSupplier protocolParamsSupplier,
//                                                BFBackendService bfBackendService) {
//
//        log.info("transactionProcessor: {}, class: {}", transactionProcessor, transactionProcessor.getClass());
//
//        var scriptSupplier = new DefaultScriptSupplier(bfBackendService.getScriptService());
//
//        return new QuickTxBuilder(hybridUtxoSupplier,
//                protocolParamsSupplier,
//                scriptSupplier,
//                transactionProcessor);
//
//    }

    @Bean
    public QuickTxBuilder quickTxBuilder(BFBackendService bfBackendService) {
        return new QuickTxBuilder(bfBackendService);
    }

}
