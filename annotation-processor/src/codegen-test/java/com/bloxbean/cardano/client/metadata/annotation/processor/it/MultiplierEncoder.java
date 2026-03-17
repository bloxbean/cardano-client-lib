package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

import java.math.BigInteger;

/**
 * Test encoder that multiplies a long value by 1000 during serialization.
 * Demonstrates a simple transformation encoder for codegen testing.
 */
public class MultiplierEncoder implements MetadataTypeAdapter<Long> {

    @Override
    public Object toMetadata(Long value) {
        return BigInteger.valueOf(value * 1000);
    }

    @Override
    public Long fromMetadata(Object metadata) {
        throw new UnsupportedOperationException("Encode-only — use MultiplierDecoder for deserialization");
    }
}
