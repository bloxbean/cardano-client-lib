package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Child class that shadows a parent field ({@code version}).
 * The child's field should take precedence.
 */
@MetadataType
public class SampleChildShadow extends SampleBaseMetadata {

    private String version; // shadows parent
    private String title;

    public SampleChildShadow() {}

    @Override
    public String getVersion() { return version; }
    @Override
    public void setVersion(String version) { this.version = version; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
