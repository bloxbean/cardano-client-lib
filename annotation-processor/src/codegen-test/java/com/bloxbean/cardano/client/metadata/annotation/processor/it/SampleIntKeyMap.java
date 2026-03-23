package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;
import java.util.Map;

/**
 * POJO testing {@code Map<Integer/Long/BigInteger, V>} field support with various value types.
 */
@MetadataType
public class SampleIntKeyMap {

    private Map<Integer, String> intKeyedSettings;
    private Map<Long, String> longKeyedLabels;
    private Map<BigInteger, String> bigIntKeyedNames;
    private Map<Integer, OrderStatus> intKeyedStatuses;
    private Map<BigInteger, SampleNestedAddress> bigIntKeyedAddresses;

    public SampleIntKeyMap() {}

    public Map<Integer, String> getIntKeyedSettings() { return intKeyedSettings; }
    public void setIntKeyedSettings(Map<Integer, String> intKeyedSettings) { this.intKeyedSettings = intKeyedSettings; }

    public Map<Long, String> getLongKeyedLabels() { return longKeyedLabels; }
    public void setLongKeyedLabels(Map<Long, String> longKeyedLabels) { this.longKeyedLabels = longKeyedLabels; }

    public Map<BigInteger, String> getBigIntKeyedNames() { return bigIntKeyedNames; }
    public void setBigIntKeyedNames(Map<BigInteger, String> bigIntKeyedNames) { this.bigIntKeyedNames = bigIntKeyedNames; }

    public Map<Integer, OrderStatus> getIntKeyedStatuses() { return intKeyedStatuses; }
    public void setIntKeyedStatuses(Map<Integer, OrderStatus> intKeyedStatuses) { this.intKeyedStatuses = intKeyedStatuses; }

    public Map<BigInteger, SampleNestedAddress> getBigIntKeyedAddresses() { return bigIntKeyedAddresses; }
    public void setBigIntKeyedAddresses(Map<BigInteger, SampleNestedAddress> bigIntKeyedAddresses) { this.bigIntKeyedAddresses = bigIntKeyedAddresses; }
}
