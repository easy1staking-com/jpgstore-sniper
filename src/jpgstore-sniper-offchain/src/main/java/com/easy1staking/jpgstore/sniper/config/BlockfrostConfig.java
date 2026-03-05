package com.easy1staking.jpgstore.sniper.config;

import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
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
        return new BFBackendService(blockfrostUrl, blockfrostKey);
    }

    @Bean
    public QuickTxBuilder defaultQuickTxBuilder(BFBackendService bfBackendService) {
        return new QuickTxBuilder(bfBackendService);
    }

}
