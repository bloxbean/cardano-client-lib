package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;
import java.util.Map;

/**
 * Child class extending {@link SampleBaseMetadata} with complex field types.
 * Tests inheritance combined with enum, list, map, and nested fields.
 */
@MetadataType
public class SampleChildComplex extends SampleBaseMetadata {

    private OrderStatus status;
    private List<String> tags;
    private Map<String, Integer> scores;
    private SampleNestedAddress address;

    public SampleChildComplex() {}

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Map<String, Integer> getScores() { return scores; }
    public void setScores(Map<String, Integer> scores) { this.scores = scores; }

    public SampleNestedAddress getAddress() { return address; }
    public void setAddress(SampleNestedAddress address) { this.address = address; }
}
