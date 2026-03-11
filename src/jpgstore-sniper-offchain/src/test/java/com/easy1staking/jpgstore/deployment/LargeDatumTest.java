package com.easy1staking.jpgstore.deployment;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.jpgstorejavaclient.AbstractTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Tag("deployment")
public class LargeDatumTest extends AbstractTest {

    @Test
    public void mintSnipeTest() throws Exception {

        var account = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC);
        log.info("Account {}", account.baseAddress());

        var hundredNfts = IntStream.range(0, 30).mapToObj(i -> "a706fc87764cde4ac018c38bf61630c1065932db49e6f495be3b29f8436f636f4c6f636f426c756550616c6d53314e4654313737")
                .map(unit -> BytesPlutusData.of(HexUtil.decodeHexString(unit)))
                .toList();

        var nfts = ListPlutusData.of(hundredNfts.toArray(new BytesPlutusData[]{}));

        var tx = new Tx()
                .from(account.baseAddress())
                .payToContract(account.baseAddress(), List.of(Amount.ada(1)), nfts)
                .withChangeAddress(account.baseAddress());

        var transaction = QUICK_TX_BUILDER.compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .mergeOutputs(false)
                .buildAndSign();

        log.info("transaction: {}", transaction.serializeToHex());

        log.info("transaction: {}", OBJECT_MAPPER.writeValueAsString(transaction));

    }

}
