package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Data
@NoArgsConstructor
@MetadataType(label = 100)
public class Cip68ReferenceDatum {
    @MetadataField(required = true)
    private String name;

    private String image;

    private Optional<String> description;

    @MetadataField(key = "media_type")
    private String mediaType;

    private MediaContent displayMedia;

    private RoyaltyInfo royalty;

    @MetadataField(key = "int_version", defaultValue = "1")
    private int metadataVersion;

    @MetadataField(key = "extra_data", enc = MetadataFieldType.STRING_BASE64)
    private byte[] extraData;

    private Map<String, List<String>> traits;

    private List<MetadataStandard> standards;

    private Set<String> tags;

    @MetadataIgnore
    private long cachedHash;
}
