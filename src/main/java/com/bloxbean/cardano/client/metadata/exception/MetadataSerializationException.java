package com.bloxbean.cardano.client.metadata.exception;

public class MetadataSerializationException extends RuntimeException {
    public MetadataSerializationException(String messsage) {
        super(messsage);
    }

    public MetadataSerializationException(String message, Exception e) {
        super(message, e);
    }
}
