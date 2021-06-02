package com.bloxbean.cardano.client.exception;

public class AddressRuntimeException extends RuntimeException {
    public AddressRuntimeException(String message) {
        super(message);
    }

    public AddressRuntimeException(String message, Exception e) {
        super(message, e);
    }
}
