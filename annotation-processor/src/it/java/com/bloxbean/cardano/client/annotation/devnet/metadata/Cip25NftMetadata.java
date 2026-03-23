package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDecoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataEncoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MetadataType(label = 721)
public class Cip25NftMetadata extends NftBaseMetadata {
    @MetadataField(required = true)
    private String name;

    private String image;

    @MetadataField(key = "media_type", defaultValue = "image/png")
    private String mediaType;

    private Optional<String> description;

    private List<NftFileDetail> files;

    private MediaContent displayMedia;

    @MetadataField(key = "policy_id", enc = MetadataFieldType.STRING_HEX)
    private byte[] policyId;

    private NftRarity rarity;

    @MetadataEncoder(EpochAdapter.class)
    @MetadataDecoder(EpochAdapter.class)
    private Instant mintedAt;

    private Map<String, String> attributes;

    @MetadataIgnore
    private String internalTrackingId;
}
