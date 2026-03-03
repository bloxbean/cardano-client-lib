package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

/**
 * Wrapper for an asset name (arbitrary byte string up to 32 bytes).
 * <p>
 * Matches the {@code cardano/assets/AssetName} definition emitted by Aiken stdlib v2+
 * ({@code title: "AssetName", dataType: "bytes"}).
 */
public final class AssetName extends ByteArrayWrapper {

    public AssetName(byte[] value) {
        super(value);
    }

    public static AssetName of(byte[] value) {
        return new AssetName(value);
    }

    public static AssetName fromPlutusData(PlutusData data) {
        return new AssetName(((BytesPlutusData) data).getValue());
    }
}
