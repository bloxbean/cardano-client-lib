package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;
import java.util.Map;

/**
 * POJO testing composite collection element support where elements are collections or maps.
 */
@MetadataType
public class SampleCompositeList {

    private List<List<String>> matrix;
    private List<Map<String, Integer>> records;
    private List<List<OrderStatus>> statusGrid;
    private List<Map<String, SampleNestedAddress>> addressRecords;

    public SampleCompositeList() {}

    public List<List<String>> getMatrix() { return matrix; }
    public void setMatrix(List<List<String>> matrix) { this.matrix = matrix; }

    public List<Map<String, Integer>> getRecords() { return records; }
    public void setRecords(List<Map<String, Integer>> records) { this.records = records; }

    public List<List<OrderStatus>> getStatusGrid() { return statusGrid; }
    public void setStatusGrid(List<List<OrderStatus>> statusGrid) { this.statusGrid = statusGrid; }

    public List<Map<String, SampleNestedAddress>> getAddressRecords() { return addressRecords; }
    public void setAddressRecords(List<Map<String, SampleNestedAddress>> addressRecords) { this.addressRecords = addressRecords; }
}
