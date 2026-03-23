package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;
import java.util.Set;

@MetadataType
public class SampleByteCollectionEncoding {
    @MetadataField(enc = MetadataFieldType.STRING_HEX)
    private List<byte[]> hexHashes;

    @MetadataField(enc = MetadataFieldType.STRING_BASE64)
    private List<byte[]> base64Blobs;

    // DEFAULT encoding (raw bytes) — already works, included for completeness
    private List<byte[]> rawPayloads;

    public List<byte[]> getHexHashes() { return hexHashes; }
    public void setHexHashes(List<byte[]> hexHashes) { this.hexHashes = hexHashes; }

    public List<byte[]> getBase64Blobs() { return base64Blobs; }
    public void setBase64Blobs(List<byte[]> base64Blobs) { this.base64Blobs = base64Blobs; }

    public List<byte[]> getRawPayloads() { return rawPayloads; }
    public void setRawPayloads(List<byte[]> rawPayloads) { this.rawPayloads = rawPayloads; }
}
