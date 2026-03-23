package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

/**
 * Stateful encoder that prepends a configurable prefix to string values.
 * <p>
 * Has <strong>no public no-arg constructor</strong> — the prefix must be injected
 * via a {@link com.bloxbean.cardano.client.metadata.annotation.MetadataAdapterResolver},
 * simulating a real-world scenario where context (e.g., a network identifier or
 * Spring-managed configuration bean) must be provided at runtime.
 */
public class PrefixEncoder implements MetadataTypeAdapter<String> {

    private final String prefix;

    public PrefixEncoder(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Object toMetadata(String value) {
        return prefix + value;
    }

    @Override
    public String fromMetadata(Object metadata) {
        throw new UnsupportedOperationException("Encode-only — use PrefixDecoder for deserialization");
    }
}
