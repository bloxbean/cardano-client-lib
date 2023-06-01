package com.bloxbean.cardano.client.metadata;

import co.nstant.in.cbor.model.Array;

import java.math.BigInteger;

/**
 * Interface for list type metadata
 */
public interface MetadataList {
    MetadataList add(BigInteger value);

    MetadataList addNegative(BigInteger value);

    MetadataList add(String value);

    MetadataList addAll(String[] value);

    MetadataList add(byte[] value);

    MetadataList add(MetadataMap map);

    MetadataList add(MetadataList list);

    void replaceAt(int index, BigInteger value);

    void replaceAt(int index, String value);

    void replaceAt(int index, byte[] value);

    void replaceAt(int index, MetadataMap map);

    void replaceAt(int index, MetadataList list);

    void removeItem(Object value);

    void removeItemAt(int index);

    Object getValueAt(int index);

    int size();

    Array getArray();

    String toJson();
}
