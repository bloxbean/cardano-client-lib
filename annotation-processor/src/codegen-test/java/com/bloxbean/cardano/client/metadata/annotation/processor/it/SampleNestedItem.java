package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;

/**
 * Nested POJO for testing {@code @MetadataType} composition in collections.
 */
@MetadataType
public class SampleNestedItem {

    private String name;
    private BigInteger quantity;

    public SampleNestedItem() {}

    public SampleNestedItem(String name, BigInteger quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigInteger getQuantity() { return quantity; }
    public void setQuantity(BigInteger quantity) { this.quantity = quantity; }
}
