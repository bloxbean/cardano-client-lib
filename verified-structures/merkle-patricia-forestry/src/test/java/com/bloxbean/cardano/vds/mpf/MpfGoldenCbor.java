package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal CBOR reader for MPF proofs (tests only).
 * Handles indefinite arrays/byte strings and the three MPF tags: 121, 122, 123.
 */
public final class MpfGoldenCbor {

    interface Step {
        int skip();
    }

    static final class BranchStep implements Step {
        final int skip;
        final byte[][] neighbors;

        BranchStep(int s, byte[][] n) {
            this.skip = s;
            this.neighbors = n;
        }

        public int skip() {
            return skip;
        }
    }

    static final class ForkStep implements Step {
        final int skip;
        final int nibble;
        final byte[] prefix;
        final byte[] root;

        ForkStep(int s, int n, byte[] p, byte[] r) {
            skip = s;
            nibble = n;
            prefix = p;
            root = r;
        }

        public int skip() {
            return skip;
        }
    }

    static final class LeafStep implements Step {
        final int skip;
        final byte[] keyHash;
        final byte[] valueHash;

        LeafStep(int s, byte[] k, byte[] v) {
            skip = s;
            keyHash = k;
            valueHash = v;
        }

        public int skip() {
            return skip;
        }
    }

    static final class Proof {
        final List<Step> steps;

        Proof(List<Step> steps) {
            this.steps = steps;
        }

        byte[] computeRoot(byte[] key, byte[] value, boolean including, HashFunction hashFn, CommitmentScheme commitments) {
            String pathHex = Bytes.toHex(hashFn.digest(key));
            if (steps.isEmpty()) {
                if (!including) return null;
                if (value == null) throw new IllegalArgumentException("Value required for inclusion");
                return commitments.commitLeaf(NibblePath.EMPTY, hashFn.digest(value));
            }
            return loop(0, 0, pathHex, value, including, hashFn, commitments, commitments.nullHash());
        }

        private byte[] loop(int cursor, int ix, String pathHex, byte[] value, boolean including,
                            HashFunction hashFn, CommitmentScheme commitments, byte[] nullHash) {
            if (ix >= steps.size()) {
                if (!including) return null;
                if (value == null) throw new IllegalArgumentException("Value required for inclusion");
                String suffixHex = pathHex.substring(cursor);
                return commitments.commitLeaf(NibblePath.of(nibblesFromHex(suffixHex)), hashFn.digest(value));
            }
            Step step = steps.get(ix);
            int nextCursor = cursor + 1 + step.skip();
            byte[] childHash = loop(nextCursor, ix + 1, pathHex, value, including, hashFn, commitments, nullHash);
            if (childHash == null) childHash = nullHash;
            int nib = hexCharToNibble(pathHex.charAt(nextCursor - 1));
            boolean last = ix + 1 == steps.size();

            if (step instanceof BranchStep) {
                BranchStep br = (BranchStep) step;
                byte[] merkle = aggregateSiblingHashes(nib, childHash, br.neighbors, hashFn, nullHash);
                byte[] prefixBytes = nibbleBytes(pathHex.substring(cursor, nextCursor - 1));
                return hashFn.digest(Bytes.concat(prefixBytes, merkle));
            } else if (step instanceof ForkStep) {
                ForkStep fk = (ForkStep) step;
                if (!including && last) {
                    byte[] prefixBytes;
                    if (fk.skip == 0) {
                        prefixBytes = Bytes.concat(new byte[]{(byte) fk.nibble}, fk.prefix);
                    } else {
                        byte[] skipped = nibbleBytes(pathHex.substring(cursor, cursor + fk.skip));
                        prefixBytes = Bytes.concat(skipped, new byte[]{(byte) fk.nibble}, fk.prefix);
                    }
                    return hashFn.digest(Bytes.concat(prefixBytes, fk.root));
                }
                if (fk.nibble == nib) throw new IllegalStateException("Fork neighbor nibble equals path nibble");
                byte[] neighborHash = hashFn.digest(Bytes.concat(fk.prefix, fk.root));
                return branchFromSparse(pathHex.substring(cursor, nextCursor - 1), nib, childHash, fk.nibble, neighborHash, hashFn, nullHash);
            } else {
                LeafStep lf = (LeafStep) step;
                String neighborPath = Bytes.toHex(lf.keyHash);
                if (!neighborPath.startsWith(pathHex.substring(0, cursor)))
                    throw new IllegalStateException("Leaf neighbor path mismatch");
                int neighborNib = hexCharToNibble(neighborPath.charAt(nextCursor - 1));
                if (neighborNib == nib) throw new IllegalStateException("Leaf neighbor nibble equals path nibble");
                if (!including && last) {
                    String suffix = neighborPath.substring(cursor);
                    return commitments.commitLeaf(NibblePath.of(nibblesFromHex(suffix)), lf.valueHash);
                }
                String suffix = neighborPath.substring(nextCursor);
                byte[] neighborHash = commitments.commitLeaf(NibblePath.of(nibblesFromHex(suffix)), lf.valueHash);
                return branchFromSparse(pathHex.substring(cursor, nextCursor - 1), nib, childHash, neighborNib, neighborHash, hashFn, nullHash);
            }
        }
    }

