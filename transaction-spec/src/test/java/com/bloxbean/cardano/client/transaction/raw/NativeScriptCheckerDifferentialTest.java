package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborHead;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.RequireGuard;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAll;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAny;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ADR 0005 D3, J10 and J1 for native scripts: {@link NativeScriptChecker} against CCL's model decoder.
 *
 * <ul>
 *   <li>Conway, both ways: the checker accepts exactly what Yano's check accepted before the move, which was a flat
 *       scan for indefinite byte strings, then CCL's model decoder ({@link RawModels#toScript}), then no guard
 *       (type 6) at any depth. So the move changes no native script's outcome.</li>
 *   <li>Dijkstra, one way (J10): what the checker accepts, the model decodes.</li>
 * </ul>
 * Over generated scripts that cover every type, arity, form and field encoding, and over byte-level mutations of
 * them; the seeds are fixed, so a failure reproduces.
 */
class NativeScriptCheckerDifferentialTest {

    private static final int GENERATED = 20_000;
    private static final int MUTATIONS = 20_000;

    @Test
    void conwayAcceptsExactlyWhatYanosCheckAccepted() {
        Random random = new Random(0x5c21_9a7d_e3b1L);
        List<byte[]> scripts = generate(random);
        int accepted = 0;
        int compared = 0;
        for (byte[] script : withMutations(scripts, random)) {
            CborSpan span = span(script);
            if (span == null) {
                continue;
            }
            boolean before = yanoAccepted(span);
            boolean now = checks(span, Era.Conway, true);
            assertThat(now).as("checker vs Yano's check on %s", HexUtil.encodeHexString(script)).isEqualTo(before);
            compared++;
            accepted += now ? 1 : 0;
        }
        // the corpus exercises both outcomes
        assertThat(compared).isGreaterThan(GENERATED);
        assertThat(accepted).isGreaterThan(compared / 10).isLessThan(compared - compared / 10);
    }

    @Test
    void inDijkstraWhatTheCheckerAcceptsTheModelDecodes() {
        Random random = new Random(0x1d1c_57a0_44e2L);
        int accepted = 0;
        for (byte[] script : withMutations(generate(random), random)) {
            CborSpan span = span(script);
            if (span == null || !checks(span, Era.Dijkstra, false)) {
                continue;
            }
            accepted++;
            assertThat(modelDecodes(span)).as("the model decodes %s", HexUtil.encodeHexString(script)).isTrue();
        }
        assertThat(accepted).isPositive();
    }

    /**
     * J10's places: a native script in a witness set, in auxiliary data (both forms) and in an output's script
     * reference is read by the same checker, so the strict transaction read accepts exactly what the checker accepts,
     * and what it accepts, CCL's transaction model decodes.
     */
    @Test
    void everyPlaceReadsTheScriptAsTheCheckerDoes() throws Exception {
        Random random = new Random(0x7a11_0c3e_9d05L);
        List<byte[]> scripts = withMutations(generate(random), random);
        int accepted = 0;
        for (int i = 0; i < scripts.size(); i += 8) {
            CborSpan span = span(scripts.get(i));
            if (span == null) {
                continue;
            }
            String script = HexUtil.encodeHexString(scripts.get(i));
            boolean checker = checks(span, Era.Conway, true);
            accepted += checker ? 1 : 0;
            for (String tx : places(script)) {
                boolean strict;
                try {
                    HaskellPolicy.CONWAY.transaction(HexUtil.decodeHexString(tx));
                    strict = true;
                } catch (LedgerDecodingException e) {
                    strict = false;
                }
                assertThat(strict).as("the strict read of %s", tx).isEqualTo(checker);
                if (strict) {
                    assertThatCode(() -> Transaction.deserialize(HexUtil.decodeHexString(tx))).as(tx)
                            .doesNotThrowAnyException();
                }
            }
        }
        assertThat(accepted).isPositive();
    }

    private static List<String> places(String script) {
        String body = "a3" + "0081" + "825820" + "00".repeat(32) + "00" + "0180" + "0200";
        String inner = "8200" + script;
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        CborHead.write(head, 2, inner.length() / 2);
        String ref = "d818" + HexUtil.encodeHexString(head.toByteArray()) + inner;
        String output = "a3" + "00" + "581d60" + "00".repeat(28) + "0100" + "03" + ref;
        return List.of(
                "84" + body + "a1" + "0181" + script + "f5f6",
                "84" + body + "a0" + "f5" + "82" + "a0" + "81" + script,
                "84" + body + "a0" + "f5" + "d90103" + "a1" + "0181" + script,
                "84" + "a3" + "0081" + "825820" + "00".repeat(32) + "00" + "0181" + output + "0200" + "a0" + "f5f6");
    }

