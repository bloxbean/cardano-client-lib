package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

import java.math.BigInteger;

/**
 * Test decoder that divides a BigInteger value by a configurable scale factor.
 * <p>
 * Has <strong>no public no-arg constructor</strong> — requires a
 * {@link com.bloxbean.cardano.client.metadata.annotation.MetadataAdapterResolver}.
 */
public class ScaleDecoder implements MetadataTypeAdapter<Long> {

    private final long scaleFactor;

    public ScaleDecoder(long scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    @Override
    public Object toMetadata(Long value) {
        throw new UnsupportedOperationException("Decode-only");
    }

    @Override
    public Long fromMetadata(Object metadata) {
        return ((BigInteger) metadata).longValue() / scaleFactor;
    }
}
