package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;

/**
 * Plutus data as received: a witness datum, or the payload of an inline datum.
 *
 * @param span the data, as encoded
 */
public record RawDatum(CborSpan span) {

    /**
     * @return the datum hash, {@code blake2b256} of the data as encoded
     */
    public byte[] hash() {
        return Blake2bUtil.blake2bHash256(span.bytes());
    }
}
