package com.bloxbean.cardano.client.supplier.local;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.yaci.core.protocol.localtx.model.TxSubmissionRequest;
import com.bloxbean.cardano.yaci.helper.LocalTxSubmissionClient;
import com.bloxbean.cardano.yaci.helper.model.TxResult;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Transaction processor implementation for local Cardano node. This class uses LocalTxSubmissionClient
 * to submit transactions to local Cardano node. This class is not thread safe.
 */
public class LocalTransactionProcessor implements TransactionProcessor {
    private LocalTxSubmissionClient txSubmissionClient;

    public LocalTransactionProcessor(LocalTxSubmissionClient txSubmissionClient) {
        this.txSubmissionClient = txSubmissionClient;
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) {
        TxSubmissionRequest txSubmissionRequest = new TxSubmissionRequest(cborData);
        Mono<TxResult> mono = txSubmissionClient.submitTx(txSubmissionRequest);
        TxResult txResult = mono.block(Duration.ofSeconds(20));

        if(txResult.isAccepted()) {
            return Result.success(txResult.getTxHash()).withValue(txResult.getTxHash()).code(200);
        } else {
            return Result.success(txResult.getErrorCbor()).withValue(txResult.getErrorCbor()).code(500);
        }
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) {
        throw new UnsupportedOperationException("Not supported");
    }
}
