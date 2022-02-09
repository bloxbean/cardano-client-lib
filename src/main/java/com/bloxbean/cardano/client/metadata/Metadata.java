package com.bloxbean.cardano.client.metadata;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;

public interface Metadata {

    /**
     * Cbor map of metadata
     * @return Cbor map
     * @throws MetadataSerializationException
     */
    Map getData() throws MetadataSerializationException;

    /**
     * Get metadata hash
     * @return
     * @throws MetadataSerializationException
     */
    byte[] getMetadataHash() throws MetadataSerializationException;

    /**
     * Serialize to cbor bytes
     * @return
     * @throws MetadataSerializationException
     */
    byte[] serialize() throws MetadataSerializationException;

    Metadata merge(Metadata metadata1);
}
