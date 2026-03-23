package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

@MetadataType
public record NftFileDetail(
        @MetadataField(required = true) String name,
        String src,
        @MetadataField(key = "media_type") String mediaType
) {}
