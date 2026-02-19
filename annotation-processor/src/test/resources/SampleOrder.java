package com.test;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
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

    private String description;

    /** Integer stored as String in metadata */
    @MetadataField(as = MetadataFieldType.STRING)
    private Integer statusCode;

    /** byte[] stored as hex String in metadata */
    @MetadataField(key = "payload", as = MetadataFieldType.STRING_HEX)
    private byte[] payloadBytes;

    /** byte[] stored as Base64 String in metadata */
    @MetadataField(key = "signature", as = MetadataFieldType.STRING_BASE64)
    private byte[] signatureBytes;

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

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public byte[] getPayloadBytes() { return payloadBytes; }
    public void setPayloadBytes(byte[] payloadBytes) { this.payloadBytes = payloadBytes; }

    public byte[] getSignatureBytes() { return signatureBytes; }
    public void setSignatureBytes(byte[] signatureBytes) { this.signatureBytes = signatureBytes; }
}
