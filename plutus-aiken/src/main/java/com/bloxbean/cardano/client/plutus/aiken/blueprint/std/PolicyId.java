package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

/**
 * Wrapper for a minting policy identifier (28-byte script hash).
 * <p>
 * Matches the {@code cardano/assets/PolicyId} definition emitted by Aiken stdlib v2+
 * ({@code title: "PolicyId", dataType: "bytes"}).
 */
public final class PolicyId extends ByteArrayWrapper {

    public PolicyId(byte[] value) {
        super(value);
    }

    public static PolicyId of(byte[] value) {
        return new PolicyId(value);
    }

    public static PolicyId fromPlutusData(PlutusData data) {
        return new PolicyId(((BytesPlutusData) data).getValue());
    }
}
