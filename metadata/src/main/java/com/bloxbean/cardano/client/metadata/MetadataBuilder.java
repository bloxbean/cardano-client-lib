package com.bloxbean.cardano.client.metadata;

import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;

/**
 * Builder class to create Metadata, MetadataMap and MetadataList
 */
public class MetadataBuilder {

    /**
     * Create Metadata object
     * @return Metadata
     */
    public static Metadata createMetadata() {
        return new CBORMetadata();
    }

    /**
     * Create MetadataMap object
     * @return MetadataMap
     */
    public static MetadataMap createMap() {
        return new CBORMetadataMap();
    }

    /**
     * Create MetadataList object
     * @return MetadataList
     */
    public static MetadataList createList() {
        return new CBORMetadataList();
    }
}
