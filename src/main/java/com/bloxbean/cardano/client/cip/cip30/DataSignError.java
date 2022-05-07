package com.bloxbean.cardano.client.cip.cip30;

public class DataSignError extends Exception {

    public DataSignError(String message, Exception e) {
        super(message, e);
    }
}
