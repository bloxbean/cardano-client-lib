package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

/**
 * Simple stateless encoder that converts strings to upper case.
 * Demonstrates a basic {@code @MetadataEncoder} usage without requiring a resolver.
 */
public class UpperCaseEncoder implements MetadataTypeAdapter<String> {

    @Override
    public Object toMetadata(String value) {
        return value.toUpperCase();
    }

    @Override
    public String fromMetadata(Object metadata) {
        throw new UnsupportedOperationException("Encode-only");
    }
}
