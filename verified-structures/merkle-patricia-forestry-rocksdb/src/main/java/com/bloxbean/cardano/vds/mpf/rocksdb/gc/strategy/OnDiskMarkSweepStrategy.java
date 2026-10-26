package com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcReport;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.DiskMarkSet;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.GcRoots;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RefcountLedger;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Low-memory mark-and-sweep GC: persists the mark set in a temporary column family
 * ({@code __vds_gc_marks_<nanoTime>}, see {@code gc.internal.DiskMarkSet}).
 *
 * <ol>
 *   <li>With {@link GcOptions#useSnapshot} (the default) a snapshot is taken first.</li>
 *   <li>The retained roots are resolved: the policy's roots plus, when a roots index is given, the LATEST root and
 *       the root at the last version (see {@link GcRoots}).</li>
 *   <li>Mark: every node reachable from a retained root is recorded in the marks column family.</li>
 *   <li>Sweep: the node keys of <em>this namespace only</em> are scanned (through the snapshot when there is
 *       one), and every unmarked node is deleted together with its refcount entry, if any. Refcount entries of
 *       kept nodes and any other keys are left alone.</li>
 *   <li>The temporary column family is dropped. Leftovers of crashed runs ({@code __vds_gc_marks_<digits>}) are dropped the next
 *       time the database is opened by {@code RocksDbStateTrees}, {@code RocksDbNodeStore(String..)} or
 *       {@code RocksDbRootsIndex(String..)}.</li>
 * </ol>
 *
 * <p>The snapshot only protects node keys that did not exist when it was taken. Nodes are content-addressed, so a
 * writer that re-puts an existing key (or commits a root reusing an old, unmarked node) during GC can still lose
 * that node. <b>Pause every writer of the namespace while GC runs.</b> The roots index is not modified: entries of
 * versions that are not retained stay listed, but their tries may no longer be readable.</p>
 *
 * <p>The roots index may be {@code null} for a caller that records its roots elsewhere: the policy's roots, which
 * must then include the current root, are the only roots kept. Before anything is marked or deleted, the run is
 * rejected if the policy returns no root, or a root other than the all-zero empty root
 * ({@code MpfTrie.getRootHashOrEmpty()}) that has no node in this namespace.</p>
 *
 * <p>With a roots index and no retained root (for example a namespace whose roots were never recorded in the roots
 * index), every node of the namespace is deleted; a warning is logged.</p>
 */
public class OnDiskMarkSweepStrategy implements GcStrategy {
    private static final Logger log = LoggerFactory.getLogger(OnDiskMarkSweepStrategy.class);

    public static final String CF_MARKS = "marks"; // legacy constant; the mark set CF is named by DiskMarkSet.CF_PREFIX

    /** Digest length assumed when there is no retained root to infer it from. */
    private static final int DEFAULT_DIGEST_LENGTH = 32;

    @Override
    public GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception {
        long start = System.currentTimeMillis();
        RocksDB db = store.db();

        // (1) Snapshot first: keys that did not exist yet are invisible to the sweep.
        Snapshot snapshot = options.useSnapshot ? db.getSnapshot() : null;
        try {
            // (2) Roots to keep, and the digest length of this namespace's node keys
            List<byte[]> roots = GcRoots.retainedRoots(index, policy);
            if (index == null) {
                // Without an index nothing else protects the current trie: a root with no node would mark nothing,
                // and the sweep would delete the whole namespace.
                for (byte[] root : roots) {
                    if (!GcRoots.isZeroRoot(root) && store.get(root) == null) {
                        throw new IllegalArgumentException("Retained root " + HexUtil.encodeHexString(root)
                                + " has no node in this namespace");
                    }
                }
            }
            int digestLength = GcRoots.digestLength(roots, DEFAULT_DIGEST_LENGTH);
            if (roots.isEmpty()) {
                log.warn("Mark-sweep GC found no retained root: every node of this namespace will be deleted");
            }

            // (3) Mark into a fresh, uniquely-named column family; (4) sweep this namespace
            KeyPrefixer keyPrefixer = store.keyPrefixer();
            try (DiskMarkSet marks = DiskMarkSet.create(db)) {
                long marked = marks.markReachable(store.nodesHandle(), keyPrefixer, roots);
                long[] totals = sweep(db, store.nodesHandle(), marks, options, keyPrefixer, snapshot, digestLength);

                GcReport report = new GcReport();
                report.marked = marked;
                report.total = totals[0];
                report.deleted = totals[1];
                report.durationMillis = System.currentTimeMillis() - start;
                return report;
            }
        } finally {
            if (snapshot != null) db.releaseSnapshot(snapshot);
        }
    }

    private long[] sweep(RocksDB db, ColumnFamilyHandle cfNodes, DiskMarkSet marks, GcOptions options,
                         KeyPrefixer keyPrefixer, Snapshot snapshot, int digestLength) throws RocksDBException {
        long total = 0, deleted = 0;
        int nodeKeyLength = 1 + digestLength;
        try (ReadOptions ro = keyPrefixer.createNamespaceScanReadOptions()) {
            if (snapshot != null) ro.setSnapshot(snapshot);
            try (RocksIterator it = db.newIterator(cfNodes, ro);
                 WriteOptions wo = new WriteOptions();
                 WriteBatch wb = new WriteBatch()) {
                for (it.seek(keyPrefixer.namespaceStart()); it.isValid(); it.next()) {
                    byte[] prefixedKey = it.key();
                    if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) break; // left this namespace
                    if (prefixedKey.length != nodeKeyLength) continue;      // refcount or other entry
                    total++;
                    byte[] nodeHash = keyPrefixer.unprefix(prefixedKey);
                    if (!marks.contains(nodeHash)) {
                        if (!options.dryRun) {
                            wb.delete(cfNodes, prefixedKey);
                            wb.delete(cfNodes, keyPrefixer.prefix(RefcountLedger.refKey(nodeHash)));
                        }
                        deleted++;
                        if (!options.dryRun && deleted % options.deleteBatchSize == 0) {
                            db.write(wo, wb);
                            wb.clear();
                            if (options.progress != null) options.progress.accept(deleted);
                        }
                    }
                }
                if (!options.dryRun) {
                    db.write(wo, wb);
                }
            }
        }
        return new long[]{total, deleted};
    }

}
