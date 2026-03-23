package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Child class extending a non-annotated base — tests that inherited fields are included.
 */
@MetadataType
public class SampleChildMetadata extends SampleBaseMetadata {

    private String name;
    private String description;

    public SampleChildMetadata() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
