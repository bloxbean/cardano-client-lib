package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;

public interface Data<T> {

    ConstrPlutusData toPlutusData();

    T fromPlutusData(ConstrPlutusData data);
}
