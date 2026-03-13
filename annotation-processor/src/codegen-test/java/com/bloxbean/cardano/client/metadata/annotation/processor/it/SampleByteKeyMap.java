package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import java.math.BigInteger;
import java.util.Map;

@MetadataType
public class SampleByteKeyMap {
    private Map<byte[], String> labels;
    private Map<byte[], BigInteger> amounts;

    public Map<byte[], String> getLabels() { return labels; }
    public void setLabels(Map<byte[], String> labels) { this.labels = labels; }

    public Map<byte[], BigInteger> getAmounts() { return amounts; }
    public void setAmounts(Map<byte[], BigInteger> amounts) { this.amounts = amounts; }
}
