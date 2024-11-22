package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;

/**
 * Implement this interface to convert an object to PlutusData.
 *
 * @param <T>
 */
public interface Data<T> {

    ConstrPlutusData toPlutusData();

}
