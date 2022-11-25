package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

public interface COSEItem {

    default byte[] serializeAsBytes() {
        DataItem di = serialize();

        try {
            return CborSerializationUtil.serialize(di, false);
        } catch (CborException e) {
            throw new CborRuntimeException("Cbor serializaion error", e);
        }
    }

    DataItem serialize();
}
