package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

import java.math.BigInteger;
import java.time.Instant;

public class EpochAdapter implements MetadataTypeAdapter<Instant> {
    @Override
    public Object toMetadata(Instant value) {
        return BigInteger.valueOf(value.getEpochSecond());
    }

    @Override
    public Instant fromMetadata(Object metadata) {
        return Instant.ofEpochSecond(((BigInteger) metadata).longValue());
    }
}
