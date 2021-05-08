package com.bloxbean.cardano.client.exception;

public class TransactionSerializationException extends Exception {

    public TransactionSerializationException(String message) {
        super(message);
    }

    public TransactionSerializationException(String message, Exception e) {
        super(message, e);
    }
}
