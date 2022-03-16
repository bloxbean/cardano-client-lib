package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;

public interface TransactionProcessor {

    Result<String> submitTransaction(byte[] cborData) throws ApiException;
}
