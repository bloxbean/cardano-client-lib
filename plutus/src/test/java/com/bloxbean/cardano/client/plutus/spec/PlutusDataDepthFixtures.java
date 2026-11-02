package com.bloxbean.cardano.client.plutus.spec;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * Plutus data nested as deep as a transaction allows (ADR 0001 section 6.7) and beyond: each decodes, serializes back to
 * the same bytes, hashes to the hash of those bytes, and equals and hashes like a second decode.
 * {@link #main(String[])} runs them all, for a forked JVM.
 */
final class PlutusDataDepthFixtures {
    private PlutusDataDepthFixtures() {
    }

    static Map<String, byte[]> all() {
        Map<String, byte[]> fixtures = new LinkedHashMap<>();
        fixtures.put("list datum (16,250 levels)", hex("81".repeat(16_250) + "00"));
        fixtures.put("indefinite list (8,000 levels)", hex("9f".repeat(8_000) + "00" + "ff".repeat(8_000)));
        fixtures.put("constr chain (5,420 levels)", hex("d87981".repeat(5_420) + "00"));
        fixtures.put("general-form constr chain (3,000 levels)", hex("d86682188081".repeat(3_000) + "00"));
        fixtures.put("nested map values (8,000 levels)", hex("a100".repeat(8_000) + "00"));
        fixtures.put("map key chain (5,000 levels)", hex("a1".repeat(5_000) + "00" + "00".repeat(5_000)));
        fixtures.put("two-entry map key chain (3,000 levels)", hex("a20101".repeat(3_000) + "02" + "00".repeat(3_000)));
        fixtures.put("list datum (100,000 levels)", hex("81".repeat(100_000) + "00"));
        return fixtures;
    }

    static void verify(String name, byte[] bytes) {
        try {
            PlutusData data = PlutusData.deserialize(bytes);
            check(Arrays.equals(data.serializeToBytes(), bytes), name + ": round trip");
            check(Arrays.equals(data.getDatumHashAsBytes(), Blake2bUtil.blake2bHash256(bytes)), name + ": datum hash");
            PlutusData again = PlutusData.deserialize(bytes);
            check(data.equals(again) && data.hashCode() == again.hashCode(), name + ": equals and hashCode");
        } catch (Exception e) {
            throw new AssertionError(name, e);
        }
    }

    public static void main(String[] args) {
        for (Map.Entry<String, byte[]> fixture : all().entrySet()) {
            verify(fixture.getKey(), fixture.getValue());
            System.out.println("ok: " + fixture.getKey());
        }
    }

    private static byte[] hex(String hex) {
        return decodeHexString(hex);
    }

    private static void check(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("depth fixture check failed: " + what);
    }
}
