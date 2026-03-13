package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;
import java.util.List;

/**
 * Java record annotated with {@code @MetadataType} — used to verify record support
 * in the metadata annotation processor.
 */
@MetadataType(label = 900)
public record SampleRecord(
        String name,
        int age,
        @MetadataField(key = "addr") String address,
        BigInteger amount,
        List<String> tags
) {}
