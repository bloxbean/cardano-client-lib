package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;

import java.io.ByteArrayOutputStream;

public interface Script {

    DataItem serializeAsDataItem() throws CborSerializationException;

    default byte[] serialize() throws CborSerializationException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborBuilder cborBuilder = new CborBuilder();
        DataItem di = serializeAsDataItem();
        cborBuilder.add(di);
        try {
            new CborEncoder(baos).encode(cborBuilder.build());
        } catch (CborException e) {
            throw new CborSerializationException("Cbor serializaion error", e);
        }
        return baos.toByteArray();
    }

    byte[] getScriptHash() throws CborSerializationException;

    String getPolicyId() throws CborSerializationException;
}
