package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;

public interface COSEBaseSign {

    default byte[] serializeAsBytes() throws CborSerializationException {
        DataItem di = serialize();

        try {
            return CborSerializationUtil.serialize(di, false);
        } catch (CborException e) {
            throw new CborSerializationException("Cbor serializaion error", e);
        }
    }

    DataItem serialize();
}
