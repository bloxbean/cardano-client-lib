package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Adapter that serializes {@link Instant} as epoch seconds (BigInteger) in metadata.
 */
public class EpochSecondsAdapter implements MetadataTypeAdapter<Instant> {

    @Override
    public Object toMetadata(Instant value) {
        return BigInteger.valueOf(value.getEpochSecond());
    }

    @Override
    public Instant fromMetadata(Object metadata) {
        return Instant.ofEpochSecond(((BigInteger) metadata).longValue());
    }
}
