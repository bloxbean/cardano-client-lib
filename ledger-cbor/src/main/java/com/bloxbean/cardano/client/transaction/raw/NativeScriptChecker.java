package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborHead;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.util.Arrays;

/**
 * Whether Haskell's native script decoder reads a script, era by era (ADR 0005 D3). It checks; it builds nothing.
 *
 * <p>A script is an untagged array {@code [type, fields]}, definite or indefinite ({@code decodeRecordSum}), with
 * exactly its type's fields: {@code [0, keyhash]}, {@code [1, [scripts]]}, {@code [2, [scripts]]},
 * {@code [3, m, [scripts]]}, {@code [4, slot]}, {@code [5, slot]} ({@code TimelockRaw}, Allegra/Scripts.hs:235-245 at
 * cardano-ledger {@code f649f975}; Shelley's multisig has types 0 to 3), and from Dijkstra {@code [6, credential]},
 * the guard. A key hash is an untagged 28-byte byte string, definite below decoder version 12 ({@code decodeBytes});
 * {@code m} an untagged integer within {@code Int} (64 bits); a slot an untagged {@code Word64}; a list of scripts an
 * untagged array, definite or indefinite. An unknown type, or one of a later era, is refused: in Conway that refuses a
 * guard at any depth.</p>
 *
 * <p>This is the one era-aware statement of native-script structure. CCL's model decoder, {@code NativeScriptCodec},
 * stays lenient and reads type 6 in any era (ADR 0004); the two agree one way: what this checker accepts, the model
 * decodes (J10). One pass over the heads, with an explicit stack, so a script of any nesting depth is checked in
 * linear time on any thread. Every rejection is a {@link LedgerDecodingException}: {@link Reason#SCRIPT}, or
 * {@link Reason#INDEFINITE_STRING} for a chunked key hash below version 12.</p>
 */
final class NativeScriptChecker {

    private static final int MAJOR_UNSIGNED = 0;
    private static final int MAJOR_NEGATIVE = 1;
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_ARRAY = 4;
    private static final int KEY_HASH_LENGTH = 28;
    private static final int TYPE_AT_LEAST = 3;
    private static final int TYPE_GUARD = 6;

    /** A frame: the scripts of a list still to read. */
    private static final int LIST = 0;
    /** A frame: a script whose list is being read, which must end after it. */
    private static final int CLOSE = 1;

    private final HeadCursor c;
    private final int maxType;
    private final boolean definiteBytes;
    private int[] kind = new int[16];
    private long[] count = new long[16];
    private int depth;

    private NativeScriptChecker(CborSpan script, Era era, boolean definiteBytes) {
        this.c = new HeadCursor(script.buffer(), script.offset(), script.offset() + script.length(), Reason.SCRIPT);
        this.maxType = maxType(era);
        this.definiteBytes = definiteBytes;
    }

    /**
     * @param script        one native script, as encoded
     * @param era           the era whose script types are read
     * @param definiteBytes whether a key hash must be a definite-length byte string (decoder versions below 12)
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    static void check(CborSpan script, Era era, boolean definiteBytes) {
        new NativeScriptChecker(script, era, definiteBytes).run();
    }

    private static int maxType(Era era) {
        if (era.compareTo(Era.Dijkstra) >= 0) {
            return TYPE_GUARD;
        }
        return era.compareTo(Era.Allegra) >= 0 ? 5 : TYPE_AT_LEAST;
    }

    private void run() {
        script();
        while (depth > 0) {
            int top = depth - 1;
            if (kind[top] == CLOSE) {
                depth--;
                close(count[top] == HeadCursor.INDEFINITE);
            } else if (count[top] == HeadCursor.INDEFINITE ? c.atBreak() : count[top] == 0) {
                depth--;
            } else {
                if (count[top] != HeadCursor.INDEFINITE) {
                    count[top]--;
                }
                script();
            }
        }
        if (!c.atEnd()) {
            throw c.reject("bytes after a native script at " + c.position());
        }
    }

    /** Reads one script's head, type and fields; a script with a list leaves frames to read it. */
    private void script() {
        int at = c.position();
        CborHead head = c.next();
        if (head.major() != MAJOR_ARRAY) {
            throw c.reject("a native script at " + at + " is not an untagged array");
        }
        long length = head.isIndefinite() ? HeadCursor.INDEFINITE : head.argument();
        if (length == 0 || (length == HeadCursor.INDEFINITE && c.peek() == 0xff)) {
            throw c.reject("an empty native script at " + at);
        }
        CborHead typeHead = c.next();
        long type = typeHead.argument();
        if (typeHead.major() != MAJOR_UNSIGNED || type < 0 || type > TYPE_GUARD) {
            throw c.reject("unknown native script type at " + at);
        }
        if (type > maxType) {
            throw c.reject(type == TYPE_GUARD
                    ? "native script: type 6, a guard, is not a native script before Dijkstra (at " + at + ")"
                    : "native script type " + type + " is not in this era (at " + at + ")");
        }
        int arity = type == TYPE_AT_LEAST ? 3 : 2;
        if (length != HeadCursor.INDEFINITE && length != arity) {
            throw c.reject("native script type " + type + " has " + arity + " items, found " + length + " at " + at);
        }
        boolean indefinite = length == HeadCursor.INDEFINITE;
        switch ((int) type) {
            case 0:
                keyHash();
                close(indefinite);
                break;
            case 1:
            case 2:
                list(indefinite);
                break;
            case TYPE_AT_LEAST:
                int64();
                list(indefinite);
                break;
            case 4:
            case 5:
                slot();
                close(indefinite);
                break;
            default:
                credential();
                close(indefinite);
        }
    }

