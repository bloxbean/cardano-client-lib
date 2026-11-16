package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A canonical re-encoding of a CBOR item, used as the equality key of decoded values: two encodings Haskell decodes
 * to equal values give the same key. Definite lengths, shortest heads, byte and text strings with their chunks
 * joined, map entries sorted by key, a tag-258 set's elements sorted (the tag dropped), and a tag-30 rational reduced
 * (Haskell keeps {@code BoundedRatio} normalised). An untagged set is sorted where the caller says one is.
 */
final class CborCanonical {

    /**
     * The deepest item compared: no value of Conway the ledger compares for equality, a set's item or a proposal, nests
     * near it, so a deeper one is not a value Haskell decodes, and the recursion stays within any thread's stack.
     */
    private static final int MAX_DEPTH = 64;

    private final byte[] data;
    private final Set<Integer> sets;
    private int pos;
    private int depth;

    private CborCanonical(byte[] data, int pos) {
        this(data, pos, Set.of());
    }

    private CborCanonical(byte[] data, int pos, Set<Integer> sets) {
        this.data = data;
        this.pos = pos;
        this.sets = sets;
    }

    /** @return the canonical encoding of {@code item} */
    static byte[] of(CborSpan item) {
        if (shortestScalarLength(item.buffer(), item.offset()) == item.length()) {
            return item.bytes();
        }
        return of(item, Set.of());
    }

    /**
     * @param sets the offsets in the item's buffer of the arrays inside it that are sets
     * @return the canonical encoding of {@code item}, with those arrays' elements sorted as a tag-258 set's are
     */
    static byte[] of(CborSpan item, Set<Integer> sets) {
        CborCanonical c = new CborCanonical(item.buffer(), item.offset(), sets);
        byte[] out = c.item();
        c.requireEnd(item);
        return out;
    }

    /**
     * @return the length of the item at {@code at} if it is an integer or a definite string whose head is the shortest,
     *         which is its own canonical encoding; else -1
     */
    private static int shortestScalarLength(byte[] data, int at) {
        int initial = data[at] & 0xff;
        int major = initial >>> 5;
        int info = initial & 0x1f;
        if (major > 3 || info > 27) {
            return -1;
        }
        int head = 1 + (info < 24 ? 0 : 1 << (info - 24));
        if (at + head > data.length) {
            return -1;
        }
        long value = info;
        if (info >= 24) {
            value = 0;
            for (int i = 1; i < head; i++) {
                value = value << 8 | data[at + i] & 0xff;
            }
            // the smallest value of each head length: 24, 2^8, 2^16, 2^32
            if (Long.compareUnsigned(value, info == 24 ? 24 : 1L << (8 << (info - 25))) < 0) {
                return -1;
            }
        }
        long length = head + (major < 2 ? 0 : value);
        return value >= 0 && at + length <= data.length ? (int) length : -1;
    }

    private void requireEnd(CborSpan item) {
        if (pos != item.offset() + item.length()) {
            throw new LedgerDecodingException(Reason.TRAILING_BYTES,
                    "CBOR item at " + item.offset() + " has trailing bytes");
        }
    }

    private int peek() {
        if (pos >= data.length) {
            throw new LedgerDecodingException(Reason.NOT_WELL_FORMED, "unexpected end of CBOR at " + pos);
        }
        return data[pos] & 0xff;
    }

    private byte[] item() {
        if (++depth > MAX_DEPTH) {
            throw new LedgerDecodingException(Reason.NESTING_TOO_DEEP,
                    "CBOR item nested more than " + MAX_DEPTH + " deep at " + pos);
        }
        byte[] out = itemHere();
        depth--;
        return out;
    }

    private byte[] itemHere() {
        int head = peek();
        int scalar = shortestScalarLength(data, pos);
        if (scalar > 0) {
            pos += scalar;
            return Arrays.copyOfRange(data, pos - scalar, pos);
        }
        int major = head >>> 5;
        int info = head & 0x1f;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (major) {
            case 0, 1 -> {
                pos++;
                head(out, major, argument(info));
            }
            case 2, 3 -> {
                byte[] content = string(major);
                head(out, major, BigInteger.valueOf(content.length));
                out.writeBytes(content);
            }
            case 4 -> {
                if (!sets.isEmpty() && sets.contains(pos)) {
                    return sortedArray();
                }
                List<byte[]> items = array();
                head(out, 4, BigInteger.valueOf(items.size()));
                items.forEach(out::writeBytes);
            }
            case 5 -> {
                pos++;
                long n = length(info);
                List<byte[][]> entries = new ArrayList<>();
                for (long i = 0; more(n, i); i++) {
                    entries.add(new byte[][]{item(), item()});
                }
                entries.sort((a, b) -> Arrays.compareUnsigned(a[0], b[0]));
                head(out, 5, BigInteger.valueOf(entries.size()));
                entries.forEach(e -> {
                    out.writeBytes(e[0]);
                    out.writeBytes(e[1]);
                });
            }
            case 6 -> {
                pos++;
                BigInteger tag = argument(info);
                if (tag.equals(BigInteger.valueOf(258))) {
                    return sortedArray();
                }
                head(out, 6, tag);
                if (tag.equals(BigInteger.valueOf(30))) {
                    out.writeBytes(rational());
                } else {
                    out.writeBytes(item());
                }
            }
            default -> {
                int start = pos;
                pos++;
                if (info >= 24 && info <= 27) {
                    pos += 1 << (info - 24);
                }
                out.write(data, start, pos - start);
            }
        }
        return out.toByteArray();
    }

