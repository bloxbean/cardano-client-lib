package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Nested POJO that itself contains a nested type — used for deep-nesting tests.
 */
@MetadataType
public class SampleDeepInner {

    private String label;
    private SampleNestedAddress address;

    public SampleDeepInner() {}

    public SampleDeepInner(String label, SampleNestedAddress address) {
        this.label = label;
        this.address = address;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public SampleNestedAddress getAddress() { return address; }
    public void setAddress(SampleNestedAddress address) { this.address = address; }
}
