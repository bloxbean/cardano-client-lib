package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

/** Hash of arbitrary data payload (Blake2b-256). */
public final class DataHash extends Hash {

    public DataHash(byte[] value) {
        super(value, "Blake2b_256");
    }

    public static DataHash of(byte[] value) {
        return new DataHash(value);
    }

    public static DataHash fromPlutusData(PlutusData data) {
        return new DataHash(((BytesPlutusData) data).getValue());
    }
}
