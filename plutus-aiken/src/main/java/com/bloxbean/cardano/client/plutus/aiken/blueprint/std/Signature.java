package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

/** Byte-array representation of a cryptographic signature. */
public final class Signature extends ByteArrayWrapper {

    public Signature(byte[] value) {
        super(value);
    }

    public static Signature of(byte[] value) {
        return new Signature(value);
    }

    public static Signature fromPlutusData(PlutusData data) {
        return new Signature(((BytesPlutusData) data).getValue());
    }
}
