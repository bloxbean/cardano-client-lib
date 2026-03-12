package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;

/**
 * POJO using public fields without getters/setters — tests direct field access codegen.
 */
@MetadataType
public class SampleDirectAccess {

    public String name;
    public Integer count;
    public List<String> tags;
    public SampleNestedAddress address;

    public SampleDirectAccess() {}
}
