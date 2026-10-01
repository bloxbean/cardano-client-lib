package com.bloxbean.cardano.client.metadata.cbor;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * Metadata nested as deep as a transaction allows (ADR 0001 section 6.7) and beyond: nested lists, nested maps, a deep
 * map key and an indefinite chain under label 0. Each decodes, serializes back to the same bytes and hashes to the hash
 * of those bytes. {@link #main(String[])} runs them all, for a forked JVM.
 */
final class MetadataDepthFixtures {
    private MetadataDepthFixtures() {
    }

    static Map<String, byte[]> all() {
        Map<String, byte[]> fixtures = new LinkedHashMap<>();
        fixtures.put("nested lists (16,250 levels)", label0("81".repeat(16_250) + "00"));
        fixtures.put("nested maps (8,000 levels)", label0("a100".repeat(8_000) + "00"));
        fixtures.put("deep map key (16,000 levels)", label0("a1" + "81".repeat(16_000) + "00" + "00"));
        fixtures.put("indefinite lists (8,000 levels)", label0("9f".repeat(8_000) + "00" + "ff".repeat(8_000)));
        fixtures.put("nested lists (100,000 levels)", label0("81".repeat(100_000) + "00"));
        return fixtures;
    }

    static void verify(String name, byte[] bytes) {
        CBORMetadata metadata = CBORMetadata.deserialize(bytes);
        check(Arrays.equals(metadata.serialize(), bytes), name + ": round trip");
        check(Arrays.equals(metadata.getMetadataHash(), Blake2bUtil.blake2bHash256(bytes)), name + ": metadata hash");
        Object value = metadata.get(BigInteger.ZERO);
        check(value instanceof CBORMetadataList || value instanceof CBORMetadataMap, name + ": label 0");
    }

    public static void main(String[] args) {
        for (Map.Entry<String, byte[]> fixture : all().entrySet()) {
            verify(fixture.getKey(), fixture.getValue());
            System.out.println("ok: " + fixture.getKey());
        }
    }

    private static byte[] label0(String metadatumHex) {
        return decodeHexString("a100" + metadatumHex);
    }

    private static void check(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("depth fixture check failed: " + what);
    }
}
