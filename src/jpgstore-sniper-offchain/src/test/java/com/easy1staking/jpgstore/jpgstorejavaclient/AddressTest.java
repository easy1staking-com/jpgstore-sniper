package com.easy1staking.jpgstore.jpgstorejavaclient;


import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.easy1staking.jpgstore.sniper.model.Constants;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class AddressTest {

    @Test
    public void newMnemonic() {
        var account = new Account();
        log.info("mnemonic: {}", account.mnemonic());
    }

    @Test
    public void address() {
        var mnemonic = System.getenv("WALLET_MNEMONIC");
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                var account = Account.createFromMnemonic(Networks.mainnet(), mnemonic, i, j);
                log.info("i: {}, j: {}, address: {}", i, j, account.getBaseAddress().getAddress());
            }
        }
    }

    @Test
    public void jpgContractAddresses() {
        var v1 = new Address(Constants.JPG_CONTRACT_ADDRESS_V1);
        var paymentV1 = AddressProvider.getEntAddress(Credential.fromScript(v1.getPaymentCredentialHash().get()), Networks.mainnet());
        var stakingV1 = AddressProvider.getStakeAddress(v1);

        var v2 = new Address(Constants.JPG_CONTRACT_ADDRESS_V2);
        var paymentV2 = AddressProvider.getEntAddress(Credential.fromScript(v2.getPaymentCredentialHash().get()), Networks.mainnet());
        var stakingV2 = AddressProvider.getStakeAddress(v2);

        log.info("paymentV1: {}, paymentV2: {}", paymentV1.getAddress(), paymentV2.getAddress());
        log.info("stakingV1: {}, stakingV2: {}", stakingV1.getAddress(), stakingV2.getAddress());
    }


}
