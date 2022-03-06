package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;

public interface Script {

    DataItem serializeAsDataItem() throws CborSerializationException;

    default byte[] serialize() throws CborSerializationException {
        DataItem di = serializeAsDataItem();

        try {
            return CborSerializationUtil.serialize(di);
        } catch (CborException e) {
            throw new CborSerializationException("Cbor serializaion error", e);
        }
    }

    byte[] getScriptHash() throws CborSerializationException;

    String getPolicyId() throws CborSerializationException;
}
