package com.bloxbean.cardano.vds.mpf.proof;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the MPF proof wire format (the CBOR of Aiken's {@code Proof}, i.e. a list of
 * {@code Branch} (tag 121), {@code Fork} (tag 122) and {@code Leaf} (tag 123) constructors).
 *
 * <p>Proof bytes are untrusted, so this is a small bounded reader for exactly that grammar rather than a general
 * CBOR decoder: declared lengths are checked against the remaining input before anything is allocated, nesting is
 * limited, and trailing bytes are rejected. Definite- and indefinite-length arrays and byte strings are accepted
 * (CCL writes definite lengths, the off-chain JS library indefinite ones). Every field is checked against the digest
 * length. Malformed input fails with an {@link IllegalArgumentException}.</p>
 */
final class ProofDecoder {

    private static final int TAG_BRANCH = 121;
    private static final int TAG_FORK = 122;
    private static final int TAG_LEAF = 123;
    private static final int MAX_STEPS = 1024;
    private static final int MAX_DEPTH = 4;
    private static final long MAX_SKIP = 4096;

    private ProofDecoder() {
    }

    /** Decodes a proof whose digest length is inferred from its fields (they must agree). */
    static WireProof decode(byte[] cborBytes) {
        return decode(cborBytes, -1);
    }

    /**
     * Decodes a proof.
     *
     * @param digestLength the expected digest length in bytes, or {@code -1} to infer it from the proof
     * @throws IllegalArgumentException if the bytes are not a well-formed MPF proof
     */
    static WireProof decode(byte[] cborBytes, int digestLength) {
        if (cborBytes == null) {
            throw new IllegalArgumentException("Proof bytes are null");
        }
        Reader reader = new Reader(cborBytes);
        Item top = reader.readItem(0);
        if (!reader.atEnd()) {
            throw new IllegalArgumentException("Trailing bytes after MPF proof");
        }
        if (top.kind != Kind.ARRAY || top.tag >= 0) {
            throw new IllegalArgumentException("MPF proof must be an untagged CBOR array");
        }
        if (top.items.size() > MAX_STEPS) {
            throw new IllegalArgumentException("MPF proof has too many steps");
        }
        int[] digest = {digestLength};
        List<WireProof.Step> steps = new ArrayList<>(top.items.size());
        for (Item item : top.items) {
            steps.add(decodeStep(item, digest));
        }
        return new WireProof(steps);
    }

    private static WireProof.Step decodeStep(Item step, int[] digest) {
        if (step.kind != Kind.ARRAY || step.tag < 0) {
            throw new IllegalArgumentException("MPF proof step must be a tagged constructor");
        }
        List<Item> fields = step.items;
        if (step.tag == TAG_BRANCH) {
            // Branch { skip, neighbors } (+ optional branch value hash for non-MPF commitment schemes)
            if (fields.size() != 2 && fields.size() != 3) {
                throw new IllegalArgumentException("Branch step must have 2 fields");
            }
            int skip = readSkip(fields.get(0));
            byte[] neighborBytes = readBytes(fields.get(1));
            int d = digestFrom(digest, neighborBytes.length, 4);
            byte[][] neighbors = new byte[4][d];
            for (int i = 0; i < 4; i++) System.arraycopy(neighborBytes, i * d, neighbors[i], 0, d);
            byte[] branchValueHash = fields.size() == 3 ? readDigest(fields.get(2), digest) : null;
            return new WireProof.BranchStep(skip, neighbors, 0, branchValueHash);
        }
        if (step.tag == TAG_FORK) {
            // Fork { skip, neighbor: Neighbor { nibble, prefix, root } }
            if (fields.size() != 2) {
                throw new IllegalArgumentException("Fork step must have 2 fields");
            }
            int skip = readSkip(fields.get(0));
            Item neighbor = fields.get(1);
            if (neighbor.kind != Kind.ARRAY || neighbor.tag != TAG_BRANCH || neighbor.items.size() != 3) {
                throw new IllegalArgumentException("Fork neighbor must be a Neighbor constructor (tag 121, 3 fields)");
            }
            long nibble = readUnsigned(neighbor.items.get(0));
            if (nibble > 15) {
                throw new IllegalArgumentException("Fork neighbor nibble out of range");
            }
            byte[] prefix = readBytes(neighbor.items.get(1));
            for (byte b : prefix) {
                if ((b & 0xFF) > 15) {
                    throw new IllegalArgumentException("Fork neighbor prefix must hold one nibble per byte");
                }
            }
            byte[] root = readDigest(neighbor.items.get(2), digest);
            return new WireProof.ForkStep(skip, (int) nibble, prefix, root);
        }
        if (step.tag == TAG_LEAF) {
            // Leaf { skip, key, value }
            if (fields.size() != 3) {
                throw new IllegalArgumentException("Leaf step must have 3 fields");
            }
            int skip = readSkip(fields.get(0));
            byte[] keyHash = readDigest(fields.get(1), digest);
            byte[] valueHash = readDigest(fields.get(2), digest);
            return new WireProof.LeafStep(skip, keyHash, valueHash);
        }
        throw new IllegalArgumentException("Unknown MPF proof step tag: " + step.tag);
    }

    private static int readSkip(Item item) {
        long v = readUnsigned(item);
        if (v > MAX_SKIP) {
            throw new IllegalArgumentException("Proof step skip out of range");
        }
        return (int) v;
    }

