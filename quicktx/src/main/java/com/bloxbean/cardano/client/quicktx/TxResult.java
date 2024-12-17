package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Result;

/**
 * Represents the result of a transaction.
 *
 * This class extends the Result class specialized with the String type, providing
 * additional transaction-specific functionality, such as associating a transaction
 * status and retrieving transaction-specific details like transaction hash.
 */
public class TxResult extends Result<String> {
    private TxStatus txStatus;

    protected TxResult(boolean successful) {
        super(successful);
    }

    protected TxResult(boolean successful, String response) {
        super(successful, response);
    }

    public TxResult withTxStatus(TxStatus status) {
        this.txStatus = status;
        return this;
    }

    public TxStatus getTxStatus() {
        return txStatus;
    }

    public String getTxHash() {
        return getValue();
    }

    public static TxResult fromResult(Result<String> result) {
        var txResult = new TxResult(result.isSuccessful(), result.getResponse());
        txResult.withValue(result.getValue());

        return txResult;
    }

}
