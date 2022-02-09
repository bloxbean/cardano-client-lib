package com.bloxbean.cardano.client.plutus.api;

import com.bloxbean.cardano.client.transaction.spec.PlutusData;

public interface PlutusObjectConverter {

    PlutusData toPlutusData(Object o);

}
