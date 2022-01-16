package com.bloxbean.cardano.client.plutus.api;

import com.bloxbean.cardano.client.transaction.spec.ConstrPlutusData;

public interface PlutusObjectConverter {

    ConstrPlutusData toPlutusData(Object o);

}
