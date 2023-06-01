package com.bloxbean.cardano.client.metadata;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;

import java.math.BigInteger;
import java.util.List;

/**
 * Metadata interface which can be used in AuxiliaryData's metadata field
 */
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

    /**
     * Merge two metadata
     * @param metadata1
     * @return merged metadata
     */
    Metadata merge(Metadata metadata1);

    Metadata put(BigInteger key, BigInteger value);

    Metadata putNegative(BigInteger key, BigInteger value);

    Metadata put(BigInteger key, byte[] value);

    Metadata put(BigInteger key, String value);

    Metadata put(BigInteger key, MetadataMap mm);

    Metadata put(BigInteger key, MetadataList list);

    Object get(BigInteger key);

    void remove(BigInteger key);

    List keys();

    default Metadata put(long key, BigInteger value) {
        return put(BigInteger.valueOf(key), value);
    }

    default Metadata putNegative(long key, long value) {
        return putNegative(BigInteger.valueOf(key), BigInteger.valueOf(value));
    }

    default Metadata put(long key, byte[] value) {
        return put(BigInteger.valueOf(key), value);
    }

    default Metadata put(long key, String value) {
        return put(BigInteger.valueOf(key), value);
    }

    default Metadata put(long key, MetadataMap mm) {
        return put(BigInteger.valueOf(key), mm);
    }

    default Metadata put(long key, MetadataList list) {
        return put(BigInteger.valueOf(key), list);
    }
}
