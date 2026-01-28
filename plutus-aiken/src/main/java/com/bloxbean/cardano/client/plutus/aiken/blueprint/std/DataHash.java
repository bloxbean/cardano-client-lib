package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

/** Hash of arbitrary data payload (Blake2b-256). */
public final class DataHash extends Hash {

    public DataHash(byte[] value) {
        super(value, "Blake2b_256");
    }

    public static DataHash of(byte[] value) {
        return new DataHash(value);
    }
}
