package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDiscriminator;
import com.bloxbean.cardano.client.metadata.annotation.MetadataSubtype;

@MetadataDiscriminator(key = "type", subtypes = {
        @MetadataSubtype(value = "image", type = ImageContent.class),
        @MetadataSubtype(value = "audio", type = AudioContent.class)
})
public interface MediaContent {
}
