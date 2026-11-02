package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Assertions shared by the GC tests: a version is intact only if every node is present and every value matches. */
final class GcTestSupport {

    private GcTestSupport() {
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Walks {@code root} and returns {present, missing} node counts. */
    static long[] reach(RocksDbNodeStore store, byte[] root) {
        long present = 0, missing = 0;
        Deque<byte[]> q = new ArrayDeque<>(List.of(root));
        Set<String> seen = new HashSet<>();
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            if (!seen.add(HexUtil.encodeHexString(h))) continue;
            byte[] enc = store.get(h);
            if (enc == null) {
                missing++;
                continue;
            }
            present++;
            q.addAll(NodeRefParser.childRefs(enc));
        }
        return new long[]{present, missing};
    }

    /** Asserts that the trie at {@code root} has no missing node and holds exactly {@code model}. */
    static void assertIntact(RocksDbNodeStore store, byte[] root, Map<String, byte[]> model, String what) {
        long[] r = reach(store, root);
        assertEquals(0, r[1], what + ": missing nodes under root " + HexUtil.encodeHexString(root));
        MpfTrie trie = new MpfTrie(store, root);
        for (Map.Entry<String, byte[]> e : model.entrySet()) {
            assertArrayEquals(e.getValue(), trie.get(HexUtil.decodeHexString(e.getKey())), what + ": key " + e.getKey());
        }
        assertEquals(model.size(), trie.getAllEntries().size(), what + ": entry count");
    }

    /** Counts node keys (prefix byte + 32-byte hash) of the store's namespace. */
    static long countNodeKeys(RocksDbNodeStore store) {
        return countKeys(store, 33);
    }

    /** Counts refcount keys ([ns][0xF0][hash]) of the store's namespace. */
    static long countRefcountKeys(RocksDbNodeStore store) {
        return countKeys(store, 34);
    }

    private static long countKeys(RocksDbNodeStore store, int length) {
        KeyPrefixer p = store.keyPrefixer();
        long cnt = 0;
        try (ReadOptions ro = p.createNamespaceScanReadOptions(); RocksIterator it = store.db().newIterator(store.nodesHandle(), ro)) {
            for (it.seek(p.namespaceStart()); it.isValid(); it.next()) {
                if (!p.hasCorrectPrefix(it.key())) break;
                if (it.key().length == length) cnt++;
            }
        }
        return cnt;
    }

    static String hex(String s) {
        return HexUtil.encodeHexString(b(s));
    }
}
