package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;
import java.util.Map;

/**
 * Labeled POJO with complex field types — tests label wrapping with list, enum, map, nested.
 */
@MetadataType(label = 42)
public class SampleLabeledComplex {

    private String name;
    private List<String> tags;
    private OrderStatus status;
    private Map<String, Integer> scores;
    private SampleNestedAddress address;

    public SampleLabeledComplex() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Map<String, Integer> getScores() { return scores; }
    public void setScores(Map<String, Integer> scores) { this.scores = scores; }

    public SampleNestedAddress getAddress() { return address; }
    public void setAddress(SampleNestedAddress address) { this.address = address; }
}
