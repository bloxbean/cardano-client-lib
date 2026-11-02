package com.bloxbean.cardano.client.common.cbor;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * Deeply nested CBOR at and beyond the maximum transaction size (16,384 bytes), shaped like the three Cardano types that
 * nest without bound: native scripts, Plutus data and metadata (ADR 0001 section 6.7). Each fixture is walked, sliced
 * and navigated to its innermost item.
 * <p>
 * {@link #main(String[])} runs every fixture, so a forked JVM (for example with {@code -Xint}) can run them too.
 */
final class CborSpanDepthFixtures {
    private static final byte[] PUBKEY_SCRIPT = decodeHexString("8200581c" + "00".repeat(28));
    private static final int NAVIGATED_LEVELS = 32;

    private CborSpanDepthFixtures() {
    }

    static Map<String, Runnable> all() {
        Map<String, Runnable> fixtures = new LinkedHashMap<>();
        // [1, [[1, [ ... [0, keyhash] ]]]]: the preprod trigger shape, 3 bytes per level
        fixtures.put("native script (5,410 levels)",
                () -> verify(nested("820181", 5_410, PUBKEY_SCRIPT), 5_410, span -> span.get(1).get(0)));
        // [[[ ... 0 ]]]: a list datum, 1 byte per level
        fixtures.put("definite list (16,250 levels)", () -> verify(nested("81", 16_250, hex("00")), 16_250, span -> span.get(0)));
        fixtures.put("indefinite list (8,000 levels)",
                () -> verify(nestedIndefinite(8_000), 8_000, span -> span.get(0)));
        // 121([ 121([ ... 0 ]) ]): a constr chain, 3 bytes per level
        fixtures.put("constr chain (5,420 levels)",
                () -> verify(nested("d87981", 5_420, hex("00")), 5_420, span -> span.untag().get(0)));
        // {0: {0: ... 0}}: nested metadata maps
        fixtures.put("nested map values (8,000 levels)",
                () -> verify(nested("a100", 8_000, hex("00")), 8_000, span -> span.field(0).orElseThrow()));
        // {[[[ ... 0 ]]]: 0}: a map key nested 16,000 levels
        fixtures.put("deep map key (16,000 levels)", () -> {
            byte[] key = nested("81", 16_000, hex("00"));
            byte[] map = concat(hex("a1"), key, hex("00"));
            CborSpan span = CborSpan.of(map);
            CborSpan keySpan = span.entries().get(0).getKey();
            check(keySpan.length() == key.length, "deep key length");
            verify(keySpan.bytes(), 16_000, s -> s.get(0));
        });
        fixtures.put("tag chain (16,000 tags)", () -> {
            byte[] bytes = nested("c1", 16_000, hex("00"));
            CborSpan span = CborSpan.of(bytes);
            for (int i = 0; i < 16_000; i++)
                span = span.untag();
            check(span.asLong() == 0, "innermost item");
        });
        // No depth limit at all, far beyond any transaction
        fixtures.put("definite list (1,000,000 levels)", () -> verify(nested("81", 1_000_000, hex("00")), 1_000_000, span -> span.get(0)));
        return fixtures;
    }

    public static void main(String[] args) {
        for (Map.Entry<String, Runnable> fixture : all().entrySet()) {
            fixture.getValue().run();
            System.out.println("ok: " + fixture.getKey());
        }
    }

    // Walks the whole fixture once, then navigates the outer levels. Navigation re-walks each child to find its end,
    // so going down every level would be quadratic; the views only navigate shallow CDDL positions.
    private static void verify(byte[] bytes, int levels, UnaryOperator<CborSpan> down) {
        CborSpan span = CborSpan.of(bytes);
        check(span.length() == bytes.length, "length");
        check(CborSpan.skip(bytes, 0, bytes.length) == bytes.length, "skip");
        for (int i = 0; i < Math.min(levels, NAVIGATED_LEVELS); i++) {
            CborSpan child = down.apply(span);
            check(child.buffer() == bytes && child.offset() > span.offset()
                    && child.offset() + child.length() <= span.offset() + span.length(), "child is a slice");
            span = child;
        }
    }

    static byte[] nested(String levelHex, int levels, byte[] innermost) {
        byte[] level = decodeHexString(levelHex);
        ByteArrayOutputStream out = new ByteArrayOutputStream(level.length * levels + innermost.length);
        for (int i = 0; i < levels; i++)
            out.write(level, 0, level.length);
        out.write(innermost, 0, innermost.length);
        return out.toByteArray();
    }

    private static byte[] nestedIndefinite(int levels) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(levels * 2 + 1);
        for (int i = 0; i < levels; i++)
            out.write(0x9f);
        out.write(0x00);
        for (int i = 0; i < levels; i++)
            out.write(0xff);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts)
            out.write(part, 0, part.length);
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
