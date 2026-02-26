package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

/** Byte-array representation of a verification key as defined by the Aiken standard library. */
public final class VerificationKey extends ByteArrayWrapper {

    public VerificationKey(byte[] value) {
        super(value);
    }

    public static VerificationKey of(byte[] value) {
        return new VerificationKey(value);
    }

    public static VerificationKey fromPlutusData(PlutusData data) {
        return new VerificationKey(((BytesPlutusData) data).getValue());
    }
}
