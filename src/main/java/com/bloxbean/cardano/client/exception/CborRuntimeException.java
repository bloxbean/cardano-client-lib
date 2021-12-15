package com.bloxbean.cardano.client.exception;

public class CborRuntimeException extends RuntimeException {
    public CborRuntimeException(String message) {
        super(message);
    }

    public CborRuntimeException(String message, Exception e) {
        super(message, e);
    }

    public CborRuntimeException(Exception e) {
        super(e);
    }
}
