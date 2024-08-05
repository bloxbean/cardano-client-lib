package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;

/**
 * Implement this interface to convert an object to PlutusData and vice versa.
 *
 * @param <T>
 */
public interface Data<T> {

    ConstrPlutusData toPlutusData();

    T fromPlutusData(ConstrPlutusData data);
}
