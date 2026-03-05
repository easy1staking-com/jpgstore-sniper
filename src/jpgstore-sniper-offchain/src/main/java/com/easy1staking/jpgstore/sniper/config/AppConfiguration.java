package com.easy1staking.jpgstore.sniper.config;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.sniper.aiken.model.AbstractContract;
import com.easy1staking.jpgstore.sniper.contract.MerkleTreeSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.PolicyIdSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import com.easy1staking.jpgstore.sniper.model.Constants;
import com.easy1staking.jpgstore.sniper.mempool.service.HybridUtxoSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Stream;

@Configuration
@Slf4j
public class AppConfiguration {

    @Bean
    public Network network(@Value("${network}") String network) {
        var actualNetwork = switch (network) {
            case "preprod" -> Networks.preprod();
            case "preview" -> Networks.preview();
            default -> Networks.mainnet();
        };
        log.info("INIT network: {}, network type: {}", network, actualNetwork);
        return actualNetwork;
    }



    @Bean
    @Qualifier("pubKeyHashes")
    public List<String> relevantPubKeyHashes(Account account,
                                             SettingsContract settingsContract,
                                             PolicyIdSnipeContract policyIdSnipeContract,
                                             MerkleTreeSnipeContract merkleTreeSnipeContract) {

        var v1Address = new Address(Constants.JPG_CONTRACT_ADDRESS_V1);
        var v2Address = new Address(Constants.JPG_CONTRACT_ADDRESS_V2);

        var contractHashes = Stream.of(settingsContract, policyIdSnipeContract, merkleTreeSnipeContract)
                .map(AbstractContract::getScriptHash);

        var addressHashes = Stream.of(v1Address, v2Address, account.getBaseAddress())
                .flatMap(address -> address.getPaymentCredentialHash().map(HexUtil::encodeHexString).stream());

        return Stream.concat(contractHashes, addressHashes)
                .toList();

    }


}
