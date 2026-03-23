package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

/**
 * Stateful decoder that strips a configurable prefix from string values.
 * <p>
 * Has <strong>no public no-arg constructor</strong> — the prefix must be injected
 * via a {@link com.bloxbean.cardano.client.metadata.annotation.MetadataAdapterResolver}.
 */
public class PrefixDecoder implements MetadataTypeAdapter<String> {

    private final String prefix;

    public PrefixDecoder(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Object toMetadata(String value) {
        throw new UnsupportedOperationException("Decode-only — use PrefixEncoder for serialization");
    }

    @Override
    public String fromMetadata(Object metadata) {
        String raw = (String) metadata;
        return raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw;
    }
}
