package com.bloxbean.cardano.statetrees.rocksdb.mpt.gc;

import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbRootsIndex;
import org.rocksdb.*;

import java.util.*;

/**
 * Low-memory GC: persists the mark set in a temporary column family ("marks").
 */
public class OnDiskMarkSweepStrategy implements GcStrategy {
    public static final String CF_MARKS = "marks"; // base name; actual CF uses a unique suffix per run

    @Override
    public GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception {
        long start = System.currentTimeMillis();
        RocksDB db = store.db();

        // (1) Create a fresh, uniquely-named marks CF for this run
        ColumnFamilyHandle cfMarks = createUniqueMarksCf(db);

        // (2) Resolve roots to keep
        Collection<byte[]> roots = policy.resolveRoots(index);

        // (3) Optional snapshot for a consistent view during mark/sweep
        Snapshot snapshot = options.useSnapshot ? db.getSnapshot() : null;
        ReadOptions ro = new ReadOptions();
        if (snapshot != null) ro.setSnapshot(snapshot);

        try {
            long marked = mark(db, store.nodesHandle(), cfMarks, roots, ro);
            long[] totals = sweep(db, store.nodesHandle(), cfMarks, options);

            GcReport report = new GcReport();
            report.marked = marked;
            report.total = totals[0];
            report.deleted = totals[1];
            report.durationMillis = System.currentTimeMillis() - start;
            return report;
        } finally {
            if (snapshot != null) db.releaseSnapshot(snapshot);
            ro.close();
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

    private ColumnFamilyHandle createUniqueMarksCf(RocksDB db) throws RocksDBException {
        String name = CF_MARKS + "_" + Long.toUnsignedString(System.nanoTime());
        return db.createColumnFamily(new ColumnFamilyDescriptor(name.getBytes(), new ColumnFamilyOptions()));
    }

    private long mark(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfMarks, Collection<byte[]> roots, ReadOptions ro) throws RocksDBException {
        Deque<byte[]> q = new ArrayDeque<>();
        for (byte[] r : roots) if (r != null && r.length == 32) q.add(r);
        long marked = 0;
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            if (db.get(cfMarks, h) != null) continue; // already seen on disk
            db.put(cfMarks, h, ONE);
            marked++;
            byte[] enc = db.get(cfNodes, ro, h);
            if (enc == null) continue;
            for (byte[] ch : NodeRefParser.childRefs(enc)) q.addLast(ch);
        }
        return marked;
    }

    private long[] sweep(RocksDB db, ColumnFamilyHandle cfNodes, ColumnFamilyHandle cfMarks, GcOptions options) throws RocksDBException {
        long total = 0, deleted = 0;
        try (ReadOptions ro = new ReadOptions(); RocksIterator it = db.newIterator(cfNodes, ro);
             WriteOptions wo = new WriteOptions(); WriteBatch wb = new WriteBatch()) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                total++;
                byte[] k = it.key();
                if (db.get(cfMarks, k) == null) {
                    if (!options.dryRun) wb.delete(cfNodes, k);
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
        return new long[]{total, deleted};
    }

    private static final byte[] ONE = new byte[]{1};
}
