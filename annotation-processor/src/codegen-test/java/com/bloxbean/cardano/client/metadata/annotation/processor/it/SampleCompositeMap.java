package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;
import java.util.Map;

/**
 * POJO testing composite {@code Map<String, V>} field support where V is a collection or map.
 */
@MetadataType
public class SampleCompositeMap {

    private Map<String, List<String>> tagsByCategory;
    private Map<String, Map<String, Integer>> nestedScores;
    private Map<String, List<OrderStatus>> statusesByGroup;
    private Map<String, List<SampleNestedAddress>> addressesByType;

    public SampleCompositeMap() {}

    public Map<String, List<String>> getTagsByCategory() { return tagsByCategory; }
    public void setTagsByCategory(Map<String, List<String>> tagsByCategory) { this.tagsByCategory = tagsByCategory; }

    public Map<String, Map<String, Integer>> getNestedScores() { return nestedScores; }
    public void setNestedScores(Map<String, Map<String, Integer>> nestedScores) { this.nestedScores = nestedScores; }

    public Map<String, List<OrderStatus>> getStatusesByGroup() { return statusesByGroup; }
    public void setStatusesByGroup(Map<String, List<OrderStatus>> statusesByGroup) { this.statusesByGroup = statusesByGroup; }

    public Map<String, List<SampleNestedAddress>> getAddressesByType() { return addressesByType; }
    public void setAddressesByType(Map<String, List<SampleNestedAddress>> addressesByType) { this.addressesByType = addressesByType; }
}
