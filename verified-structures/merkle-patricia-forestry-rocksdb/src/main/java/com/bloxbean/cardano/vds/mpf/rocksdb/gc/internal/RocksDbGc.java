package com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal;

import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;
import org.rocksdb.*;

import java.util.*;

public final class RocksDbGc {
    public static final class Options {
        public boolean dryRun = false;
        public int deleteBatchSize = 10_000; // tune per deployment
        public java.util.function.LongConsumer progress = null; // called with deleted count
    }

    public static final class Report {
        public long reachable;
        public long total;
        public long deleted;
    }

    /**
     * Marks reachable nodes starting from the given roots.
     */
    public static Set<String> markReachable(RocksDbNodeStore store, Collection<byte[]> rootHashes) {
        Deque<byte[]> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (byte[] r : rootHashes)
            if (r != null && r.length == 32) {
                q.add(r);
                seen.add(key(r));
            }
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            byte[] enc = store.get(h);
            if (enc == null) continue;
            for (byte[] ch : NodeRefParser.childRefs(enc)) {
                String k = key(ch);
                if (seen.add(k)) q.addLast(ch);
            }
        }
        return seen;
    }

    /**
     * Sweeps unreachable nodes from the nodes CF using a RocksDB WriteBatch for efficiency.
     */
    public static Report sweepUnreachable(RocksDbNodeStore store, Set<String> reachable, Options opts) {
        Report report = new Report();
        try (ReadOptions ro = new ReadOptions(); RocksIterator it = store.db().newIterator(store.nodesHandle(), ro)) {
            WriteOptions wopts = new WriteOptions();
            WriteBatch wb = new WriteBatch();
            long deleted = 0, total = 0;
            for (it.seekToFirst(); it.isValid(); it.next()) {
                total++;
                byte[] k = it.key();
                if (!reachable.contains(key(k))) {
                    if (!opts.dryRun) wb.delete(store.nodesHandle(), k);
                    deleted++;
                    if (!opts.dryRun && deleted % opts.deleteBatchSize == 0) {
                        store.db().write(wopts, wb);
                        wb.close();
                        wb = new WriteBatch();
                        if (opts.progress != null) opts.progress.accept(deleted);
                    }
                }
            }
            if (!opts.dryRun) {
                store.db().write(wopts, wb);
            }
            wb.close();
            wopts.close();
            report.total = total;
            report.deleted = deleted;
            report.reachable = reachable.size();
            return report;
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * GCs nodes, keeping exactly the specified versions. Use with care; ensure these versions cover all data you want to retain.
     */
    public static Report gcKeepVersions(RocksDbNodeStore store, RocksDbRootsIndex index, Collection<Long> versions, Options opts) {
        java.util.List<byte[]> roots = new ArrayList<>();
        for (Long v : versions) {
            byte[] r = index.get(v);
            if (r != null) roots.add(r);
        }
        Set<String> reachable = markReachable(store, roots);
        return sweepUnreachable(store, reachable, opts);
    }

    /**
     * Keeps the latest N versions (by version number).
     */
    public static Report gcKeepLatest(RocksDbNodeStore store, RocksDbRootsIndex index, int latestCount, Options opts) {
        NavigableMap<Long, byte[]> all = index.listAll();
        if (all.isEmpty()) return new Report();
        List<byte[]> keep = new ArrayList<>();
        int kept = 0;
        for (Map.Entry<Long, byte[]> e : all.descendingMap().entrySet()) {
            keep.add(e.getValue());
            if (++kept >= latestCount) break;
        }
        Set<String> reachable = markReachable(store, keep);
        return sweepUnreachable(store, reachable, opts);
    }

    /**
     * Starts GC in a background thread; returns the thread for optional join/cancel.
     */
    public static Thread startGcAsync(Runnable r) {
        Thread t = new Thread(r, "rocksdb-mpt-gc");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String key(byte[] h) {
        return java.util.Arrays.toString(h);
    }
}

