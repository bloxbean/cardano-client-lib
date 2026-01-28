package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

/** Hash of a verification key (Blake2b-224). */
public final class VerificationKeyHash extends Hash {

    public VerificationKeyHash(byte[] value) {
        super(value, "Blake2b_224");
    }

    public static VerificationKeyHash of(byte[] value) {
        return new VerificationKeyHash(value);
    }
}