    @Test
    void erasBeforeAllegraHaveNoTimeLocks() {
        CborSpan after = span(HexUtil.decodeHexString("82041864"));
        assertThat(checks(after, Era.Allegra, true)).isTrue();
        assertThat(checks(after, Era.Shelley, true)).isFalse();
        CborSpan guard = span(HexUtil.decodeHexString("8206" + "8200581c" + "11".repeat(28)));
        assertThat(checks(guard, Era.Conway, true)).isFalse();
        assertThat(checks(guard, Era.Dijkstra, true)).isTrue();
    }

    // ---- the two sides

    private static boolean checks(CborSpan span, Era era, boolean definiteBytes) {
        try {
            NativeScriptChecker.check(span, era, definiteBytes);
            return true;
        } catch (LedgerDecodingException e) {
            return false;
        }
    }

    /** Yano's {@code RawScript.of(NATIVE, span)} and {@code requireConwayTypes()} at {@code de892d95}. */
    private static boolean yanoAccepted(CborSpan span) {
        if (hasIndefiniteByteString(span)) {
            return false;
        }
        NativeScript script = model(span);
        return script != null && !holdsAGuard(script);
    }

    private static boolean modelDecodes(CborSpan span) {
        return model(span) != null;
    }

    private static NativeScript model(CborSpan span) {
        try {
            return (NativeScript) RawModels.toScript(new RawScript(0, span));
        } catch (CborDeserializationException | CborRuntimeException e) {
            return null;
        }
    }

    /** Yano's {@code requireDefiniteByteStrings}: a flat pass over every head. */
    private static boolean hasIndefiniteByteString(CborSpan span) {
        byte[] b = span.buffer();
        int pos = span.offset();
        int end = pos + span.length();
        while (pos < end) {
            if ((b[pos] & 0xff) == 0xff) {
                pos++;
                continue;
            }
            CborHead head = CborHead.at(b, pos, end);
            if (head.major() == 2 && head.isIndefinite()) {
                return true;
            }
            pos = (head.major() == 2 || head.major() == 3) && !head.isIndefinite() ? head.stringEnd(end) : head.end();
        }
        return false;
    }

