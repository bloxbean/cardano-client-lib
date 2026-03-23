package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDiscriminator;
import com.bloxbean.cardano.client.metadata.annotation.MetadataSubtype;

/**
 * Polymorphic parent type using {@code @MetadataDiscriminator}.
 * Does NOT carry {@code @MetadataType} — only the concrete subtypes do.
 */
@MetadataDiscriminator(key = "type", subtypes = {
        @MetadataSubtype(value = "image", type = SampleImageMedia.class),
        @MetadataSubtype(value = "audio", type = SampleAudioMedia.class)
})
public interface SampleMedia {
}
