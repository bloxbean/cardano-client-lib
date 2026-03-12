package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;
import java.util.Map;

/**
 * POJO testing {@code Map<String, V>} field support with various value types.
 */
@MetadataType
public class SampleMap {

    private Map<String, String> settings;
    private Map<String, Integer> scores;
    private Map<String, BigInteger> amounts;
    private Map<String, OrderStatus> statusMap;
    private Map<String, SampleNestedAddress> addresses;

    public SampleMap() {}

    public Map<String, String> getSettings() { return settings; }
    public void setSettings(Map<String, String> settings) { this.settings = settings; }

    public Map<String, Integer> getScores() { return scores; }
    public void setScores(Map<String, Integer> scores) { this.scores = scores; }

    public Map<String, BigInteger> getAmounts() { return amounts; }
    public void setAmounts(Map<String, BigInteger> amounts) { this.amounts = amounts; }

    public Map<String, OrderStatus> getStatusMap() { return statusMap; }
    public void setStatusMap(Map<String, OrderStatus> statusMap) { this.statusMap = statusMap; }

    public Map<String, SampleNestedAddress> getAddresses() { return addresses; }
    public void setAddresses(Map<String, SampleNestedAddress> addresses) { this.addresses = addresses; }
}
