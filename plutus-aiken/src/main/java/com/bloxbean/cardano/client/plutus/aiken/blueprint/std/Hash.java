package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import java.util.Optional;

/** Generic hash wrapper carrying raw bytes and an optional algorithm hint. */
public class Hash extends ByteArrayWrapper {

    private final String algorithm;

    public Hash(byte[] value) {
        this(value, null);
    }

    public Hash(byte[] value, String algorithm) {
        super(value);
        this.algorithm = algorithm;
    }

    public static Hash of(byte[] value) {
        return new Hash(value, null);
    }

    public static Hash of(byte[] value, String algorithm) {
        return new Hash(value, algorithm);
    }

    public Optional<String> algorithm() {
        return Optional.ofNullable(algorithm);
    }
}
