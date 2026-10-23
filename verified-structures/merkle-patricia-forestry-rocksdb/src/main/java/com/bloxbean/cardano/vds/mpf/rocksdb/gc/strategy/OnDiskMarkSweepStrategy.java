package com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy;

import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcReport;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.GcRoots;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RefcountLedger;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.rocksdb.*;

import java.util.*;

/**
 * Low-memory mark-and-sweep GC: persists the mark set in a temporary column family ("marks").
 *
 * <ol>
 *   <li>With {@link GcOptions#useSnapshot} (the default) a snapshot is taken first.</li>
 *   <li>The retained roots are resolved: the policy's roots plus the LATEST root and the root at the last
 *       version (see {@link GcRoots}).</li>
 *   <li>Mark: every node reachable from a retained root is recorded in the marks column family.</li>
 *   <li>Sweep: the node keys of <em>this namespace only</em> are scanned (through the snapshot when there is
 *       one), and every unmarked node is deleted together with its refcount entry, if any. Refcount entries of
 *       kept nodes and any other keys are left alone.</li>
 * </ol>
 *
 * <p>Nodes written after the snapshot are never swept. GC is still not safe while another writer commits to the
 * same namespace (a new root may reuse an old, unmarked node), so pause writers during GC. The roots index is not
 * modified: entries of versions that are not retained stay listed, but their tries may no longer be readable.</p>
 */
public class OnDiskMarkSweepStrategy implements GcStrategy {
    public static final String CF_MARKS = "marks"; // base name; actual CF uses a unique suffix per run

    /** Digest length assumed when there is no retained root to infer it from. */
    private static final int DEFAULT_DIGEST_LENGTH = 32;

    @Override
    public GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception {
        long start = System.currentTimeMillis();
        RocksDB db = store.db();

        // (1) Snapshot first, so nothing committed after this point can be swept.
        Snapshot snapshot = options.useSnapshot ? db.getSnapshot() : null;
        ColumnFamilyHandle cfMarks = null;
        try {
            // (2) Roots to keep, and the digest length of this namespace's node keys
            List<byte[]> roots = GcRoots.retainedRoots(index, policy);
            int digestLength = GcRoots.digestLength(roots, DEFAULT_DIGEST_LENGTH);

            // (3) Fresh, uniquely-named marks CF for this run
            cfMarks = createUniqueMarksCf(db);

            KeyPrefixer keyPrefixer = store.keyPrefixer();
            long marked = mark(db, store.nodesHandle(), cfMarks, roots, keyPrefixer);
            long[] totals = sweep(db, store.nodesHandle(), cfMarks, options, keyPrefixer, snapshot, digestLength);

            GcReport report = new GcReport();
            report.marked = marked;
            report.total = totals[0];
            report.deleted = totals[1];
            report.durationMillis = System.currentTimeMillis() - start;
            return report;
        } finally {
            if (snapshot != null) db.releaseSnapshot(snapshot);
            if (cfMarks != null) {
                // Drop marks CF to reclaim space
                try {
                    db.dropColumnFamily(cfMarks);
                } catch (Exception ignored) {
                }
                try {
                    cfMarks.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private ColumnFamilyHandle createUniqueMarksCf(RocksDB db) throws RocksDBException {
        String name = CF_MARKS + "_" + Long.toUnsignedString(System.nanoTime());
        return db.createColumnFamily(new ColumnFamilyDescriptor(name.getBytes(), new ColumnFamilyOptions()));
    }

    private long mark(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfMarks, Collection<byte[]> roots,
                      KeyPrefixer keyPrefixer) throws RocksDBException {
        Deque<byte[]> q = new ArrayDeque<>(roots);
        long marked = 0;
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            if (db.get(cfMarks, h) != null) continue; // already seen on disk
            db.put(cfMarks, h, ONE);
            marked++;
            // Read the live store: marking a superset of what the snapshot holds is always safe.
            byte[] enc = db.get(cfNodes, keyPrefixer.prefix(h));
            if (enc == null) continue;
            for (byte[] ch : NodeRefParser.childRefs(enc)) q.addLast(ch);
        }
        return marked;
    }

    private long[] sweep(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfMarks, GcOptions options,
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
                    if (db.get(cfMarks, nodeHash) == null) {
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

    private static final byte[] ONE = new byte[]{1};
}
