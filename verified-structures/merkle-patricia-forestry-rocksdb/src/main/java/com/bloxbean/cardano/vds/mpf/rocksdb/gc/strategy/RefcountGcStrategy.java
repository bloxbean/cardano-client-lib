package com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcReport;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.GcRoots;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RefcountLedger;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.rocksdb.*;

import java.util.*;

/**
 * Reference-counting GC for MULTI_VERSION stores.
 *
 * <p>Every version must be recorded with {@code RocksDbStateTrees.putRootWithRefcount(root)}, which adds one to
 * the count of every node of that version's trie. {@link #run} then drops each version whose root the retention
 * policy does not keep: it subtracts one along the dropped trie, deletes the nodes whose count reaches zero, and
 * removes the dropped version entries from the roots index, so running GC again never decrements twice.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Do not mix strategies on a namespace, and record every version through {@code putRootWithRefcount}.
 *       Stores whose counts were written with the pre-0.8.0 same-batch pattern (nodes and {@code incrementAll}
 *       staged in one WriteBatch) are inconsistent; use {@link OnDiskMarkSweepStrategy} for them.</li>
 *   <li>The LATEST root and the root at the last version are always kept (see {@link GcRoots}).</li>
 *   <li>Do not run GC while another thread or process writes to the same namespace.</li>
 * </ul>
 */
public class RefcountGcStrategy implements GcStrategy {
    public static final String CF_REFS = "refs"; // legacy name; refcounts are stored as prefixed keys in the nodes CF

    @Override
    public GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception {
        long start = System.currentTimeMillis();
        RocksDB db = store.db();
        ColumnFamilyHandle cfNodes = store.nodesHandle();
        KeyPrefixer keyPrefixer = store.keyPrefixer();

        List<byte[]> keep = GcRoots.retainedRoots(index, policy);
        List<Long> dropVersions = new ArrayList<>();
        List<byte[]> dropRoots = new ArrayList<>();
        for (Map.Entry<Long, byte[]> e : index.listAll().entrySet()) {
            if (!GcRoots.contains(keep, e.getValue())) {
                dropVersions.add(e.getKey());
                dropRoots.add(e.getValue());
            }
        }

        long total = 0, deleted = 0;
        if (dropRoots.isEmpty()) return report(0, 0, start);
        try (WriteOptions wo = new WriteOptions();
             WriteBatch wb = new WriteBatch();
             ReadOptions ro = new ReadOptions()) {
            RefcountLedger ledger = new RefcountLedger(db, cfNodes, keyPrefixer, wb, ro,
                    h -> db.get(cfNodes, ro, keyPrefixer.prefix(h)), null);
            for (byte[] r : keep) {
                if (!GcRoots.isZeroRoot(r) && ledger.count(r) == 0) {
                    throw new IllegalStateException("Retained root " + HexUtil.encodeHexString(r)
                            + " has no refcount: this namespace holds versions that were not recorded with "
                            + "putRootWithRefcount (or whose counts were lost). Refcount GC cannot run safely; "
                            + "use OnDiskMarkSweepStrategy instead.");
                }
            }
            for (byte[] r : dropRoots) {
                long[] res = ledger.decrement(r);
                total += res[0];
                deleted += res[1];
            }
            if (!options.dryRun) {
                boolean sameDb = index.db() == db;
                if (sameDb) {
                    // Index entries and refcounts commit atomically.
                    index.withBatch(wb, () -> {
                        for (long v : dropVersions) index.deleteVersion(v);
                        return null;
                    });
                } else {
                    // Separate databases: remove the index entries first. A crash after this only leaks nodes;
                    // the reverse order could make a re-run decrement the same versions twice.
                    for (long v : dropVersions) index.deleteVersion(v);
                }
                db.write(wo, wb);
            }
        }

        return report(total, deleted, start);
    }

    private static GcReport report(long total, long deleted, long start) {
        GcReport r = new GcReport();
        r.total = total;
        r.deleted = deleted;
        r.marked = -1; // not applicable here
        r.durationMillis = System.currentTimeMillis() - start;
        return r;
    }

    /**
     * Adds one to the refcount of every node reachable from {@code root}, staged into {@code wb}.
     *
     * <p>Counts are read from the database, so the trie nodes must already be committed. Prefer
     * {@code RocksDbStateTrees.putRootWithRefcount(root)}, which also works inside
     * {@code nodeStore.withBatch(...)} / {@code RocksDbMptSession.write(...)} where the nodes are only staged.</p>
     *
     * @param db          the RocksDB instance
     * @param cfNodes     the column family handle for nodes
     * @param cfRefs      the column family handle for reference counts (unused; counts live in {@code cfNodes})
     * @param root        the root hash to start traversal from
     * @param wb          the write batch for atomic operations
     * @param keyPrefixer the key prefixer for namespace support
     * @return the number of nodes processed
     * @throws IllegalStateException if the root node is not committed
     * @throws RocksDBException      if a database operation fails
     */
    public static long incrementAll(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfRefs, byte[] root, WriteBatch wb, KeyPrefixer keyPrefixer) throws RocksDBException {
        return new RefcountLedger(db, cfNodes, keyPrefixer, wb, null,
                h -> db.get(cfNodes, keyPrefixer.prefix(h)), null).increment(root);
    }

    /**
     * Subtracts one from the refcount of every tracked node reachable from {@code root} and deletes the nodes
     * whose count goes from 1 to 0. Untracked nodes are skipped together with their subtrees.
     *
     * @param db          the RocksDB instance
     * @param cfNodes     the column family handle for nodes
     * @param cfRefs      the column family handle for reference counts (unused; counts live in {@code cfNodes})
     * @param root        the root hash to start traversal from
     * @param wb          the write batch for atomic operations
     * @param keyPrefixer the key prefixer for namespace support
     * @return array containing [nodes processed, nodes deleted]
     * @throws RocksDBException if a database operation fails
     */
    public static long[] decrementAll(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfRefs, byte[] root, WriteBatch wb, KeyPrefixer keyPrefixer) throws RocksDBException {
        return decrementAll(db, cfNodes, cfRefs, root, wb, null, keyPrefixer);
    }

    /**
     * Decrement with optional ReadOptions (for snapshot-consistent reads).
     *
     * @param db          the RocksDB instance
     * @param cfNodes     the column family handle for nodes
     * @param cfRefs      the column family handle for reference counts (unused; counts live in {@code cfNodes})
     * @param root        the root hash to start traversal from
     * @param wb          the write batch for atomic operations
     * @param ro          optional read options for snapshot consistency
     * @param keyPrefixer the key prefixer for namespace support
     * @return array containing [nodes processed, nodes deleted]
     * @throws RocksDBException if a database operation fails
     */
    public static long[] decrementAll(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfRefs, byte[] root, WriteBatch wb, ReadOptions ro, KeyPrefixer keyPrefixer) throws RocksDBException {
        return new RefcountLedger(db, cfNodes, keyPrefixer, wb, ro,
                h -> ro != null ? db.get(cfNodes, ro, keyPrefixer.prefix(h)) : db.get(cfNodes, keyPrefixer.prefix(h)),
                null).decrement(root);
    }
}
