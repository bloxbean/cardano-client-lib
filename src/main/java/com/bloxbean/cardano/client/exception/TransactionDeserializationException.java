package com.bloxbean.cardano.client.exception;

public class TransactionDeserializationException extends Exception {

    public TransactionDeserializationException(String message) {
        super(message);
    }

    public TransactionDeserializationException(String message, Exception e) {
        super(message, e);
    }
}
