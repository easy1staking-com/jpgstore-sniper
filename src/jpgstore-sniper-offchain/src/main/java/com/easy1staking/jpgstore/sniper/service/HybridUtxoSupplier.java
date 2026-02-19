package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.model.Block;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class HybridUtxoSupplier implements UtxoSupplier {

    private final BFBackendService backendService;

    private final Account account;
    private Map<String, List<Utxo>> mempoolTransactions = new ConcurrentHashMap<>();

    private List<Utxo> walletUtxo = new Vector<>();

    private Utxo jgpstoreContractUtxo;

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        return walletUtxo;
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {

        if (jgpstoreContractUtxo.getTxHash().equals(txHash) && jgpstoreContractUtxo.getOutputIndex() == outputIndex) {
            log.info("Requested jpg contract utxo");
            return Optional.of(jgpstoreContractUtxo);
        }

        Optional<Utxo> mempoolUtxo = mempoolTransactions
                .values()
                .stream().flatMap(Collection::stream)
                .filter(utxo -> utxo.getTxHash().equals(txHash) && utxo.getOutputIndex() == outputIndex).findFirst();

        mempoolUtxo.ifPresent(utxo -> log.info("found utxo in mempool: {}", utxo));


        return mempoolUtxo
                .or(() -> {
                    Optional<Utxo> walletUtxo = this.walletUtxo
                            .stream()
                            .filter(utxo -> utxo.getTxHash().equals(txHash) && utxo.getOutputIndex() == outputIndex)
                            .findFirst();

                    if (walletUtxo.isPresent()) {
                        log.info("found wallet utxo: {}", walletUtxo.get());
                    } else {
                        log.info("{}:{} not found in wallet", txHash, outputIndex);
                    }

                    return walletUtxo;
                })
                .or(() -> {
                    log.info("{}:{} not found, looking up on blockfrost", txHash, outputIndex);
                    try {
                        Optional<Utxo> onchainUtxo = Optional.of(backendService.getUtxoService().getTxOutput(txHash, outputIndex).getValue());
                        if (onchainUtxo.isPresent()) {
                            log.info("found utxo onchain: {}", onchainUtxo.get());
                        } else {
                            log.info("{}:{} not found on blockfrost either", txHash, outputIndex);
                        }
                        return onchainUtxo;
                    } catch (ApiException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public void extractMempoolUtxo(Transaction tx) {
        String txHash = TransactionUtil.getTxHash(tx);
        List<TransactionOutput> outputs = tx.getBody().getOutputs();
        List<Utxo> utxos = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput transactionOutput = outputs.get(0);
            Utxo utxo = Utxo.builder()
                    .txHash(txHash)
                    .outputIndex(i)
                    .address(transactionOutput.getAddress())
                    .amount(ValueUtil.toAmountList(transactionOutput.getValue()))
                    .dataHash(HexUtil.encodeHexString(transactionOutput.getDatumHash()))
                    .inlineDatum(Optional.ofNullable(transactionOutput.getInlineDatum()).map(PlutusData::serializeToHex).orElse(null))
                    .referenceScriptHash(HexUtil.encodeHexString(transactionOutput.getScriptRef()))
                    .build();
            utxos.add(utxo);
        }
        mempoolTransactions.put(txHash, utxos);
    }

    public void processBlock(Block block) {
//        log.info("Processed block {}", block.getHeader().getHeaderBody().getSlot());
        block.getTransactionBodies().forEach(tx -> mempoolTransactions.remove(tx.getTxHash()));
        try {
            Result<List<Utxo>> utxos = backendService.getUtxoService().getUtxos(account.baseAddress(), 100, 1);
            walletUtxo.clear();
            walletUtxo.addAll(utxos.getValue());
        } catch (ApiException e) {
            log.error("Could not fetch utxos...");
        }
    }

    @PostConstruct
    public void getJpgstoreUtxoContract() {
        try {
            jgpstoreContractUtxo = backendService.getUtxoService().getTxOutput("9a32459bd4ef6bbafdeb8cf3b909d0e3e2ec806e4cc6268529280b0fc1d06f5b", 0).getValue();
        } catch (ApiException e) {
            log.error("Could not load JPG Store Script utxo");
            throw new RuntimeException(e);
        }
    }

}
