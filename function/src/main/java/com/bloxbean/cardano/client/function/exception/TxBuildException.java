package com.bloxbean.cardano.client.function.exception;

public class TxBuildException extends RuntimeException {

    public TxBuildException(String msg) {
        super(msg);
    }

    public TxBuildException(Exception exception) {
        super(exception);
    }

    public TxBuildException(String msg, Exception ex) {
        super(msg, ex);
    }
}