    /** Opens a list of scripts, after which the script that holds it must end. */
    private void list(boolean scriptIndefinite) {
        int at = c.position();
        CborHead head = c.next();
        if (head.major() != MAJOR_ARRAY) {
            throw c.reject("a native script list at " + at + " is not an untagged array");
        }
        push(CLOSE, scriptIndefinite ? HeadCursor.INDEFINITE : 0);
        push(LIST, head.isIndefinite() ? HeadCursor.INDEFINITE : head.argument());
    }

    /** A script's array ends: an indefinite one with its break, a definite one has had its items (checked first). */
    private void close(boolean indefinite) {
        if (indefinite && !c.atBreak()) {
            throw c.reject("a native script at " + c.position() + " has more items than its type");
        }
    }

    private void keyHash() {
        bytes(KEY_HASH_LENGTH, "a key hash");
    }

    /** {@code [0, keyhash]} or {@code [1, scripthash]}, as {@code decodeRecordSum} reads a credential. */
    private void credential() {
        int at = c.position();
        CborHead head = c.next();
        if (head.major() != MAJOR_ARRAY || (!head.isIndefinite() && head.argument() != 2)) {
            throw c.reject("a guard's credential at " + at + " is not [type, hash]");
        }
        CborHead type = c.next();
        if (type.major() != MAJOR_UNSIGNED || type.argument() < 0 || type.argument() > 1) {
            throw c.reject("a guard's credential at " + at + " has an unknown type");
        }
        bytes(KEY_HASH_LENGTH, "a credential hash");
        close(head.isIndefinite());
    }

    private void bytes(int expected, String what) {
        int at = c.position();
        CborHead head = c.next();
        if (head.major() != MAJOR_BYTES) {
            throw c.reject(what + " at " + at + " is not an untagged byte string");
        }
        long found;
        if (head.isIndefinite()) {
            if (definiteBytes) {
                throw new LedgerDecodingException(Reason.INDEFINITE_STRING, "an indefinite-length byte string in a "
                        + "native script at " + at + " (definite only below decoder version 12)");
            }
            found = 0;
            while (!c.atBreak()) {
                found += c.definiteString(MAJOR_BYTES).length;
            }
        } else {
            c.skipString(head);
            found = head.argument();
        }
        if (found != expected) {
            throw c.reject(what + " at " + at + " has " + found + " bytes, expected " + expected);
        }
    }

    /** {@code m} of {@code TimelockMOf}: an {@code Int}, a major-0 or major-1 integer within 64 bits, signed. */
    private void int64() {
        int at = c.position();
        CborHead head = c.next();
        boolean integer = head.major() == MAJOR_UNSIGNED || head.major() == MAJOR_NEGATIVE;
        // the argument as a signed long is non-negative exactly when the value fits Int64
        if (!integer || head.argument() < 0) {
            throw c.reject("the m of an at-least script at " + at + " is not a 64-bit integer");
        }
    }

    /** A slot: an untagged {@code Word64}. */
    private void slot() {
        int at = c.position();
        if (c.next().major() != MAJOR_UNSIGNED) {
            throw c.reject("a time lock's slot at " + at + " is not an unsigned integer");
        }
    }

    private void push(int frameKind, long frameCount) {
        if (depth == kind.length) {
            kind = Arrays.copyOf(kind, depth * 2);
            count = Arrays.copyOf(count, depth * 2);
        }
        kind[depth] = frameKind;
        count[depth] = frameCount;
        depth++;
    }
}
