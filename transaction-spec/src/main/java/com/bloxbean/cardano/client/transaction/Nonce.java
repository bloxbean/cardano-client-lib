package com.bloxbean.cardano.client.transaction;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.raw.RawNonce;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class Nonce {
    private byte[] hash;

    public DataItem serialize() throws CborSerializationException {
        Array array = new Array();
        if (hash != null) {
            array.add(new UnsignedInteger(1));
            array.add(new ByteString(hash));
        } else {
            array.add(new UnsignedInteger(0));
        }

        return array;
    }

    public static Nonce deserialize(DataItem di) throws CborDeserializationException {
        if (di == null) return null;
        return from(RawNonce.lenient(RawModels.span(di)));
    }

    /**
     * @param raw a nonce as received
     * @return the model, with no hash for the neutral nonce
     */
    public static Nonce from(RawNonce raw) {
        return new Nonce(raw.hash().orElse(null));
    }
}