    private static long readUnsigned(Item item) {
        if (item.kind != Kind.UINT || item.tag >= 0) {
            throw new IllegalArgumentException("Expected an unsigned integer");
        }
        return item.value;
    }

    private static byte[] readBytes(Item item) {
        if (item.kind != Kind.BYTES || item.tag >= 0) {
            throw new IllegalArgumentException("Expected a byte string");
        }
        return item.bytes;
    }

    private static byte[] readDigest(Item item, int[] digest) {
        byte[] bytes = readBytes(item);
        digestFrom(digest, bytes.length, 1);
        return bytes;
    }

    /** Checks (or infers) the digest length given a field of {@code multiple} digests. */
    private static int digestFrom(int[] digest, int length, int multiple) {
        if (length == 0 || length % multiple != 0) {
            throw new IllegalArgumentException("Proof field has an invalid length " + length);
        }
        int d = length / multiple;
        if (digest[0] < 0) {
            digest[0] = d;
        } else if (digest[0] != d) {
            throw new IllegalArgumentException("Proof field length " + length + " does not match digest length " + digest[0]);
        }
        return d;
    }

    // ------------------------------------------------------------------ bounded CBOR reader

    private enum Kind { UINT, BYTES, ARRAY }

    private static final class Item {
        final Kind kind;
        final long value;        // UINT (unsigned, < 2^63 enforced)
        final byte[] bytes;      // BYTES
        final List<Item> items;  // ARRAY
        long tag = -1;           // -1 = untagged

        private Item(Kind kind, long value, byte[] bytes, List<Item> items) {
            this.kind = kind;
            this.value = value;
            this.bytes = bytes;
            this.items = items;
        }
    }

    private static final class Reader {
        private static final int BREAK = 0xFF;
        private final byte[] in;
        private int pos;

        Reader(byte[] in) {
            this.in = in;
        }

        boolean atEnd() {
            return pos == in.length;
        }

        private int remaining() {
            return in.length - pos;
        }

        private int peek() {
            if (pos >= in.length) throw new IllegalArgumentException("Unexpected end of MPF proof");
            return in[pos] & 0xFF;
        }

        private int next() {
            int b = peek();
            pos++;
            return b;
        }

        /** Reads the argument of an initial byte; returns -1 for the indefinite-length marker. */
        private long argument(int info) {
            if (info < 24) return info;
            int n;
            switch (info) {
                case 24: n = 1; break;
                case 25: n = 2; break;
                case 26: n = 4; break;
                case 27: n = 8; break;
                case 31: return -1;
                default: throw new IllegalArgumentException("Invalid CBOR additional information " + info);
            }
            if (remaining() < n) throw new IllegalArgumentException("Unexpected end of MPF proof");
            long v = 0;
            for (int i = 0; i < n; i++) v = (v << 8) | (in[pos++] & 0xFF);
            if (v < 0) throw new IllegalArgumentException("CBOR argument too large");
            return v;
        }

        Item readItem(int depth) {
            if (depth > MAX_DEPTH) throw new IllegalArgumentException("MPF proof nested too deeply");
            int initial = next();
            int major = initial >> 5;
            long arg = argument(initial & 0x1F);
            switch (major) {
                case 0:
                    if (arg < 0) throw new IllegalArgumentException("Indefinite-length integer");
                    return new Item(Kind.UINT, arg, null, null);
                case 2:
                    return new Item(Kind.BYTES, 0, arg < 0 ? indefiniteBytes() : definiteBytes(arg), null);
                case 4:
                    return new Item(Kind.ARRAY, 0, null, arg < 0 ? indefiniteArray(depth) : definiteArray(arg, depth));
                case 6: {
                    if (arg < 0) throw new IllegalArgumentException("Invalid CBOR tag");
                    Item tagged = readItem(depth);
                    if (tagged.tag >= 0) throw new IllegalArgumentException("Nested CBOR tags are not allowed");
                    tagged.tag = arg;
                    return tagged;
                }
                default:
                    throw new IllegalArgumentException("Unexpected CBOR major type " + major + " in MPF proof");
            }
        }

        private byte[] definiteBytes(long length) {
            if (length > remaining()) throw new IllegalArgumentException("Byte string longer than the proof");
            byte[] out = new byte[(int) length];
            System.arraycopy(in, pos, out, 0, out.length);
            pos += out.length;
            return out;
        }

        private byte[] indefiniteBytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (peek() != BREAK) {
                int initial = next();
                if (initial >> 5 != 2) throw new IllegalArgumentException("Indefinite byte string chunk must be a byte string");
                long length = argument(initial & 0x1F);
                if (length < 0) throw new IllegalArgumentException("Nested indefinite byte string");
                byte[] chunk = definiteBytes(length);
                out.write(chunk, 0, chunk.length);
            }
            pos++; // BREAK
            return out.toByteArray();
        }

        private List<Item> definiteArray(long length, int depth) {
            // Every element takes at least one byte, so a larger count cannot be satisfied by the input.
            if (length > remaining()) throw new IllegalArgumentException("Array longer than the proof");
            List<Item> items = new ArrayList<>((int) length);
            for (long i = 0; i < length; i++) items.add(readItem(depth + 1));
            return items;
        }

        private List<Item> indefiniteArray(int depth) {
            List<Item> items = new ArrayList<>();
            while (peek() != BREAK) {
                items.add(readItem(depth + 1));
            }
            pos++; // BREAK
            return items;
        }
    }
}
