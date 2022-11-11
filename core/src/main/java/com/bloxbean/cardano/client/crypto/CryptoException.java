package com.bloxbean.cardano.client.crypto;

public class CryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CryptoException() {
    }

    public CryptoException(String msg) {
        super(msg);
    }

    public CryptoException(Throwable cause) {
        super(cause);
    }

    public CryptoException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
