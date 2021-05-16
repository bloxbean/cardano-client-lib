package com.bloxbean.cardano.client.backend.impl.blockfrost.excpetion;

public class BlockfrostConfigurationException extends RuntimeException {

    public BlockfrostConfigurationException(String message) {
        super(message);
    }

    public BlockfrostConfigurationException(String message, Exception exception) {
        super(message, exception);
    }
}
