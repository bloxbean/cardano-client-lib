package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Integration test POJO covering {@code byte[]} encoding overrides.
 * The annotation processor generates {@code SampleByteEncodingMetadataConverter} from this class.
 *
 * <p>Covered scenarios (per ADR metadata/0004):
 * <ul>
 *   <li>{@code byte[]} DEFAULT   → Cardano bytes (raw binary)</li>
 *   <li>{@code byte[]} STRING_HEX    → lowercase hex string (HexUtil)</li>
 *   <li>{@code byte[]} STRING_BASE64 → Base64 string (java.util.Base64)</li>
 * </ul>
 */
@MetadataType
public class SampleByteEncoding {

    /** Default encoding — stored as raw Cardano bytes. */
    private byte[] rawData;

    /** Hex encoding — stored as lowercase hex text, e.g. {@code "deadbeef"}. */
    @MetadataField(key = "hexData", enc = MetadataFieldType.STRING_HEX)
    private byte[] hexData;

    /** Base64 encoding — stored as Base64 text, e.g. {@code "3q2+7w=="}. */
    @MetadataField(key = "b64Data", enc = MetadataFieldType.STRING_BASE64)
    private byte[] base64Data;

    public byte[] getRawData() { return rawData; }
    public void setRawData(byte[] rawData) { this.rawData = rawData; }

    public byte[] getHexData() { return hexData; }
    public void setHexData(byte[] hexData) { this.hexData = hexData; }

    public byte[] getBase64Data() { return base64Data; }
    public void setBase64Data(byte[] base64Data) { this.base64Data = base64Data; }
}
