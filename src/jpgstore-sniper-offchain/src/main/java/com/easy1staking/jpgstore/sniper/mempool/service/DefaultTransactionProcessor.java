package com.easy1staking.jpgstore.sniper.mempool.service;

import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

//@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultTransactionProcessor implements TransactionProcessor {

    private final TransactionEvaluator transactionEvaluator;

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
        return transactionEvaluator.evaluateTx(cbor, inputUtxos);
    }

    @PostConstruct
    public void logInfo() {
        log.info("running evaluator: {}", transactionEvaluator.getClass());
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        return null;
    }

}
