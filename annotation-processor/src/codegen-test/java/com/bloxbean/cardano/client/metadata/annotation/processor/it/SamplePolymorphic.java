package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * POJO with a polymorphic field dispatched via {@code @MetadataDiscriminator}.
 */
@MetadataType
public class SamplePolymorphic {
    private String name;
    private SampleMedia media;

    public SamplePolymorphic() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SampleMedia getMedia() { return media; }
    public void setMedia(SampleMedia media) { this.media = media; }
}
