package com.easy1staking.jpgstore.jpgstorejavaclient;


import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.yaci.core.common.Constants;
import com.bloxbean.cardano.yaci.helper.LocalClientProvider;
import com.bloxbean.cardano.yaci.helper.LocalTxMonitorClient;
import com.easy1staking.jpgstore.sniper.service.ListingDatumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.Vector;

import static com.easy1staking.jpgstore.sniper.model.Constants.JPG_CONTRACT_ADDRESS_V1;

public class JpgstoreSniperTest extends SpringBaseTest {

    @Autowired
    private ListingDatumService listingDatumService;

    @Test
    public void listenerApp() {

        LocalClientProvider localClientProvider = new LocalClientProvider("localhost", 3000, Constants.MAINNET_PROTOCOL_MAGIC);
        localClientProvider.start();

        List<String> txAlreadySeen = new Vector<>();

        LocalTxMonitorClient localTxMonitorClient = localClientProvider.getTxMonitorClient();
        localTxMonitorClient
                .streamMempoolTransactions()
                .map(bytes -> {
                    Optional<Transaction> transactionOpt;
                    try {
                        transactionOpt = Optional.of(Transaction.deserialize(bytes));
                    } catch (CborDeserializationException e) {
                        transactionOpt = Optional.empty();
                    }
                    return transactionOpt;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(tx -> {
                    String txHash = TransactionUtil.getTxHash(tx);
                    if (txAlreadySeen.contains(txHash)) {
                        return false;
                    } else {
                        txAlreadySeen.add(txHash);
                        return true;
                    }
                })
                .flatMap(tx -> {
                    Optional<TransactionOutput> first = tx.getBody()
                            .getOutputs()
                            .stream()
                            .filter(txOutput -> txOutput.getAddress().equalsIgnoreCase(JPG_CONTRACT_ADDRESS_V1))
                            .findFirst();
                    return first.map(Flux::just).orElseGet(Flux::empty);
                })
                .subscribe(txOutput -> {
                    Value value = txOutput.getValue();
                    System.out.println("value: " + value);
                    Optional<String> s = listingDatumService.deserializeDatum(txOutput.getInlineDatum());
                    System.out.println(s);
                });

    }

}
