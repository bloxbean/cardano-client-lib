package com.test;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;

import java.math.BigInteger;

@MetadataType
public class SampleOrder {

    private String recipient;
    private BigInteger amount;
    private Long timestamp;
    private Integer quantity;

    @MetadataField(key = "ref_id")
    private String referenceId;

    @MetadataIgnore
    private String internalId;

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public BigInteger getAmount() { return amount; }
    public void setAmount(BigInteger amount) { this.amount = amount; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getInternalId() { return internalId; }
    public void setInternalId(String internalId) { this.internalId = internalId; }

}