    // Parser
    static Proof parse(byte[] cbor) {
        Cursor cur = new Cursor(cbor);
        int head = cur.peek() & 0xFF;
        int majorType = (head & 0xE0);
        boolean isArray = (majorType == 0x80) || head == 0x9F; // definite or indefinite array
        boolean isTag = (majorType == 0xC0);
        if (!isArray && !isTag) throw new IllegalArgumentException("Expected array for proof");
        List<Object> items = readArray(cur);
        List<Step> steps = new ArrayList<>();
        for (Object item : items) steps.add(parseStep((Obj) item));
        return new Proof(steps);
    }

    private static Step parseStep(Obj obj) {
        // obj is Tag(121|122|123) over an Array
        if (obj.tag < 0) throw new IllegalArgumentException("Missing tag");
        if (!(obj.value instanceof Obj) || ((Obj) obj.value).kind != Kind.ARRAY)
            throw new IllegalArgumentException("Tagged array expected");
        Obj arr = (Obj) obj.value;
        List<Obj> a = arr.items;
        switch (obj.tag) {
            case 121: {
                int skip = (int) a.get(0).num;
                byte[] neighborsBytes = a.get(1).bytes;
                int L = neighborsBytes.length / 4;
                byte[][] neighbors = new byte[4][];
                for (int i = 0; i < 4; i++) {
                    neighbors[i] = Arrays.copyOfRange(neighborsBytes, i * L, (i + 1) * L);
                }
                // Optional third element: branch value hash (ignored in MPF mode)
                return new BranchStep(skip, neighbors);
            }
            case 122: {
                int skip = (int) a.get(0).num;
                Obj neighbor = a.get(1); // may be tag 121 over array
                if (neighbor.tag != 121) throw new IllegalArgumentException("Fork neighbor must be tag 121");
                Obj nArr = (Obj) neighbor.value;
                int nibble = (int) nArr.items.get(0).num;
                byte[] prefix = nArr.items.get(1).bytes;
                byte[] root = nArr.items.get(2).bytes;
                return new ForkStep(skip, nibble, prefix, root);
            }
            case 123: {
                int skip = (int) a.get(0).num;
                byte[] keyHash = a.get(1).bytes;
                byte[] valueHash = a.get(2).bytes;
                return new LeafStep(skip, keyHash, valueHash);
            }
            default:
                throw new IllegalArgumentException("Unknown tag: " + obj.tag);
        }
    }

    // Minimal CBOR structures
    enum Kind {ARRAY, BYTESTRING, UINT, TAG}

