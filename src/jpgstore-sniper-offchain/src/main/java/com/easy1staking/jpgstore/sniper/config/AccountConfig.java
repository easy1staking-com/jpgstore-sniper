package com.easy1staking.jpgstore.sniper.config;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AccountConfig {

    @Bean
    public Account account(@Value("${wallet.mnemonic}") String mnemonic) {
        var account = Account.createFromMnemonic(Networks.mainnet(), mnemonic, 2, 0);
        log.info("INIT - address: {}", account.baseAddress());
        return account;
    }

}
