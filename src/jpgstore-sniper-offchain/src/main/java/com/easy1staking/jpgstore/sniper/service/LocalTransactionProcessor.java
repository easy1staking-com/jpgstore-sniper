package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.yaci.core.protocol.localtx.model.TxSubmissionRequest;
import com.bloxbean.cardano.yaci.helper.LocalTxSubmissionClient;
import com.bloxbean.cardano.yaci.helper.model.TxResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

//@Component
@RequiredArgsConstructor
@Slf4j
public class LocalTransactionProcessor implements TransactionProcessor {

    private final LocalTxSubmissionClient localTxSubmissionClient;

    private final AikenTransactionEvaluator aikenTransactionEvaluator;

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        log.info("About to submit tx");
        TxResult result = localTxSubmissionClient.submitTx(new TxSubmissionRequest(cborData)).block();
        if (result.isAccepted()) {
            log.info("TX accepted");
            return Result.success(result.getTxHash());
        } else {
            log.info("TX not accepted");
            return Result.error(result.getErrorCbor());
        }
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
        log.info("Running aiken tx evaluation");
        long startTime = System.currentTimeMillis();
        Result<List<EvaluationResult>> listResult = aikenTransactionEvaluator.evaluateTx(cbor, inputUtxos);
        log.info("Tx evaluation ran in {}ms", System.currentTimeMillis() - startTime);
        return listResult;
    }
}
