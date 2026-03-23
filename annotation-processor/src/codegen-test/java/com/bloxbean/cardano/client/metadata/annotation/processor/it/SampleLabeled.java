package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * POJO testing {@code @MetadataType(label = 721)} — generates
 * {@code toMetadata} and {@code fromMetadata} convenience methods.
 */
@MetadataType(label = 721)
public class SampleLabeled {

    private String name;
    private String description;

    public SampleLabeled() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
