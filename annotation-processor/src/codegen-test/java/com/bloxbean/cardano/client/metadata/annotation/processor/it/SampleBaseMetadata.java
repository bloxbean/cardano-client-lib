package com.bloxbean.cardano.client.metadata.annotation.processor.it;

/**
 * Base class for inheritance testing (no {@code @MetadataType} annotation).
 * Fields and accessors here should be included in child converters.
 */
public class SampleBaseMetadata {

    private String version;
    private String author;

    public SampleBaseMetadata() {}

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}
