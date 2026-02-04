package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

/** Byte-array representation of a cryptographic signature. */
public final class Signature extends ByteArrayWrapper {

    public Signature(byte[] value) {
        super(value);
    }

    public static Signature of(byte[] value) {
        return new Signature(value);
    }

}
