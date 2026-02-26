package com.easy1staking.jpgstore.sniper.config;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.easy1staking.jpgstore.sniper.service.HybridUtxoSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfiguration {

    @Bean
    public UtxoSupplier utxoSupplier(BFBackendService bfBackendService) {
        return new HybridUtxoSupplier(bfBackendService.getUtxoService());
    }

}
