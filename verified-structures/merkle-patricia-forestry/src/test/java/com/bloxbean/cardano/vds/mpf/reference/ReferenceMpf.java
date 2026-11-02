package com.bloxbean.cardano.vds.mpf.reference;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Test-only reference model of a Merkle Patricia Forestry, written from the specification (the Aiken
 * {@code merkle-patricia-forestry} library and its off-chain JS {@code Trie.fromList} / {@code Proof.rewind}),
 * independently of the production {@code MpfTrie}. It holds no nodes: roots and proofs are computed directly from
 * the set of (hashed key, value hash) pairs, so it cannot share a bug with the node-based implementation.
 */
public final class ReferenceMpf {

    private static final ObjectMapper OM = new ObjectMapper();

    private final HashFunction hashFn;
    private final byte[] nullHash;
    private final Map<String, byte[]> entries = new TreeMap<>(); // hex(path) -> value hash

    public ReferenceMpf(HashFunction hashFn) {
        this.hashFn = hashFn;
        this.nullHash = new byte[hashFn.digest(new byte[0]).length];
    }

    public void put(byte[] key, byte[] value) {
        entries.put(hex(hashFn.digest(key)), hashFn.digest(value));
    }

    public void delete(byte[] key) {
        entries.remove(hex(hashFn.digest(key)));
    }

    /** The root, or {@code null} for the empty trie. */
    public byte[] root() {
        if (entries.isEmpty()) return null;
        return hash(all(entries), 0);
    }

    /**
     * The canonical proof for {@code key} in the shape of the JS library's {@code Proof.toJSON()}. For an absent key
     * this is the proof of the key in the trie where it was inserted.
     *
     * @param branchOnly emit a {@code Branch} step at every branch (the pre-#715 CCL shape) instead of
     *                   {@code Leaf}/{@code Fork} steps for branches with a single other child
     */
    public ArrayNode proof(byte[] key, boolean branchOnly) {
        ArrayNode steps = OM.createArrayNode();
        Map<String, byte[]> withKey = new TreeMap<>(entries);
        String keyPath = hex(hashFn.digest(key));
        withKey.putIfAbsent(keyPath, hashFn.digest(new byte[0]));
        List<Entry> node = all(withKey);
        int[] path = nibbles(keyPath);
        int depth = 0;
        while (node.size() > 1) {
            int c = commonPrefix(node, depth);
            int at = depth + c;
            Map<Integer, List<Entry>> groups = groups(node, at);
            int me = path[at];
            List<Integer> siblings = new ArrayList<>(groups.keySet());
            siblings.remove(Integer.valueOf(me));
            ObjectNode step = steps.addObject();
            if (siblings.size() == 1 && !branchOnly) {
                int s = siblings.get(0);
                List<Entry> g = groups.get(s);
                ObjectNode neighbor = OM.createObjectNode();
                if (g.size() == 1) {
                    step.put("type", "leaf");
                    step.put("skip", c);
                    neighbor.put("key", g.get(0).pathHex);
                    neighbor.put("value", hex(g.get(0).valueHash));
                } else {
                    int c2 = commonPrefix(g, at + 1);
                    step.put("type", "fork");
                    step.put("skip", c);
                    neighbor.put("nibble", s);
                    neighbor.put("prefix", hex(nibbleBytes(g.get(0).path, at + 1, at + 1 + c2)));
                    neighbor.put("root", hex(merkle16(children(g, at + 1 + c2))));
                }
                step.set("neighbor", neighbor);
            } else {
                step.put("type", "branch");
                step.put("skip", c);
                step.put("neighbors", hex(merkleProof(children(node, at), me)));
            }
            node = groups.get(me);
            depth = at + 1;
        }
        return steps;
    }

    // ------------------------------------------------------------------ model

    private static final class Entry {
        final String pathHex;
        final int[] path;
        final byte[] valueHash;

        Entry(String pathHex, byte[] valueHash) {
            this.pathHex = pathHex;
            this.path = nibbles(pathHex);
            this.valueHash = valueHash;
        }
    }

