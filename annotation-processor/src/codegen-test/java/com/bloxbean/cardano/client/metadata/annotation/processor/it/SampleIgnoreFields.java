package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;

/**
 * POJO with {@code @MetadataIgnore} fields — proves ignored fields are excluded from serialization.
 */
@MetadataType
public class SampleIgnoreFields {

    private String name;

    @MetadataIgnore
    private String secret;

    private Integer count;

    @MetadataIgnore
    private List<String> internalTags;

    public SampleIgnoreFields() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }

    public List<String> getInternalTags() { return internalTags; }
    public void setInternalTags(List<String> internalTags) { this.internalTags = internalTags; }
}