    static final class Obj {
        Kind kind;
        List<Obj> items;
        byte[] bytes;
        long num;
        int tag;
        Object value;
    }

    static final class Cursor {
        final byte[] b;
        int i = 0;

        Cursor(byte[] b) {
            this.b = b;
        }

        int peek() {
            return b[i];
        }

        int read() {
            return b[i++] & 0xFF;
        }
    }

    private static List<Object> readArray(Cursor c) {
        Obj arr = readObj(c);
        if (arr.kind == Kind.TAG && arr.value instanceof Obj) {
            arr = (Obj) arr.value; // accept tag-wrapped array (mode tag)
        }
        if (arr.kind != Kind.ARRAY) throw new IllegalArgumentException("not array");
        return (List) arr.items;
    }

    private static Obj readObj(Cursor c) {
        int ib = c.read();
        int mt = (ib & 0xE0) >> 5;
        int ai = ib & 0x1F;
        switch (mt) {
            case 4: // array
                return readArrayObj(c, ai);
            case 2: // byte string
                return readByteStringObj(c, ai);
            case 0: // uint
                return readUintObj(c, ai);
            case 6: // tag
                int tag = (int) readUintValue(c, ai);
                Obj inner = readObj(c);
                Obj t = new Obj();
                t.kind = Kind.TAG;
                t.tag = tag;
                t.value = inner;
                return t;
            default:
                throw new IllegalArgumentException("Unsupported major type: " + mt);
        }
    }

    private static Obj readArrayObj(Cursor c, int ai) {
        Obj o = new Obj();
        o.kind = Kind.ARRAY;
        o.items = new ArrayList<>();
        if (ai == 31) { // indefinite
            while ((c.peek() & 0xFF) != 0xFF) {
                o.items.add(readObj(c));
            }
            c.read(); // break
        } else {
            int len = (int) readUintValue(c, ai);
            for (int i = 0; i < len; i++) o.items.add(readObj(c));
        }
        return o;
    }

    private static Obj readByteStringObj(Cursor c, int ai) {
        Obj o = new Obj();
        o.kind = Kind.BYTESTRING;
        if (ai == 31) { // indefinite
            byte[] acc = new byte[0];
            while ((c.peek() & 0xFF) != 0xFF) {
                Obj chunk = readObj(c);
                if (chunk.kind != Kind.BYTESTRING) throw new IllegalArgumentException("expected bstr chunk");
                acc = Bytes.concat(acc, chunk.bytes);
            }
            c.read();
            o.bytes = acc;
            return o;
        }
        int len = (int) readUintValue(c, ai);
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = (byte) c.read();
        o.bytes = out;
        return o;
    }

    private static Obj readUintObj(Cursor c, int ai) {
        Obj o = new Obj();
        o.kind = Kind.UINT;
        o.num = readUintValue(c, ai);
        return o;
    }

    private static long readUintValue(Cursor c, int ai) {
        if (ai < 24) return ai;
        if (ai == 24) return c.read();
        if (ai == 25) return ((c.read() << 8) | c.read());
        if (ai == 26) return ((long) c.read() << 24) | ((long) c.read() << 16) | ((long) c.read() << 8) | c.read();
        throw new IllegalArgumentException("uint too large");
    }

    // Helpers mirroring production WireProof
    private static int[] nibblesFromHex(String hex) {
        int[] out = new int[hex.length()];
        for (int i = 0; i < hex.length(); i++) out[i] = Integer.parseInt(hex.substring(i, i + 1), 16);
        return out;
    }

    private static int hexCharToNibble(char c) {
        return Integer.parseInt(String.valueOf(c), 16);
    }

    private static byte[] nibbleBytes(String hex) {
        byte[] out = new byte[hex.length()];
        for (int i = 0; i < hex.length(); i++) out[i] = (byte) Integer.parseInt(hex.substring(i, i + 1), 16);
        return out;
    }

