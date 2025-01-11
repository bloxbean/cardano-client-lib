package com.bloxbean.cardano.client.account;

public class AccountException extends RuntimeException {
    public AccountException() {
    }

    public AccountException(String msg) {
        super(msg);
    }

    public AccountException(Throwable cause) {
        super(cause);
    }

    public AccountException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
