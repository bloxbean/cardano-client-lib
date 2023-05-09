package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;

import java.util.List;

/**
 * Implement this interface to provide transaction submission capability.
 */
public interface TransactionProcessor {

    Result<String> submitTransaction(byte[] cborData) throws ApiException;

    Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException;
}
