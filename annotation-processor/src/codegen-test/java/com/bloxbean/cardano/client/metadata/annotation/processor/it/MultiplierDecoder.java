package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

import java.math.BigInteger;

/**
 * Test decoder that divides a BigInteger by 1000 during deserialization.
 * Counterpart to {@link MultiplierEncoder} for codegen testing.
 */
public class MultiplierDecoder implements MetadataTypeAdapter<Long> {

    @Override
    public Object toMetadata(Long value) {
        throw new UnsupportedOperationException("Decode-only — use MultiplierEncoder for serialization");
    }

    @Override
    public Long fromMetadata(Object metadata) {
        return ((BigInteger) metadata).longValue() / 1000;
    }
}
