package com.bloxbean.cardano.client.quicktx.blueprint.extender.common;

public class TxResult {
    private String txHash;
    private String error;
    private boolean isSuccessful;

    private TxResult(String txHash, String error, boolean isSuccessful) {
        this.txHash = txHash;
        this.error = error;
        this.isSuccessful = isSuccessful;
    }


    public static TxResult success(String txHash) {
        return new TxResult(txHash, null, true);
    }

    public static TxResult error(String error) {
        return new TxResult(null, error, false);
    }

    public String getTxHash() {
        return txHash;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

}
