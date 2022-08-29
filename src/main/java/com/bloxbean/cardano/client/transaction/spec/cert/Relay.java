package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborSerializationException;

public interface Relay {
    Array serialize() throws CborSerializationException;
}
