package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataEncoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataDecoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Codegen test POJO covering {@code @MetadataEncoder} and {@code @MetadataDecoder}.
 * <ul>
 *   <li>{@code encodedOnly} — encoder-only, deserialization falls back to built-in</li>
 *   <li>{@code decodedOnly} — decoder-only, serialization falls back to built-in</li>
 *   <li>{@code bothDirections} — separate encoder and decoder</li>
 *   <li>{@code plainValue} — baseline: no encoder/decoder</li>
 * </ul>
 */
@MetadataType(label = 960)
public class SampleEncoderDecoder {

    private String name;

    @MetadataEncoder(MultiplierEncoder.class)
    @MetadataField(key = "encoded_only")
    private long encodedOnly;

    @MetadataDecoder(MultiplierDecoder.class)
    @MetadataField(key = "decoded_only")
    private long decodedOnly;

    @MetadataEncoder(MultiplierEncoder.class)
    @MetadataDecoder(MultiplierDecoder.class)
    @MetadataField(key = "both")
    private long bothDirections;

    @MetadataField(key = "plain")
    private long plainValue;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getEncodedOnly() { return encodedOnly; }
    public void setEncodedOnly(long encodedOnly) { this.encodedOnly = encodedOnly; }

    public long getDecodedOnly() { return decodedOnly; }
    public void setDecodedOnly(long decodedOnly) { this.decodedOnly = decodedOnly; }

    public long getBothDirections() { return bothDirections; }
    public void setBothDirections(long bothDirections) { this.bothDirections = bothDirections; }

    public long getPlainValue() { return plainValue; }
    public void setPlainValue(long plainValue) { this.plainValue = plainValue; }
}
