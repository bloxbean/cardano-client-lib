package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Integration test POJO with {@code required = true} fields.
 * The annotation processor generates {@code SampleRequiredMetadataConverter}.
 */
@MetadataType
public class SampleRequired {

    @MetadataField(required = true)
    private String name;

    @MetadataField(key = "ref_id", required = true)
    private int refId;

    private String optional;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRefId() { return refId; }
    public void setRefId(int refId) { this.refId = refId; }

    public String getOptional() { return optional; }
    public void setOptional(String optional) { this.optional = optional; }
}