    private static List<Entry> all(Map<String, byte[]> m) {
        List<Entry> out = new ArrayList<>(m.size());
        m.forEach((k, v) -> out.add(new Entry(k, v)));
        return out;
    }

    /** Commitment of the subtree holding {@code node} (paths agree up to {@code depth}). */
    private byte[] hash(List<Entry> node, int depth) {
        if (node.size() == 1) {
            return leaf(node.get(0).path, depth, node.get(0).valueHash);
        }
        int c = commonPrefix(node, depth);
        return combine(nibbleBytes(node.get(0).path, depth, depth + c), merkle16(children(node, depth + c)));
    }

    private byte[][] children(List<Entry> node, int at) {
        byte[][] children = new byte[16][];
        for (Map.Entry<Integer, List<Entry>> g : groups(node, at).entrySet()) {
            children[g.getKey()] = hash(g.getValue(), at + 1);
        }
        return children;
    }

    private static Map<Integer, List<Entry>> groups(List<Entry> node, int at) {
        Map<Integer, List<Entry>> groups = new TreeMap<>();
        for (Entry e : node) groups.computeIfAbsent(e.path[at], k -> new ArrayList<>()).add(e);
        return groups;
    }

    private static int commonPrefix(List<Entry> node, int depth) {
        int[] first = node.get(0).path;
        int c = 0;
        outer:
        while (depth + c < first.length) {
            for (Entry e : node) {
                if (e.path[depth + c] != first[depth + c]) break outer;
            }
            c++;
        }
        return c;
    }

    /** Aiken {@code combine(suffix(path, cursor), value)}. */
    private byte[] leaf(int[] path, int cursor, byte[] valueHash) {
        byte[] head;
        int from;
        if (cursor % 2 == 0) {
            head = new byte[]{(byte) 0xFF};
            from = cursor;
        } else {
            head = new byte[]{0x00, (byte) path[cursor]};
            from = cursor + 1;
        }
        byte[] tail = new byte[(path.length - from) / 2];
        for (int i = 0; i < tail.length; i++) tail[i] = (byte) ((path[from + 2 * i] << 4) | path[from + 2 * i + 1]);
        return hashFn.digest(concat(head, tail, valueHash));
    }

    private byte[] merkle16(byte[][] children) {
        byte[][] layer = new byte[16][];
        for (int i = 0; i < 16; i++) layer[i] = children[i] == null ? nullHash : children[i];
        return merkleRange(layer, 0, 16);
    }

    private byte[] merkleRange(byte[][] layer, int from, int to) {
        if (to - from == 1) return layer[from];
        int mid = (from + to) / 2;
        return combine(merkleRange(layer, from, mid), merkleRange(layer, mid, to));
    }

    /** Sibling subtree roots of width 8, 4, 2 and 1 for slot {@code me} (JS {@code merkleProof}). */
    private byte[] merkleProof(byte[][] children, int me) {
        byte[][] layer = new byte[16][];
        for (int i = 0; i < 16; i++) layer[i] = children[i] == null ? nullHash : children[i];
        byte[] out = new byte[0];
        int from = 0, to = 16;
        while (to - from > 1) {
            int mid = (from + to) / 2;
            if (me < mid) {
                out = concat(out, merkleRange(layer, mid, to));
                to = mid;
            } else {
                out = concat(out, merkleRange(layer, from, mid));
                from = mid;
            }
        }
        return out;
    }

    private byte[] combine(byte[] a, byte[] b) {
        return hashFn.digest(concat(a, b));
    }

    // ------------------------------------------------------------------ bytes

    private static byte[] nibbleBytes(int[] path, int from, int to) {
        byte[] out = new byte[Math.max(0, to - from)];
        for (int i = from; i < to; i++) out[i - from] = (byte) path[i];
        return out;
    }

    private static int[] nibbles(String hex) {
        int[] out = new int[hex.length()];
        for (int i = 0; i < out.length; i++) out[i] = Character.digit(hex.charAt(i), 16);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
