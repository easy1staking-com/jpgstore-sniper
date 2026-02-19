package com.easy1staking.jpgstore.sniper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.easy1staking.jpgstore.sniper")
@EntityScan(basePackages = "com.easy1staking.jpgstore.sniper.model.entity")
@EnableJpaRepositories(basePackages = "com.easy1staking.jpgstore.sniper.repository")
public class JpgStoreListenerApp {

    public static void main(String[] args) {
        SpringApplication.run(JpgStoreListenerApp.class, args);
    }

}