    private static boolean holdsAGuard(NativeScript root) {
        Deque<NativeScript> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            NativeScript next = pending.pop();
            if (next instanceof RequireGuard) {
                return true;
            }
            if (next instanceof ScriptAll) {
                ((ScriptAll) next).getScripts().forEach(pending::push);
            } else if (next instanceof ScriptAny) {
                ((ScriptAny) next).getScripts().forEach(pending::push);
            } else if (next instanceof ScriptAtLeast) {
                ((ScriptAtLeast) next).getScripts().forEach(pending::push);
            }
        }
        return false;
    }

    private static CborSpan span(byte[] bytes) {
        try {
            return CborSpan.of(bytes);
        } catch (CborRuntimeException e) {
            return null;
        }
    }

    // ---- inputs

    private static List<byte[]> generate(Random random) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < GENERATED; i++) {
            ByteArrayOutputStream script = new ByteArrayOutputStream();
            script(script, random, 0);
            out.add(script.toByteArray());
        }
        return out;
    }

    private static List<byte[]> withMutations(List<byte[]> scripts, Random random) {
        List<byte[]> out = new ArrayList<>(scripts);
        for (int i = 0; i < MUTATIONS; i++) {
            byte[] source = scripts.get(random.nextInt(scripts.size()));
            out.add(mutate(source, random));
        }
        return out;
    }

    private static byte[] mutate(byte[] source, Random random) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int at = random.nextInt(source.length);
        switch (random.nextInt(4)) {
            case 0: // replace a byte
                out.write(source, 0, source.length);
                byte[] replaced = out.toByteArray();
                replaced[at] = (byte) random.nextInt(256);
                return replaced;
            case 1: // flip a bit of a head-like byte
                out.write(source, 0, source.length);
                byte[] flipped = out.toByteArray();
                flipped[at] ^= (byte) (1 << random.nextInt(8));
                return flipped;
            case 2: // insert a byte
                out.write(source, 0, at);
                out.write(random.nextInt(256));
                out.write(source, at, source.length - at);
                return out.toByteArray();
            default: // delete a byte, keeping the rest
                out.write(source, 0, at);
                out.write(source, at + 1, source.length - at - 1);
                return out.toByteArray();
        }
    }

    /** A native script, usually well formed for Conway, sometimes not, in every encoding the decoders treat apart. */
    private static void script(ByteArrayOutputStream out, Random random, int depth) {
        if (random.nextInt(40) == 0) {
            maybeTag(out, random, 100);
            out.write(random.nextInt(2) == 0 ? 0xa0 : 0x40); // not an array
            return;
        }
        int type = random.nextInt(30) == 0 ? 7 : random.nextInt(depth > 3 ? 1 : 7);
        if (depth > 3 && random.nextBoolean()) {
            type = random.nextBoolean() ? 4 : 5;
        }
        int arity = type == 3 ? 3 : 2;
        int items = random.nextInt(25) == 0 ? arity + (random.nextBoolean() ? 1 : -1) : arity;
        boolean indefinite = random.nextInt(6) == 0;
        maybeTag(out, random, 60);
        if (items <= 0) {
            out.write(indefinite ? 0x9f : 0x80);
            if (indefinite) {
                out.write(0xff);
            }
            return;
        }
        arrayHead(out, items, indefinite, random);
        integer(out, type, random);
        int written = 1;
        switch (type) {
            case 0:
                keyHash(out, random);
                written++;
                break;
            case 1:
            case 2:
                list(out, random, depth);
                written++;
                break;
            case 3:
                m(out, random);
                list(out, random, depth);
                written += 2;
                break;
            case 4:
            case 5:
                slot(out, random);
                written++;
                break;
            default:
                credential(out, random);
                written++;
        }
        for (; written < items; written++) {
            out.write(0x00);
        }
        if (indefinite) {
            out.write(0xff);
        }
    }

    private static void list(ByteArrayOutputStream out, Random random, int depth) {
        if (random.nextInt(40) == 0) {
            maybeTag(out, random, 1);
            out.write(0xa0); // a map, not a list
            return;
        }
        int n = random.nextInt(depth > 2 ? 2 : 4);
        boolean indefinite = random.nextInt(5) == 0;
        maybeTag(out, random, 50);
        arrayHead(out, n, indefinite, random);
        for (int i = 0; i < n; i++) {
            script(out, random, depth + 1);
        }
        if (indefinite) {
            out.write(0xff);
        }
    }

    private static void keyHash(ByteArrayOutputStream out, Random random) {
        int length = random.nextInt(20) == 0 ? 27 + random.nextInt(3) : 28;
        maybeTag(out, random, 50);
        if (random.nextInt(12) == 0) { // chunked
            out.write(0x5f);
            int first = random.nextInt(length + 1);
            bytes(out, first, random);
            bytes(out, length - first, random);
            out.write(0xff);
        } else {
            bytes(out, length, random);
        }
    }

    private static void credential(ByteArrayOutputStream out, Random random) {
        if (random.nextInt(10) == 0) {
            keyHash(out, random); // not [type, hash]
            return;
        }
        boolean indefinite = random.nextInt(6) == 0;
        arrayHead(out, 2, indefinite, random);
        integer(out, random.nextInt(10) == 0 ? 2 : random.nextInt(2), random);
        keyHash(out, random);
        if (indefinite) {
            out.write(0xff);
        }
    }

    private static void m(ByteArrayOutputStream out, Random random) {
        switch (random.nextInt(8)) {
            case 0:
                out.write(0x20 + random.nextInt(3)); // -1 to -3
                break;
            case 1:
                head(out, 1, Long.MAX_VALUE); // -2^63
                break;
            case 2:
                head(out, 1, Long.MIN_VALUE); // -1 - 2^63: out of Int64
                break;
            case 3:
                head(out, 0, Long.MIN_VALUE); // 2^63: out of Int64
                break;
            case 4:
                out.write(0xc2); // a bignum
                bytes(out, 1, random);
                break;
            default:
                integer(out, random.nextInt(5), random);
        }
    }

    private static void slot(ByteArrayOutputStream out, Random random) {
        switch (random.nextInt(8)) {
            case 0:
                out.write(0x20); // -1
                break;
            case 1:
                head(out, 0, -1L); // 2^64 - 1
                break;
            case 2:
                out.write(0xc2); // a bignum
                bytes(out, 2, random);
                break;
            case 3:
                maybeTag(out, random, 1);
                integer(out, 100, random);
                break;
            default:
                integer(out, random.nextInt(1_000_000), random);
        }
    }

    /** An unsigned integer, sometimes with a longer head than needed (cborg reads any width). */
    private static void integer(ByteArrayOutputStream out, long value, Random random) {
        if (random.nextInt(10) == 0) {
            out.write(0x1b);
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) (value >>> shift));
            }
        } else {
            head(out, 0, value);
        }
    }

    private static void arrayHead(ByteArrayOutputStream out, int n, boolean indefinite, Random random) {
        if (indefinite) {
            out.write(0x9f);
        } else if (random.nextInt(15) == 0) {
            out.write(0x98);
            out.write(n);
        } else {
            head(out, 4, n);
        }
    }

    private static void bytes(ByteArrayOutputStream out, int n, Random random) {
        head(out, 2, n);
        for (int i = 0; i < n; i++) {
            out.write(random.nextInt(256));
        }
    }

    private static void maybeTag(ByteArrayOutputStream out, Random random, int oneIn) {
        if (random.nextInt(oneIn) == 0) {
            out.write(0xd8);
            out.write(random.nextBoolean() ? 24 : 0x79);
        }
    }

    private static void head(ByteArrayOutputStream out, int major, long argument) {
        CborHead.write(out, major, argument);
    }
}
