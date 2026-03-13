package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.Map;
import java.util.Optional;

/**
 * Complex record testing enum, Optional, Map, and nested record types.
 */
@MetadataType
public record SampleRecordComplex(
        String label,
        OrderStatus status,
        Optional<String> nickname,
        Map<String, Integer> scores,
        @MetadataIgnore String internal
) {}
