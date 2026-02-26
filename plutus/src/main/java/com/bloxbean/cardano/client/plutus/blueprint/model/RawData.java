package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;

/**
 * Marker interface for types whose on-chain encoding is raw {@link PlutusData}
 * (e.g.&nbsp;{@code BytesPlutusData}) rather than {@code ConstrPlutusData}.
 *
 * <p>Implementing this interface lets the annotation processor detect bytes-wrapper
 * shared types via a clean type check instead of scanning for a {@code toPlutusData()}
 * method.</p>
 */
public interface RawData {
    PlutusData toPlutusData();
}
