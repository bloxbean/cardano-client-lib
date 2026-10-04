package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;

import java.util.Arrays;
import java.util.LinkedHashMap;

import static com.bloxbean.cardano.client.common.cbor.CborSpanDepthFixtures.nested;
import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * The shapes of {@link CborSpanDepthFixtures} through the {@link CborSerializationUtil} codec: each decodes to a
 * {@link DataItem} tree and encodes back to the same bytes (the fixtures are canonical, or indefinite, which the tree
 * keeps). The real preprod trigger transaction and its block (a 5,383-level native script) decode too: the transaction
 * re-encodes to the same bytes; the block has a redeemer with a chunked byte string, which the tree joins as cbor-java
 * does, so its re-encoding is checked to be stable instead. {@link #main(String[])} runs them all, for a forked JVM.
 */
final class DataItemCodecDepthFixtures {
    private static final byte[] PUBKEY_SCRIPT = decodeHexString("8200581c" + "00".repeat(28));

    private DataItemCodecDepthFixtures() {
    }

    static java.util.Map<String, byte[]> all() {
        java.util.Map<String, byte[]> fixtures = new LinkedHashMap<>();
        fixtures.put("native script (5,410 levels)", nested("820181", 5_410, PUBKEY_SCRIPT));
        fixtures.put("definite list (16,250 levels)", nested("81", 16_250, hex("00")));
        fixtures.put("indefinite list (8,000 levels)", indefinite(8_000));
        fixtures.put("constr chain (5,420 levels)", nested("d87981", 5_420, hex("00")));
        fixtures.put("nested map values (8,000 levels)", nested("a100", 8_000, hex("00")));
        fixtures.put("deep map key (16,000 levels)", concat(hex("a1"), nested("81", 16_000, hex("00")), hex("00")));
        fixtures.put("tag chain (16,000 tags)", nested("c1", 16_000, hex("00")));
        // maps whose key is the next map: every level's key is a whole subtree (hashing must stay linear per key)
        fixtures.put("map key chain (5,000 levels)", hex("a1".repeat(5_000) + "00" + "00".repeat(5_000)));
        // the same with a second entry per level, so canonical encoding sorts at every level: {1: 1, <next>: 0}
        fixtures.put("two-entry map key chain (3,000 levels)", hex("a20101".repeat(3_000) + "02" + "00".repeat(3_000)));
        fixtures.put("definite list (200,000 levels)", nested("81", 200_000, hex("00")));
        return fixtures;
    }

    static void verify(String name, byte[] bytes) {
        DataItem item = CborSerializationUtil.deserialize(bytes);
        try {
            check(Arrays.equals(CborSerializationUtil.serialize(item), bytes), name + ": canonical round trip");
            check(Arrays.equals(CborSerializationUtil.serialize(item, false), bytes), name + ": round trip");
        } catch (CborException e) {
            throw new AssertionError(name, e);
        }
        if (item instanceof Map) {
            Map map = (Map) item;
            DataItem key = map.getKeys().iterator().next();
            // look the deep key up with a separately decoded copy
            byte[] keyBytes = CborSpan.of(bytes).entries().get(0).getKey().bytes();
            check(map.get(CborSerializationUtil.deserialize(keyBytes)) == map.get(key), name + ": key lookup");
        }
    }

    /**
     * @return the real corpus items too deep for the recursive code: the preprod trigger transaction and its block
     */
    static java.util.Map<String, byte[]> real() {
        java.util.Map<String, byte[]> fixtures = new LinkedHashMap<>();
        for (RealCborCorpus.Item item : RealCborCorpus.all())
            if (RealCborCorpus.tooDeepForRecursiveCode(item.cbor()))
                fixtures.put(item.name(), item.cbor());
        check(fixtures.size() == 2, "the trigger transaction and block are in the corpus");
        return fixtures;
    }

    // The encoding of the decoded tree, not sorted, is the original bytes, unless they hold chunked strings, which the
    // tree joins; then encoding is stable: decoding and encoding the encoding gives it back.
    static void verifyReal(String name, byte[] bytes) {
        try {
            byte[] encoded = encode(bytes);
            if (RealCborCorpus.hasChunkedStrings(bytes))
                check(Arrays.equals(encode(encoded), encoded), name + ": stable re-encoding");
            else
                check(Arrays.equals(encoded, bytes), name + ": round trip");
        } catch (CborException e) {
            throw new AssertionError(name, e);
        }
    }

    private static byte[] encode(byte[] bytes) throws CborException {
        return CborSerializationUtil.serialize(CborSerializationUtil.deserializeAll(bytes).toArray(new DataItem[0]), false);
    }

    public static void main(String[] args) {
        for (java.util.Map.Entry<String, byte[]> fixture : all().entrySet()) {
            verify(fixture.getKey(), fixture.getValue());
            System.out.println("ok: " + fixture.getKey());
        }
        for (java.util.Map.Entry<String, byte[]> fixture : real().entrySet()) {
            verifyReal(fixture.getKey(), fixture.getValue());
            System.out.println("ok: " + fixture.getKey());
        }
    }

    private static byte[] indefinite(int levels) {
        byte[] bytes = new byte[2 * levels + 1];
        Arrays.fill(bytes, 0, levels, (byte) 0x9f);
        Arrays.fill(bytes, levels + 1, bytes.length, (byte) 0xff);
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] part : parts)
            out.writeBytes(part);
        return out.toByteArray();
    }

    private static byte[] hex(String hex) {
        return decodeHexString(hex);
    }

    private static void check(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("depth fixture check failed: " + what);
    }
}
