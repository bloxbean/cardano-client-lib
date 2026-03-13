package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;

@MetadataType
public record RoyaltyInfo(
        @MetadataField(required = true) String address,
        @MetadataField(key = "rate_bps") BigInteger rateBps
) {}
