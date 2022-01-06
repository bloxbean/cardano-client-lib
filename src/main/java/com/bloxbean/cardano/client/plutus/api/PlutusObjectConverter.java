package com.bloxbean.cardano.client.plutus.api;

import com.bloxbean.cardano.client.transaction.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;

public interface PlutusObjectConverter {

    public ConstrPlutusData convertToPlutusData(Object o);

    public <T> T convertToObject(PlutusData plutusData, Class<T> tClass);
}
