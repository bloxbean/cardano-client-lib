package com.bloxbean.cardano.statetrees.rocksdb.gc;

import com.bloxbean.cardano.statetrees.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbRootsIndex;
import org.rocksdb.*;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Incremental refcount GC.
 * <p>
 * Usage pattern (recommended):
 * - When a new root is accepted: call incrementAll(newRoot) within your atomic WriteBatch.
 * - When a root/slot is pruned: call decrementAll(oldRoot) within the same batch, and delete nodes whose refcount hits 0.
 * <p>
 * The run() here implements retention by decrementing all roots not kept per policy.
 * It assumes increments for kept roots have been applied at insertion time.
 */
public class RefcountGcStrategy implements GcStrategy {
    public static final String CF_REFS = "refs"; // legacy name; now store refcounts as prefixed keys in nodes CF

    @Override
    public GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception {
        RocksDB db = store.db();
        ColumnFamilyHandle cfNodes = store.nodesHandle();
        ColumnFamilyHandle cfRefs = cfNodes; // store refcounts alongside nodes with a special key prefix

        // Determine versions to keep and to drop
        NavigableMap<Long, byte[]> all = index.listAll();
        Set<byte[]> keepRoots = new HashSet<>(policy.resolveRoots(index));
        List<Map.Entry<Long, byte[]>> drop = new ArrayList<>();
        for (var e : all.entrySet()) {
            if (!containsRoot(keepRoots, e.getValue())) drop.add(e);
        }

        long start = System.currentTimeMillis();
        long total = 0, deleted = 0;

        try (WriteOptions wo = new WriteOptions(); WriteBatch wb = new WriteBatch()) {
            ReadOptions ro = new ReadOptions();
            for (var e : drop) {
                // Decrement refcounts for the entire subtree of this dropped root
                long[] res = decrementAll(db, cfNodes, cfRefs, e.getValue(), wb, ro);
                total += res[0];
                deleted += res[1];
            }
            if (!options.dryRun) db.write(wo, wb);
            ro.close();
        }

        GcReport r = new GcReport();
        r.total = total;
        r.deleted = deleted;
        r.marked = -1; // not applicable here
        r.durationMillis = System.currentTimeMillis() - start;
        return r;
    }

    /**
     * Increment refcount for every node reachable from root. Call during atomic commit of a new root.
     * 
     * @param db the RocksDB instance
     * @param cfNodes the column family handle for nodes
     * @param cfRefs the column family handle for reference counts
     * @param root the root hash to start traversal from
     * @param wb the write batch for atomic operations
     * @return the number of nodes processed
     * @throws RocksDBException if a database operation fails
     */
    public static long incrementAll(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfRefs, byte[] root, WriteBatch wb) throws RocksDBException {
        if (root == null || root.length != 32) return 0;
        long touched = 0;
        Deque<byte[]> q = new ArrayDeque<>();
        q.add(root);
        Set<String> seen = new HashSet<>();
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            String key = Arrays.toString(h);
            if (!seen.add(key)) continue;
            long cnt = getRef(db, cfNodes, h);
            putRef(wb, cfNodes, h, cnt + 1);
            touched++;
            byte[] enc = db.get(cfNodes, h);
            if (enc == null) continue;
            for (byte[] ch : NodeRefParser.childRefs(enc)) q.addLast(ch);
        }
        return touched;
    }

    /**
     * Decrement refcount for every node reachable from root. Delete nodes whose refcount reaches zero.
     * 
     * @param db the RocksDB instance
     * @param cfNodes the column family handle for nodes
     * @param cfRefs the column family handle for reference counts
     * @param root the root hash to start traversal from
     * @param wb the write batch for atomic operations
     * @return array containing [nodes processed, nodes deleted]
     * @throws RocksDBException if a database operation fails
     */
    public static long[] decrementAll(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfRefs, byte[] root, WriteBatch wb) throws RocksDBException {
        return decrementAll(db, cfNodes, cfRefs, root, wb, null);
    }

    /**
     * Decrement with optional ReadOptions (for snapshot-consistent reads).
     * 
     * @param db the RocksDB instance
     * @param cfNodes the column family handle for nodes
     * @param cfRefs the column family handle for reference counts
     * @param root the root hash to start traversal from
     * @param wb the write batch for atomic operations
     * @param ro optional read options for snapshot consistency
     * @return array containing [nodes processed, nodes deleted]
     * @throws RocksDBException if a database operation fails
     */
    public static long[] decrementAll(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfRefs, byte[] root, WriteBatch wb, ReadOptions ro) throws RocksDBException {
        if (root == null || root.length != 32) return new long[]{0, 0};
        long touched = 0, deleted = 0;
        Deque<byte[]> q = new ArrayDeque<>();
        q.add(root);
        Set<String> seen = new HashSet<>();
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            String key = Arrays.toString(h);
            if (!seen.add(key)) continue;
            long cnt = getRef(db, cfNodes, h);
            long next = Math.max(0, cnt - 1);
            if (next == 0) {
                // delete count and node
                wb.delete(cfNodes, refKey(h));
                wb.delete(cfNodes, h);
                deleted++;
            } else {
                putRef(wb, cfNodes, h, next);
            }
            touched++;
            byte[] enc = (ro != null) ? db.get(cfNodes, ro, h) : db.get(cfNodes, h);
            if (enc == null) continue;
            for (byte[] ch : NodeRefParser.childRefs(enc)) q.addLast(ch);
        }
        return new long[]{touched, deleted};
    }

    private static boolean containsRoot(Set<byte[]> roots, byte[] r) {
        for (byte[] x : roots) if (Arrays.equals(x, r)) return true;
        return false;
    }

    private static long getRef(RocksDB db, ColumnFamilyHandle cfNodes, byte[] key) throws RocksDBException {
        byte[] v = db.get(cfNodes, refKey(key));
        if (v == null || v.length != 8) return 0L;
        return ByteBuffer.wrap(v).getLong();
    }

    private static void putRef(WriteBatch wb, ColumnFamilyHandle cfNodes, byte[] key, long val) throws RocksDBException {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putLong(val);
        wb.put(cfNodes, refKey(key), bb.array());
    }

    private static byte[] refKey(byte[] nodeHash) {
        byte[] out = new byte[1 + nodeHash.length];
        out[0] = (byte) 0xF0; // prefix for refcount entries
        System.arraycopy(nodeHash, 0, out, 1, nodeHash.length);
        return out;
    }
}
