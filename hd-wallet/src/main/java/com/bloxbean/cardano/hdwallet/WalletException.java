package com.bloxbean.cardano.hdwallet;

public class WalletException extends RuntimeException {

    public WalletException() {
    }

    public WalletException(String msg) {
        super(msg);
    }

    public WalletException(Throwable cause) {
        super(cause);
    }

    public WalletException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
