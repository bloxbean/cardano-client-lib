package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;

import java.util.List;

public interface TransactionEvaluator {
    Result<List<EvaluationResult>> evaluateTx(byte[] cbor) throws ApiException;
}
