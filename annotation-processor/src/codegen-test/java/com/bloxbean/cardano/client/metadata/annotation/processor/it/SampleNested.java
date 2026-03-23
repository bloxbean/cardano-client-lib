package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;
import java.util.Optional;

/**
 * POJO testing nested {@code @MetadataType} composition:
 * scalar nested, List of nested, Optional nested.
 */
@MetadataType
public class SampleNested {

    private String orderId;
    private SampleNestedAddress address;
    private List<SampleNestedItem> items;
    private Optional<SampleNestedAddress> billingAddress;

    public SampleNested() {
        this.billingAddress = Optional.empty();
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public SampleNestedAddress getAddress() { return address; }
    public void setAddress(SampleNestedAddress address) { this.address = address; }

    public List<SampleNestedItem> getItems() { return items; }
    public void setItems(List<SampleNestedItem> items) { this.items = items; }

    public Optional<SampleNestedAddress> getBillingAddress() { return billingAddress; }
    public void setBillingAddress(Optional<SampleNestedAddress> billingAddress) { this.billingAddress = billingAddress; }
}
