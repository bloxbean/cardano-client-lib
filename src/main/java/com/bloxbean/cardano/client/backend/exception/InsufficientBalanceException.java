package com.bloxbean.cardano.client.backend.exception;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }

    public InsufficientBalanceException(String message, Exception e) {
        super(message, e);
    }
}
