package com.bloxbean.cardano.client.backend.exception;

public class ApiRuntimeException extends RuntimeException {

    public ApiRuntimeException(String message) {
        super(message);
    }

    public ApiRuntimeException(Exception exception) {
        super(exception);
    }

    public ApiRuntimeException(String message, Exception e) {
        super(message, e);
    }
}
