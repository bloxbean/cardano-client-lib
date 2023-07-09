package com.bloxbean.cardano.client.metadata;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Map;

import java.math.BigInteger;
import java.util.List;

/**
 * Interface for map type metadata
 */
public interface MetadataMap {
    MetadataMap put(BigInteger key, BigInteger value);

    MetadataMap putNegative(BigInteger key, BigInteger value);

    MetadataMap put(BigInteger key, byte[] value);

    MetadataMap put(BigInteger key, String value);

    MetadataMap put(BigInteger key, MetadataMap value);

    MetadataMap put(BigInteger key, MetadataList list);

    MetadataMap put(String key, BigInteger value);

    MetadataMap putNegative(String key, BigInteger value);

    MetadataMap put(String key, byte[] value);

    MetadataMap put(String key, String value);

    MetadataMap put(String key, MetadataMap value);

    MetadataMap put(String key, MetadataList list);

    MetadataMap put(byte[] key, BigInteger value);

    MetadataMap putNegative(byte[] key, BigInteger value);

    MetadataMap put(byte[] key, byte[] value);

    MetadataMap put(byte[] key, String value);

    MetadataMap put(byte[] key, MetadataMap value);

    MetadataMap put(byte[] key, MetadataList list);

    Object get(String key);

    Object get(BigInteger key);

    Object get(byte[] key);

    void remove(String key);

    void remove(BigInteger key);

    void remove(byte[] key);

    List keys();

    Map getMap();

    String toJson() throws CborException;
}