    /** A {@code [numerator, denominator]} reduced by their greatest common divisor. */
    private byte[] rational() {
        int start = pos;
        List<byte[]> items = array();
        if (items.size() == 2 && (items.get(0)[0] & 0xe0) == 0 && (items.get(1)[0] & 0xe0) == 0) {
            BigInteger n = new CborCanonical(items.get(0), 0).argumentOf();
            BigInteger d = new CborCanonical(items.get(1), 0).argumentOf();
            BigInteger g = n.gcd(d);
            if (g.signum() > 0) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                head(out, 4, BigInteger.TWO);
                head(out, 0, n.divide(g));
                head(out, 0, d.divide(g));
                return out.toByteArray();
            }
        }
        pos = start;
        return item();
    }

    private BigInteger argumentOf() {
        int head = peek();
        pos++;
        return argument(head & 0x1f);
    }

    private byte[] sortedArray() {
        List<byte[]> items = array();
        items.sort(Arrays::compareUnsigned);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        head(out, 4, BigInteger.valueOf(items.size()));
        items.forEach(out::writeBytes);
        return out.toByteArray();
    }

    private List<byte[]> array() {
        int head = peek();
        if (head >>> 5 != 4) {
            throw new LedgerDecodingException(Reason.UNEXPECTED_TYPE, "expected an array at " + pos);
        }
        pos++;
        long n = length(head & 0x1f);
        List<byte[]> items = new ArrayList<>();
        for (long i = 0; more(n, i); i++) {
            items.add(item());
        }
        return items;
    }

    private byte[] string(int major) {
        int head = peek();
        pos++;
        int info = head & 0x1f;
        if (info != 31) {
            return bytes(argument(info));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (peek() != 0xff) {
            int chunk = peek();
            if (chunk >>> 5 != major || (chunk & 0x1f) == 31) {
                throw new LedgerDecodingException(Reason.NOT_WELL_FORMED, "bad indefinite string chunk at " + pos);
            }
            pos++;
            out.writeBytes(bytes(argument(chunk & 0x1f)));
        }
        pos++;
        return out.toByteArray();
    }

    private byte[] bytes(BigInteger length) {
        if (length.compareTo(BigInteger.valueOf(data.length - pos)) > 0) {
            throw new LedgerDecodingException(Reason.NOT_WELL_FORMED, "string runs past the input at " + pos);
        }
        byte[] out = Arrays.copyOfRange(data, pos, pos + length.intValue());
        pos += out.length;
        return out;
    }

    private long length(int info) {
        if (info == 31) {
            return -1;
        }
        BigInteger n = argument(info);
        if (n.bitLength() > 31) {
            throw new LedgerDecodingException(Reason.NOT_WELL_FORMED, "container too large at " + pos);
        }
        return n.longValue();
    }

    private boolean more(long n, long i) {
        if (n >= 0) {
            return i < n;
        }
        if (peek() == 0xff) {
            pos++;
            return false;
        }
        return true;
    }

    private BigInteger argument(int info) {
        if (info < 24) {
            return BigInteger.valueOf(info);
        }
        if (info > 27) {
            throw new LedgerDecodingException(Reason.NOT_WELL_FORMED,
                    "unsupported CBOR additional information " + info + " at " + pos);
        }
        int n = 1 << (info - 24);
        if (pos + n > data.length) {
            throw new LedgerDecodingException(Reason.NOT_WELL_FORMED, "truncated CBOR head at " + pos);
        }
        BigInteger v = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            v = v.shiftLeft(8).or(BigInteger.valueOf(data[pos++] & 0xff));
        }
        return v;
    }

    private static void head(ByteArrayOutputStream out, int major, BigInteger value) {
        int type = major << 5;
        long v = value.longValue();
        if (value.bitLength() > 63) {
            out.write(type | 27);
            byte[] b = value.toByteArray();
            byte[] fixed = new byte[8];
            System.arraycopy(b, Math.max(0, b.length - 8), fixed, Math.max(0, 8 - b.length), Math.min(8, b.length));
            out.writeBytes(fixed);
        } else if (v < 24) {
            out.write(type | (int) v);
        } else if (v < 0x100) {
            out.write(type | 24);
            out.write((int) v);
        } else if (v < 0x10000) {
            out.write(type | 25);
            out.write((int) (v >> 8));
            out.write((int) v);
        } else if (v < 0x1_0000_0000L) {
            out.write(type | 26);
            for (int shift = 24; shift >= 0; shift -= 8) {
                out.write((int) (v >> shift));
            }
        } else {
            out.write(type | 27);
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) (v >> shift));
            }
        }
    }
}
