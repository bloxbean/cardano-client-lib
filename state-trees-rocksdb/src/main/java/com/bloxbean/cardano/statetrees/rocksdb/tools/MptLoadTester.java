package com.bloxbean.cardano.statetrees.rocksdb.tools;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.api.NodeStore;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbStateTrees;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RocksDB-focused load generator for the MPT (MPF mode via SecureTrie by default).
 *
 * <p>Example with refcount GC:</p>
 * <pre>
 *   ./gradlew :state-trees-rocksdb:MptLoadTester.main \
 *       --args="--records=1000000 --batch=1000 --value-size=128 --rocksdb=/tmp/mpt-load \
 *                --delete-ratio=0.1 --proof-every=1000 --secure \
 *                --gc=refcount --keep-latest=100"
 * </pre>
 *
 * <p>GC modes: none (default), refcount, marksweep</p>
 */
public final class MptLoadTester {

    private MptLoadTester() {}

    public static void main(String[] args) throws Exception {
        LoadOptions options = LoadOptions.parse(args);
        HashFunction hash = Blake2b256::digest;

        if (options.inMemory) {
            // In-memory mode (simple HashMap-backed NodeStore)
            NodeStore mem = new MemoryNodeStore();
            runLoad(mem, null, hash, options);
        } else {
            if (Files.notExists(options.rocksDbPath)) Files.createDirectories(options.rocksDbPath);
            // Use unified state trees manager so we can optionally record roots + refcounts
            try (RocksDbStateTrees stateTrees = new RocksDbStateTrees(options.rocksDbPath.toString())) {
                runLoad(stateTrees.nodeStore(), stateTrees, hash, options);
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                System.err.println("RocksDB JNI not available or failed to open: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    private static void runLoad(NodeStore nodeStore, RocksDbStateTrees stateTrees, HashFunction hash, LoadOptions options) {
        final boolean useSecure = options.secure;
        final boolean recordRoots = options.recordRoots && stateTrees != null;
        final boolean gcRefcount = "refcount".equalsIgnoreCase(options.gcMode);
        final boolean gcMarkSweep = "marksweep".equalsIgnoreCase(options.gcMode);

        MerklePatriciaTrie rawTrie = new MerklePatriciaTrie(nodeStore, hash);
        SecureTrie trie = useSecure ? new SecureTrie(nodeStore, hash) : null;

        Random random = new SecureRandom();
        long remaining = options.totalRecords;
        Instant start = Instant.now();
        long proofChecks = 0;
        long deletesIssued = 0;
        long versionsRecorded = 0;
        long commits = 0;
        java.util.ArrayDeque<Long> versionWindow = new java.util.ArrayDeque<>();

        // Track live keys to support deletes
        final boolean trackLiveKeys = options.deleteRatio > 0.0d;
        ArrayList<byte[]> liveKeys = trackLiveKeys ? new ArrayList<>() : null;
        Map<ByteArrayWrapper, Integer> liveIndex = trackLiveKeys ? new HashMap<>() : null;

        while (remaining > 0) {
            int batchSize = (int) Math.min(options.batchSize, remaining);

            Map<byte[], byte[]> updates = new LinkedHashMap<>(batchSize);
            int desiredDeletes = 0;
            if (trackLiveKeys) {
                desiredDeletes = (int) Math.round(batchSize * options.deleteRatio);
                desiredDeletes = Math.min(desiredDeletes, liveKeys.size());
                for (int i = 0; i < desiredDeletes; i++) {
                    int idx = ThreadLocalRandom.current().nextInt(liveKeys.size());
                    byte[] key = liveKeys.get(idx);
                    updates.put(key, null);
                    deletesIssued++;
                }
            }

            while (updates.size() < batchSize) {
                byte[] key = new byte[32];
                random.nextBytes(key);
                byte[] value = new byte[options.valueSize];
                random.nextBytes(value);
                updates.put(key, value);
            }

            // Apply updates (batch if RocksDB is used for much better throughput)
            if (stateTrees != null) {
                try (org.rocksdb.WriteBatch wb = new org.rocksdb.WriteBatch();
                     org.rocksdb.WriteOptions wo = new org.rocksdb.WriteOptions()) {
                    // Optionally disable WAL for stress tests (unsafe for durability)
                    if (options.noWal) wo.setDisableWAL(true);
                    ((RocksDbNodeStore) stateTrees.nodeStore()).withBatch(wb, () -> {
                        for (Map.Entry<byte[], byte[]> e : updates.entrySet()) {
                            byte[] key = e.getKey();
                            byte[] val = e.getValue();
                            if (useSecure) {
                                if (val == null) trie.delete(key); else trie.put(key, val);
                            } else {
                                if (val == null) rawTrie.delete(key); else rawTrie.put(key, val);
                            }
                        }
                        return null;
                    });

                    // Record root + refcount ops if requested (using same WriteBatch)
                    if (recordRoots) {
                        byte[] root = useSecure ? trie.getRootHash() : rawTrie.getRootHash();
                        if (gcRefcount && (options.refcountEvery <= 1 || (commits % options.refcountEvery) == 0)) {
                            final long[] vHolder = new long[1];
                            stateTrees.rootsIndex().withBatch(wb, () -> {
                                long v = stateTrees.rootsIndex().nextVersion();
                                stateTrees.rootsIndex().put(v, root);
                                vHolder[0] = v;
                                return null;
                            });
                            versionsRecorded = vHolder[0];
                            com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.strategy.RefcountGcStrategy.incrementAll(
                                    stateTrees.db(), stateTrees.nodeStore().nodesHandle(), stateTrees.nodeStore().nodesHandle(), root, wb, stateTrees.nodeStore().keyPrefixer());
                            versionWindow.addLast(versionsRecorded);
                            while (versionWindow.size() > options.keepLatest) {
                                Long oldVer = versionWindow.removeFirst();
                                byte[] oldRoot = stateTrees.rootsIndex().get(oldVer);
                                if (oldRoot != null) {
                                    com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.strategy.RefcountGcStrategy.decrementAll(
                                            stateTrees.db(), stateTrees.nodeStore().nodesHandle(), stateTrees.nodeStore().nodesHandle(), oldRoot, wb, stateTrees.nodeStore().keyPrefixer());
                                }
                            }
                        } else if (!gcRefcount) {
                            final long[] vHolder = new long[1];
                            stateTrees.rootsIndex().withBatch(wb, () -> {
                                long v = stateTrees.rootsIndex().nextVersion();
                                stateTrees.rootsIndex().put(v, root);
                                vHolder[0] = v;
                                return null;
                            });
                            versionsRecorded = vHolder[0];
                            versionWindow.addLast(versionsRecorded);
                            while (versionWindow.size() > options.keepLatest) versionWindow.removeFirst();
                        }
                    }
                    stateTrees.db().write(wo, wb);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to write batch to RocksDB", ex);
                }
            } else {
                for (Map.Entry<byte[], byte[]> e : updates.entrySet()) {
                    byte[] key = e.getKey();
                    byte[] val = e.getValue();
                    if (useSecure) {
                        if (val == null) trie.delete(key); else trie.put(key, val);
                    } else {
                        if (val == null) rawTrie.delete(key); else rawTrie.put(key, val);
                    }
                }
            }

            // Maintain live set
            if (trackLiveKeys) {
                for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
                    ByteArrayWrapper key = new ByteArrayWrapper(entry.getKey());
                    if (entry.getValue() == null) {
                        Integer idx = liveIndex.remove(key);
                        if (idx != null) {
                            int lastIdx = liveKeys.size() - 1;
                            byte[] last = liveKeys.remove(lastIdx);
                            if (idx < liveKeys.size()) {
                                liveKeys.set(idx, last);
                                ByteArrayWrapper lastWrap = new ByteArrayWrapper(last);
                                liveIndex.remove(lastWrap);
                                liveIndex.put(lastWrap, idx);
                            }
                        }
                    } else {
                        Integer idx = liveIndex.get(key);
                        if (idx == null) {
                            liveKeys.add(entry.getKey());
                            liveIndex.put(key, liveKeys.size() - 1);
                        } else {
                            liveKeys.set(idx, entry.getKey());
                        }
                    }
                }
            }

            // Optionally run mark-sweep GC on interval (foreground)
            commits++;
            if (gcMarkSweep && options.gcInterval > 0 && (commits % options.gcInterval) == 0 && stateTrees != null) {
                try {
                    System.out.println("Running mark-sweep GC...");
                    long gcStart = System.currentTimeMillis();
                    var gcManager = new com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.GcManager(
                            (RocksDbNodeStore) stateTrees.nodeStore(), stateTrees.rootsIndex());
                    var policy = com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.RetentionPolicy.keepLatestN(options.keepLatest);
                    var gcOpts = new com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.GcOptions();
                    gcOpts.deleteBatchSize = 20_000;
                    gcOpts.useSnapshot = true;
                    var report = gcManager.runSync(new com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.strategy.OnDiskMarkSweepStrategy(), policy, gcOpts);
                    long gcEnd = System.currentTimeMillis();
                    System.out.printf("Mark-sweep GC completed: deleted %,d nodes in %.2f s%n", report.deleted, (gcEnd - gcStart) / 1000.0);
                } catch (Exception e) {
                    throw new RuntimeException("Mark-sweep GC failed", e);
                }
            }

            // Optional proof exercise
            if (options.proofEvery > 0 && ((options.totalRecords - remaining) % options.proofEvery) == 0) {
                Map.Entry<byte[], byte[]> sample = updates.entrySet().iterator().next();
                if (useSecure) {
                    trie.get(sample.getKey());
                    trie.getProofWire(sample.getKey());
                } else {
                    rawTrie.get(sample.getKey());
                    rawTrie.getProofWire(sample.getKey());
                }
                proofChecks++;
            }

            remaining -= batchSize;
            if (options.progressPeriod > 0 && (options.totalRecords - remaining) % options.progressPeriod == 0) {
                Duration elapsed = Duration.between(start, Instant.now());
                double throughput = (options.totalRecords - remaining) / Math.max(1, elapsed.toMillis() / 1000.0);
                System.out.printf(
                        "Progress: %,d / %,d (%.2f%%) throughput=%.0f ops/s (deletes %,d)%n",
                        options.totalRecords - remaining,
                        options.totalRecords,
                        (options.totalRecords - remaining) * 100.0 / options.totalRecords,
                        throughput,
                        deletesIssued);
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double seconds = Math.max(1, elapsed.toMillis() / 1000.0);
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("==== Load Summary ====");
        System.out.printf("Total operations: %,d%n", options.totalRecords);
        System.out.printf("Duration: %.2f s (%.0f ops/s)%n", seconds, options.totalRecords / seconds);
        System.out.printf("Deletes issued: %,d%n", deletesIssued);
        System.out.printf("Proof checks: %,d%n", proofChecks);
        if (recordRoots) System.out.printf("Latest version recorded: %d%n", versionsRecorded);
        System.out.printf("Heap used: %.2f MB%n", usedHeap / 1024.0 / 1024.0);
    }

    private static final class LoadOptions {
        final long totalRecords;
        final int batchSize;
        final int valueSize;
        final boolean inMemory;
        final Path rocksDbPath;
        final long progressPeriod;
        final long proofEvery;
        final double deleteRatio;
        final boolean secure;
        final boolean recordRoots;
        final String gcMode; // none|refcount|marksweep
        final int keepLatest;
        final long gcInterval; // batches between GC runs (mark-sweep)
        final long refcountEvery; // batches between refcount increments
        final boolean noWal; // disable WAL for throughput tests

        private LoadOptions(long totalRecords,
                            int batchSize,
                            int valueSize,
                            boolean inMemory,
                            Path rocksDbPath,
                            long progressPeriod,
                            long proofEvery,
                            double deleteRatio,
                            boolean secure,
                            boolean recordRoots,
                            String gcMode,
                            int keepLatest,
                            long gcInterval,
                            long refcountEvery,
                            boolean noWal) {
            this.totalRecords = totalRecords;
            this.batchSize = batchSize;
            this.valueSize = valueSize;
            this.inMemory = inMemory;
            this.rocksDbPath = rocksDbPath;
            this.progressPeriod = progressPeriod;
            this.proofEvery = proofEvery;
            this.deleteRatio = deleteRatio;
            this.secure = secure;
            this.recordRoots = recordRoots;
            this.gcMode = gcMode;
            this.keepLatest = keepLatest;
            this.gcInterval = gcInterval;
            this.refcountEvery = refcountEvery;
            this.noWal = noWal;
        }

        static LoadOptions parse(String[] args) {
            long records = 1_000_000L;
            int batch = 1_000;
            int valueSize = 128;
            boolean inMemory = false;
            Path rocksPath = Path.of("./mpt-load-db");
            long progress = 100_000L;
            long proofEvery = 0L;
            double deleteRatio = 0.0d;
            boolean secure = true;
            boolean recordRoots = true;
            String gcMode = "none";
            int keepLatest = 1;
            long gcInterval = 0L;
            long refcountEvery = 1L;
            boolean noWal = false;

            for (String arg : args) {
                if (arg.startsWith("--records=")) {
                    records = Long.parseLong(arg.substring("--records=".length()));
                } else if (arg.startsWith("--batch=")) {
                    batch = Integer.parseInt(arg.substring("--batch=".length()));
                } else if (arg.startsWith("--value-size=")) {
                    valueSize = Integer.parseInt(arg.substring("--value-size=".length()));
                } else if (arg.equals("--memory")) {
                    inMemory = true;
                } else if (arg.startsWith("--rocksdb=")) {
                    rocksPath = Path.of(arg.substring("--rocksdb=".length()));
                } else if (arg.startsWith("--progress=")) {
                    progress = Long.parseLong(arg.substring("--progress=".length()));
                } else if (arg.startsWith("--proof-every=")) {
                    proofEvery = Long.parseLong(arg.substring("--proof-every=".length()));
                } else if (arg.startsWith("--delete-ratio=")) {
                    deleteRatio = Double.parseDouble(arg.substring("--delete-ratio=".length()));
                } else if (arg.equals("--secure")) {
                    secure = true;
                } else if (arg.equals("--plain")) {
                    secure = false;
                } else if (arg.equals("--no-roots")) {
                    recordRoots = false;
                } else if (arg.startsWith("--gc=")) {
                    gcMode = arg.substring("--gc=".length()); // none|refcount|marksweep
                } else if (arg.startsWith("--keep-latest=")) {
                    keepLatest = Integer.parseInt(arg.substring("--keep-latest=".length()));
                } else if (arg.startsWith("--gc-interval=")) {
                    gcInterval = Long.parseLong(arg.substring("--gc-interval=".length()));
                } else if (arg.startsWith("--refcount-every=")) {
                    refcountEvery = Long.parseLong(arg.substring("--refcount-every=".length()));
                } else if (arg.equals("--no-wal")) {
                    noWal = true;
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                }
            }

            if (!inMemory) Objects.requireNonNull(rocksPath, "rocksDbPath");
            if (deleteRatio < 0.0d || deleteRatio > 1.0d) throw new IllegalArgumentException("--delete-ratio must be between 0.0 and 1.0");

            return new LoadOptions(records, batch, valueSize, inMemory, rocksPath, progress, proofEvery, deleteRatio, secure, recordRoots,
                    gcMode, keepLatest, gcInterval, refcountEvery, noWal);
        }

        private static void printUsageAndExit() {
            System.out.println("Usage: MptLoadTester [options]\n" +
                    "  --records=N        Total operations (default 1_000_000)\n" +
                    "  --batch=N          Updates per batch (default 1000)\n" +
                    "  --value-size=N     Value size in bytes (default 128)\n" +
                    "  --memory           Use in-memory NodeStore (default RocksDB)\n" +
                    "  --rocksdb=PATH     RocksDB directory (default ./mpt-load-db)\n" +
                    "  --secure           Use SecureTrie (hashed keys, default)\n" +
                    "  --plain            Use plain MerklePatriciaTrie (raw keys)\n" +
                    "  --no-roots         Do not record roots/refcounts (RocksDB only)\n" +
                    "  --gc=MODE          none|refcount|marksweep (default none)\n" +
                    "  --keep-latest=N    Retention window for roots (default 1)\n" +
                    "  --gc-interval=N    Run mark-sweep every N batches (marksweep)\n" +
                    "  --refcount-every=N Increment refcounts every N batches (refcount)\n" +
                    "  --progress=N       Progress interval (default 100_000 ops)\n" +
                    "  --proof-every=N    Build value+proof every N batches\n" +
                    "  --delete-ratio=F   Fraction of deletes per batch (0-1)\n" +
                    "  --no-wal           Disable WAL for faster ingest (unsafe)\n" +
                    "  --help             Show this message and exit");
            System.exit(0);
        }
    }

    // Simple in-memory NodeStore for MPT testing
    private static final class MemoryNodeStore implements NodeStore {
        private final Map<BytesWrapper, byte[]> map = new HashMap<>();

        @Override
        public byte[] get(byte[] hash) {
            return map.get(new BytesWrapper(hash));
        }

        @Override
        public void put(byte[] hash, byte[] nodeBytes) {
            map.put(new BytesWrapper(hash), nodeBytes);
        }

        @Override
        public void delete(byte[] hash) {
            map.remove(new BytesWrapper(hash));
        }

        private static final class BytesWrapper {
            private final byte[] b;
            private final int h;
            BytesWrapper(byte[] b) { this.b = Arrays.copyOf(b, b.length); this.h = Arrays.hashCode(this.b); }
            @Override public boolean equals(Object o) { return (o instanceof BytesWrapper) && Arrays.equals(b, ((BytesWrapper) o).b); }
            @Override public int hashCode() { return h; }
        }
    }

    private static final class ByteArrayWrapper {
        private final byte[] bytes;
        private final int hash;
        private ByteArrayWrapper(byte[] bytes) { this.bytes = Arrays.copyOf(bytes, bytes.length); this.hash = Arrays.hashCode(this.bytes); }
        @Override public boolean equals(Object o) { return (o instanceof ByteArrayWrapper) && Arrays.equals(bytes, ((ByteArrayWrapper) o).bytes); }
        @Override public int hashCode() { return hash; }
    }
}
