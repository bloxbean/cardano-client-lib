package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

/** Byte-array representation of a verification key as defined by the Aiken standard library. */
public final class VerificationKey extends ByteArrayWrapper {

    public VerificationKey(byte[] value) {
        super(value);
    }

    public static VerificationKey of(byte[] value) {
        return new VerificationKey(value);
    }
}
