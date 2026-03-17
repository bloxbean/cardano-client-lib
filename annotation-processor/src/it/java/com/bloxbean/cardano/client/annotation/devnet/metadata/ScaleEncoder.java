package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

import java.math.BigInteger;

/**
 * Test encoder that multiplies a long value by a configurable scale factor.
 * <p>
 * Has <strong>no public no-arg constructor</strong> — requires a
 * {@link com.bloxbean.cardano.client.metadata.annotation.MetadataAdapterResolver}.
 */
public class ScaleEncoder implements MetadataTypeAdapter<Long> {

    private final long scaleFactor;

    public ScaleEncoder(long scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    @Override
    public Object toMetadata(Long value) {
        return BigInteger.valueOf(value * scaleFactor);
    }

    @Override
    public Long fromMetadata(Object metadata) {
        throw new UnsupportedOperationException("Encode-only");
    }
}
