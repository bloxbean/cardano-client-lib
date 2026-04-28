package com.bloxbean.cardano.client.test.vds;

import com.bloxbean.cardano.client.test.ByteArrayWrapper;
import com.bloxbean.cardano.client.test.CardanoArbitraries;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static factory methods for MPF-specific jqwik arbitraries.
 * Provides generators for hash functions, trie key-values, and trie operations.
 */
public final class MpfArbitraries {

    private MpfArbitraries() {}

    /** SHA-256 hash function lambda. */
    public static final HashFunction SHA256 = data -> {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    };

    /** SHA3-256 hash function lambda. */
    public static final HashFunction SHA3_256 = data -> {
        try {
            return MessageDigest.getInstance("SHA3-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    };

    /**
     * Arbitrary hash function: one of Blake2b-256, SHA-256, or SHA3-256.
     */
    public static Arbitrary<HashFunction> hashFunctions() {
        return Arbitraries.of(
                Blake2b256::digest,
                SHA256,
                SHA3_256
        );
    }

    /**
     * Arbitrary alphanumeric key (4–32 chars) as UTF-8 bytes.
     */
    public static Arbitrary<byte[]> alphanumericKey() {
        return Arbitraries.strings()
                .alpha().numeric().ofMinLength(4).ofMaxLength(32)
                .map(s -> s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Arbitrary alphanumeric value (1–64 chars) as UTF-8 bytes.
     */
    public static Arbitrary<byte[]> alphanumericValue() {
        return Arbitraries.strings()
                .alpha().numeric().ofMinLength(1).ofMaxLength(64)
                .map(s -> s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Arbitrary list of key-value pairs for trie operations.
     * Keys: 1–64 bytes, values: 1–256 bytes.
     *
     * @param min minimum number of entries
     * @param max maximum number of entries
     */
    public static Arbitrary<List<Map.Entry<byte[], byte[]>>> trieKeyValues(int min, int max) {
        Arbitrary<byte[]> keys = CardanoArbitraries.bytesRange(1, 64);
        Arbitrary<byte[]> values = CardanoArbitraries.bytesRange(1, 256);
        return Combinators.combine(keys, values)
                .as((k, v) -> (Map.Entry<byte[], byte[]>) new AbstractMap.SimpleEntry<>(k, v))
                .list().ofMinSize(min).ofMaxSize(max);
    }

    /**
     * Arbitrary list of alphanumeric key-value pairs for trie operations.
     * Keys: alphanumeric strings (4–32 chars), values: alphanumeric strings (1–64 chars).
     * Uses string-based keys to avoid CBOR proof encoding edge cases with arbitrary binary data.
     *
     * @param min minimum number of entries
     * @param max maximum number of entries
     */
    public static Arbitrary<List<Map.Entry<byte[], byte[]>>> trieKeyValuesAlphanumeric(int min, int max) {
        return Combinators.combine(alphanumericKey(), alphanumericValue())
                .as((k, v) -> (Map.Entry<byte[], byte[]>) new AbstractMap.SimpleEntry<>(k, v))
                .list().ofMinSize(min).ofMaxSize(max);
    }

    /**
     * Deduplicate a list of key-value entries by key (last-write-wins).
     * Returns a {@link LinkedHashMap} preserving insertion order of the last occurrence.
     *
     * @param entries raw entries with potentially duplicate keys
     * @return deduplicated map from {@link ByteArrayWrapper} keys to byte[] values
     */
    public static Map<ByteArrayWrapper, byte[]> deduplicateEntries(
            List<Map.Entry<byte[], byte[]>> entries) {
        Map<ByteArrayWrapper, byte[]> map = new LinkedHashMap<>();
        for (Map.Entry<byte[], byte[]> e : entries) {
            map.put(new ByteArrayWrapper(e.getKey()), e.getValue());
        }
        return map;
    }

    /**
     * Arbitrary list of trie operations (PUT, DELETE, GET) drawn from a pool of keys.
     *
     * @param keys the pool of keys to draw from
     * @param min  minimum number of operations
     * @param max  maximum number of operations
     */
    public static Arbitrary<List<TrieOperation>> trieOperations(List<byte[]> keys, int min, int max) {
        Arbitrary<TrieOperation.OpType> opTypes = Arbitraries.of(TrieOperation.OpType.values());
        Arbitrary<byte[]> keyArb = Arbitraries.of(keys);
        Arbitrary<byte[]> valueArb = CardanoArbitraries.bytesRange(1, 256);
        return Combinators.combine(opTypes, keyArb, valueArb)
                .as(TrieOperation::new)
                .list().ofMinSize(min).ofMaxSize(max);
    }
}
