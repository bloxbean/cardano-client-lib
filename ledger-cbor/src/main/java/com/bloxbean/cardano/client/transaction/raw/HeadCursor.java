package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborHead;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.util.Arrays;

/**
 * A forward walk over CBOR heads, for the strict validators whose rules depend on the encoding itself and that must run
 * in linear time over input of any nesting depth: Plutus {@code Data} (chunk sizes, the one-byte bignum heads),
 * metadata (text chunks) and native scripts. {@code CborSpan} cannot serve them: enumerating a span's children walks
 * each child again, which is quadratic in the depth. Heads are read by {@link CborHead#at}, the one reader of the RFC
 * 8949 head rules; nesting is tracked on an explicit stack ({@link Containers}), never by recursion.
 *
 * <p>A head that is not well formed, or input that ends early, is a {@link LedgerDecodingException} with the reason
 * the validator gives.</p>
 */
final class HeadCursor {

    /** A container length meaning "indefinite, terminated by a break". */
    static final long INDEFINITE = -1;

    private static final int BREAK = 0xff;

    private final byte[] data;
    private final int end;
    private final Reason reason;
    private int pos;

    /**
     * @param reason the reason for a rejection of the bytes' form: the validator's own
     */
    HeadCursor(byte[] data, int start, int end, Reason reason) {
        this.data = data;
        this.pos = start;
        this.end = end;
        this.reason = reason;
    }

    boolean atEnd() {
        return pos >= end;
    }

    int position() {
        return pos;
    }

    /** @return the next head's initial byte, not consumed */
    int peek() {
        if (pos >= end) {
            throw reject("unexpected end of CBOR at " + pos);
        }
        return data[pos] & 0xff;
    }

    /** Consumes a break and returns true, or returns false when the next byte is not a break. */
    boolean atBreak() {
        if (peek() == BREAK) {
            pos++;
            return true;
        }
        return false;
    }

    /** @return the next head, consumed (a string's content, if any, follows it) */
    CborHead next() {
        try {
            CborHead head = CborHead.at(data, pos, end);
            pos = head.end();
            return head;
        } catch (CborRuntimeException e) {
            throw new LedgerDecodingException(reason, e.getMessage(), e);
        }
    }

    /** @return the length of an array or map head of {@code major} (consumed), or {@link #INDEFINITE} */
    long containerHeader(int major) {
        int at = pos;
        CborHead head = next();
        if (head.major() != major) {
            throw reject("unexpected CBOR major type " + head.major() + " at " + at);
        }
        if (head.isIndefinite()) {
            return INDEFINITE;
        }
        if (head.argument() < 0 || head.argument() > Integer.MAX_VALUE) {
            throw reject("CBOR container too large at " + at);
        }
        return head.argument();
    }

    /** @return the content of the definite-length string of {@code major} at the cursor (a chunk), consumed */
    byte[] definiteString(int major) {
        int at = pos;
        CborHead head = next();
        if (head.major() != major || head.isIndefinite()) {
            throw reject("expected a definite-length string of major type " + major + " at " + at);
        }
        int contentEnd = stringEnd(head);
        byte[] out = Arrays.copyOfRange(data, head.end(), contentEnd);
        pos = contentEnd;
        return out;
    }

    /** Skips the content of a definite-length string whose head was just read. */
    void skipString(CborHead head) {
        pos = stringEnd(head);
    }

    private int stringEnd(CborHead head) {
        try {
            return head.stringEnd(end);
        } catch (CborRuntimeException e) {
            throw new LedgerDecodingException(reason, e.getMessage(), e);
        }
    }

    LedgerDecodingException reject(String message) {
        return new LedgerDecodingException(reason, message);
    }

    /**
     * The open arrays and maps of a walk: after reading an item's head the walker opens its container, and
     * {@link #next} says whether another item must be read, consuming the breaks of indefinite containers and closing
     * finished ones. A break may end an indefinite map only before a key; anywhere else it is read as an item, which
     * every walker refuses.
     */
    static final class Containers {
        private static final int ITEMS = 0;
        private static final int INDEFINITE_ITEMS = 1;
        private static final int INDEFINITE_MAP_KEY = 2;
        private static final int INDEFINITE_MAP_VALUE = 3;
        private static final int SEALED = 4;
        private static final int SEALED_INDEFINITE = 5;

        private int[] kind = new int[16];
        private long[] count = new long[16];
        private int depth;

        /** Opens a container of {@code major} 4 or 5 whose head gave {@code length} (or {@link #INDEFINITE}). */
        void open(int major, long length) {
            if (length == INDEFINITE) {
                push(major == 5 ? INDEFINITE_MAP_KEY : INDEFINITE_ITEMS, 0);
            } else {
                push(ITEMS, major == 5 ? 2 * length : length);
            }
        }

        /**
         * Opens an array whose {@code items} items the walker reads itself (each one opened on top of this): when they
         * are done, an indefinite array must end with its break and a definite one must have had exactly that many.
         */
        void seal(long length, int items) {
            if (length == INDEFINITE) {
                push(SEALED_INDEFINITE, 0);
            } else {
                push(SEALED, length - items);
            }
        }

        /** @return true when another item must be read, false when the walk is complete */
        boolean next(HeadCursor c) {
            while (depth > 0) {
                int top = depth - 1;
                switch (kind[top]) {
                    case ITEMS:
                        if (count[top] == 0) {
                            depth--;
                        } else {
                            count[top]--;
                            return true;
                        }
                        break;
                    case INDEFINITE_ITEMS:
                        if (!c.atBreak()) {
                            return true;
                        }
                        depth--;
                        break;
                    case INDEFINITE_MAP_KEY:
                        if (!c.atBreak()) {
                            kind[top] = INDEFINITE_MAP_VALUE;
                            return true;
                        }
                        depth--;
                        break;
                    case INDEFINITE_MAP_VALUE:
                        kind[top] = INDEFINITE_MAP_KEY;
                        return true;
                    default:
                        boolean indefinite = kind[top] == SEALED_INDEFINITE;
                        long extra = count[top];
                        depth--;
                        if (indefinite ? !c.atBreak() : extra != 0) {
                            throw c.reject("an array at " + c.pos + " does not have the expected number of elements");
                        }
                }
            }
            return false;
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
}
