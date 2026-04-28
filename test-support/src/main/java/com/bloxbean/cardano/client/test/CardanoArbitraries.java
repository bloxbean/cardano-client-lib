package com.bloxbean.cardano.client.test;

import com.bloxbean.cardano.client.util.HexUtil;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static factory methods for Cardano-related jqwik arbitraries.
 * Provides generators for common Cardano data types used in property-based testing.
 */
public final class CardanoArbitraries {

    private CardanoArbitraries() {}

    /**
     * Arbitrary byte arrays of exact size.
     */
    public static Arbitrary<byte[]> bytes(int size) {
        return Arbitraries.bytes().array(byte[].class).ofSize(size);
    }

    /**
     * Arbitrary byte arrays with size in [minSize, maxSize].
     */
    public static Arbitrary<byte[]> bytesRange(int minSize, int maxSize) {
        return Arbitraries.integers().between(minSize, maxSize)
                .flatMap(CardanoArbitraries::bytes);
    }

    /**
     * Arbitrary 32-byte hash digests.
     */
    public static Arbitrary<byte[]> hashes() {
        return bytes(32);
    }

    /**
     * Arbitrary 28-byte policy IDs.
     */
    public static Arbitrary<byte[]> policyIds() {
        return bytes(28);
    }

    /**
     * Arbitrary asset names (0–32 bytes).
     */
    public static Arbitrary<byte[]> assetNames() {
        return bytesRange(0, 32);
    }

    /**
     * Arbitrary lovelace amounts (0 to 45 quadrillion).
     */
    public static Arbitrary<BigInteger> lovelaceAmounts() {
        return Arbitraries.bigIntegers().between(
                BigInteger.ZERO,
                BigInteger.valueOf(45_000_000_000_000_000L)
        );
    }

    /**
     * Arbitrary hex strings of given byte length.
     */
    public static Arbitrary<String> hexStrings(int byteLen) {
        return bytes(byteLen).map(HexUtil::encodeHexString);
    }

    /**
     * Arbitrary key-value pair with key size 1–64 bytes and value size 1–256 bytes.
     */
    public static Arbitrary<Map.Entry<byte[], byte[]>> keyValues() {
        Arbitrary<byte[]> keys = bytesRange(1, 64);
        Arbitrary<byte[]> values = bytesRange(1, 256);
        return Combinators.combine(keys, values)
                .as(AbstractMap.SimpleEntry::new);
    }

    /**
     * Arbitrary map of byte array keys to byte array values.
     * Uses {@link ByteArrayWrapper} for proper key equality.
     *
     * @param min minimum number of entries
     * @param max maximum number of entries
     */
    public static Arbitrary<Map<ByteArrayWrapper, byte[]>> keyValueMaps(int min, int max) {
        return keyValues()
                .list().ofMinSize(min).ofMaxSize(max)
                .map(list -> {
                    Map<ByteArrayWrapper, byte[]> map = new LinkedHashMap<>();
                    for (Map.Entry<byte[], byte[]> e : list) {
                        map.put(new ByteArrayWrapper(e.getKey()), e.getValue());
                    }
                    return map;
                });
    }
}
