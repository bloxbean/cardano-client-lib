package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;

/**
 * Integration test POJO with {@code defaultValue} on various field types.
 * The annotation processor generates {@code SampleDefaultValueMetadataConverter}.
 */
@MetadataType
public class SampleDefaultValue {

    @MetadataField(defaultValue = "UNKNOWN")
    private String status;

    @MetadataField(defaultValue = "42")
    private int count;

    @MetadataField(defaultValue = "100")
    private long timestamp;

    @MetadataField(defaultValue = "true")
    private boolean active;

    @MetadataField(defaultValue = "999")
    private BigInteger amount;

    private String noDefault;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public BigInteger getAmount() { return amount; }
    public void setAmount(BigInteger amount) { this.amount = amount; }

    public String getNoDefault() { return noDefault; }
    public void setNoDefault(String noDefault) { this.noDefault = noDefault; }
}