    private static byte[] branchFromSparse(String prefixHex, int meNibble, byte[] meHash, int neighborNibble, byte[] neighborHash,
                                           HashFunction hashFn, byte[] nullHash) {
        byte[][] nodes = new byte[16][];
        for (int i = 0; i < 16; i++) nodes[i] = Arrays.copyOf(nullHash, nullHash.length);
        nodes[meNibble] = meHash;
        nodes[neighborNibble] = neighborHash;
        int length = nodes.length;
        byte[][] layer = nodes;
        while (length > 1) {
            byte[][] next = new byte[length / 2][];
            for (int i = 0; i < length; i += 2) {
                next[i / 2] = hashFn.digest(Bytes.concat(layer[i], layer[i + 1]));
            }
            layer = next;
            length = layer.length;
        }
        byte[] merkle = layer[0];
        byte[] prefixBytes = nibbleBytes(prefixHex);
        return hashFn.digest(Bytes.concat(prefixBytes, merkle));
    }

    private static byte[] aggregateSiblingHashes(int nibble, byte[] me, byte[][] neighbors,
                                                 HashFunction hashFn, byte[] nullHash) {
        byte[] lvl1 = neighbors[0];
        byte[] lvl2 = neighbors[1];
        byte[] lvl3 = neighbors[2];
        byte[] lvl4 = neighbors[3];
        switch (nibble) {
            case 0:
                return h(h(h(h(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
            case 1:
                return h(h(h(h(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
            case 2:
                return h(h(h(lvl3, h(me, lvl4, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
            case 3:
                return h(h(h(lvl3, h(lvl4, me, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
            case 4:
                return h(h(lvl2, h(h(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
            case 5:
                return h(h(lvl2, h(h(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
            case 6:
                return h(h(lvl2, h(lvl3, h(me, lvl4, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
            case 7:
                return h(h(lvl2, h(lvl3, h(lvl4, me, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
            case 8:
                return h(lvl1, h(h(h(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
            case 9:
                return h(lvl1, h(h(h(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
            case 10:
                return h(lvl1, h(h(lvl3, h(me, lvl4, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
            case 11:
                return h(lvl1, h(h(lvl3, h(lvl4, me, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
            case 12:
                return h(lvl1, h(lvl2, h(h(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
            case 13:
                return h(lvl1, h(lvl2, h(h(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
            case 14:
                return h(lvl1, h(lvl2, h(lvl3, h(me, lvl4, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
            case 15:
                return h(lvl1, h(lvl2, h(lvl3, h(lvl4, me, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
            default:
                throw new IllegalArgumentException("Invalid nibble: " + nibble);
        }
    }

    private static byte[] h(byte[] left, byte[] right, HashFunction hashFn, byte[] nullHash) {
        byte[] l = left == null ? nullHash : left;
        byte[] r = right == null ? nullHash : right;
        return hashFn.digest(Bytes.concat(l, r));
    }

    // Test helper (MPT commitment)
    public static boolean verify(byte[] expectedRoot, byte[] key, byte[] value, boolean including,
                                 byte[] cbor, HashFunction hashFn) {
        Proof p = parse(cbor);
        CommitmentScheme cs = new MpfCommitmentScheme(hashFn);
        byte[] computed = p.computeRoot(key, value, including, hashFn, cs);
        byte[] ne = expectedRoot == null ? cs.nullHash() : expectedRoot;
        byte[] nc = computed == null ? cs.nullHash() : computed;
        return Arrays.equals(ne, nc);
    }

    // Test helper (JMT: same MPF semantics, reuse MPT commitment scheme)
    public static boolean verifyJmt(byte[] expectedRoot, byte[] key, byte[] value, boolean including,
                                    byte[] cbor, HashFunction hashFn) {
        return verify(expectedRoot, key, value, including, cbor, hashFn);
    }
}
