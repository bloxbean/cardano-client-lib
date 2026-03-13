package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;
import java.time.Instant;

@MetadataType
public record DelegationInstruction(
        @MetadataField(key = "pool_id", required = true) String poolId,
        @MetadataField(key = "weight_bps") BigInteger weightBps,
        @MetadataField(adapter = EpochAdapter.class) Instant delegatedAt
) {}
