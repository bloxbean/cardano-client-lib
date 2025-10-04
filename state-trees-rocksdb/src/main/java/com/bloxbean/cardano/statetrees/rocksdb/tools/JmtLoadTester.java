package com.bloxbean.cardano.statetrees.rocksdb.tools;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStoreConfig;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RocksDB-focused load generator for the streaming JMT engine.
 *
 * <p>Example:</p>
 * <pre>
 *   ./gradlew :state-trees-rocksdb:com.bloxbean.cardano.statetrees.rocksdb.tools.JmtLoadTester.main \
 *       --args="--records=1000000 --batch=1000 --value-size=128 --rocksdb=/tmp/jmt-load \
 *                --delete-ratio=0.1 --node-cache=4096 --value-cache=8192 --proof-every=1000"
 * </pre>
 */
public final class JmtLoadTester {

    private JmtLoadTester() {
    }

    public static void main(String[] args) throws Exception {
        LoadOptions options = LoadOptions.parse(args);
        HashFunction hash = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hash);

        JellyfishMerkleTreeStoreConfig.Builder cfg = JellyfishMerkleTreeStoreConfig.builder();
        if (options.nodeCacheSize > 0) {
            cfg.enableNodeCache(true).nodeCacheSize(options.nodeCacheSize);
        }
        if (options.valueCacheSize > 0) {
            cfg.enableValueCache(true).valueCacheSize(options.valueCacheSize);
        }
        cfg.resultNodeLimit(options.resultNodeLimit);
        JellyfishMerkleTreeStoreConfig config = cfg.build();

        try (JmtStore store = options.inMemory ? new InMemoryJmtStore() : createRocksStore(options)) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, commitments, hash,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);
            runLoad(tree, store, options);
        }
    }

    private static JmtStore createRocksStore(LoadOptions options) throws Exception {
        if (Files.notExists(options.rocksDbPath)) {
            Files.createDirectories(options.rocksDbPath);
        }
        try {
            Class<?> clazz = Class.forName("com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtStore");
            // Build store Options to propagate WAL/sync flags
            Class<?> optsClass = Class.forName("com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtStore$Options");
            Object builder = optsClass.getMethod("builder").invoke(null);
            if (options.noWal) {
                builder.getClass().getMethod("disableWalForBatches", boolean.class).invoke(builder, true);
            }
            builder.getClass().getMethod("syncOnCommit", boolean.class).invoke(builder, options.syncCommit);
            Object opts = builder.getClass().getMethod("build").invoke(builder);
            return (JmtStore) clazz.getMethod("open", String.class, optsClass)
                    .invoke(null, options.rocksDbPath.toString(), opts);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("RocksDbJmtStore not found on classpath. Add the state-trees-rocksdb module.", e);
        }
    }

    private static void runLoad(JellyfishMerkleTreeStore tree, JmtStore store, LoadOptions options) {
        Random random = new SecureRandom();
        long remaining = options.totalRecords;
        long version = tree.latestVersion().orElse(0L);
        Instant start = Instant.now();
        final Instant deadline = (options.durationSeconds > 0) ? start.plusSeconds(options.durationSeconds) : null;
        long proofChecks = 0;
        long deletesIssued = 0;
        long processed = 0L;

        final boolean trackLiveKeys = options.useMix || options.deleteRatio > 0.0d;
        ArrayList<byte[]> liveKeys = trackLiveKeys ? new ArrayList<>() : null;
        Map<ByteArrayWrapper, Integer> liveIndex = trackLiveKeys ? new HashMap<>() : null;

        BufferedWriter versionLogWriter = null;
        BufferedWriter statsWriter = null;
        Instant lastStats = start;
        try {
            if (options.versionLogPath != null) {
                Path parent = options.versionLogPath.getParent();
                if (parent != null && Files.notExists(parent)) {
                    Files.createDirectories(parent);
                }
                versionLogWriter = Files.newBufferedWriter(options.versionLogPath);
                versionLogWriter.write("version,root_hex");
                versionLogWriter.newLine();
            }
            if (options.statsCsvPath != null) {
                Path parent = options.statsCsvPath.getParent();
                if (parent != null && Files.notExists(parent)) {
                    Files.createDirectories(parent);
                }
                statsWriter = Files.newBufferedWriter(options.statsCsvPath);
                statsWriter.write("ts_iso,processed_ops,ops_per_sec,deletes_total,proofs_total,latest_version,heap_mb,db_size_mb,wal_disabled,sync_commit,pending_compaction_mb,running_compactions,running_flushes,immutable_memtables,active_mem_mb,all_mem_mb");
                statsWriter.newLine();
            }

            while (true) {
                if (options.totalRecords > 0 && remaining <= 0) break;
                if (deadline != null && Instant.now().isAfter(deadline)) break;

                int batchSize = (options.totalRecords > 0)
                        ? (int) Math.min(options.batchSize, remaining)
                        : options.batchSize; // duration-only mode: honor configured batch size
                Map<byte[], byte[]> updates = new LinkedHashMap<>(batchSize);

                if (options.useMix) {
                    int deletes = (int) Math.round(batchSize * options.mixDelete);
                    int updatesCnt = (int) Math.round(batchSize * options.mixUpdate);
                    deletes = trackLiveKeys ? Math.min(deletes, liveKeys.size()) : 0;
                    updatesCnt = trackLiveKeys ? Math.min(updatesCnt, liveKeys.size()) : 0;

                    for (int i = 0; i < deletes; i++) {
                        if (liveKeys.isEmpty()) break;
                        int idx = ThreadLocalRandom.current().nextInt(liveKeys.size());
                        byte[] key = liveKeys.get(idx);
                        updates.put(key, null);
                        deletesIssued++;
                    }
                    for (int i = 0; i < updatesCnt; i++) {
                        if (liveKeys.isEmpty()) break;
                        int idx = ThreadLocalRandom.current().nextInt(liveKeys.size());
                        byte[] key = liveKeys.get(idx);
                        byte[] value = new byte[options.valueSize];
                        random.nextBytes(value);
                        updates.put(key, value);
                    }
                    while (updates.size() < batchSize) {
                        byte[] key = new byte[32];
                        random.nextBytes(key);
                        byte[] value = new byte[options.valueSize];
                        random.nextBytes(value);
                        updates.put(key, value);
                    }
                } else {
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
                }

                version++;
                tree.commit(version, updates);

                if (versionLogWriter != null) {
                    versionLogWriter.write(version + "," + toHex(tree.rootHash(version)));
                    versionLogWriter.newLine();
                }

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
                                if (liveKeys.size() < options.liveKeyCap) {
                                    liveKeys.add(entry.getKey());
                                    liveIndex.put(key, liveKeys.size() - 1);
                                } else if (!liveKeys.isEmpty()) {
                                    int replaceIdx = ThreadLocalRandom.current().nextInt(liveKeys.size());
                                    byte[] old = liveKeys.set(replaceIdx, entry.getKey());
                                    // Update index mappings
                                    liveIndex.remove(new ByteArrayWrapper(old));
                                    liveIndex.put(key, replaceIdx);
                                }
                            } else {
                                liveKeys.set(idx, entry.getKey());
                            }
                        }
                    }
                }

                boolean doProof = false;
                if (options.useMix) {
                    doProof = ThreadLocalRandom.current().nextDouble() < options.mixProof;
                } else if (options.proofEvery > 0 && (version % options.proofEvery) == 0) {
                    doProof = true;
                }
                if (doProof) {
                    Map.Entry<byte[], byte[]> sample = updates.entrySet().iterator().next();
                    tree.get(sample.getKey());
                    tree.getProof(sample.getKey(), version);
                    proofChecks++;
                }

                if (options.pruneInterval > 0 && (version % options.pruneInterval) == 0) {
                    long target = options.pruneToAbsolute;
                    if (options.pruneWindow > 0) {
                        target = Math.max(0, version - options.pruneWindow);
                    }
                    if (target >= 0 && target <= version) {
                        tree.prune(target);
                    }
                }

                remaining -= batchSize;
                processed += batchSize;
                if (options.progressPeriod > 0 && processed % options.progressPeriod == 0) {
                    Duration elapsed = Duration.between(start, Instant.now());
                    double throughput = processed / Math.max(1, elapsed.toMillis() / 1000.0);
                    System.out.printf(
                            "Progress: %,d / %,d (%.2f%%) throughput=%.0f ops/s (deletes %,d)%n",
                            processed,
                            options.totalRecords,
                            options.totalRecords > 0 ? processed * 100.0 / options.totalRecords : 0.0,
                            throughput,
                            deletesIssued);
                }

                // Periodic stats CSV
                if (statsWriter != null && options.statsPeriodSeconds > 0) {
                    Instant now = Instant.now();
                    if (Duration.between(lastStats, now).getSeconds() >= options.statsPeriodSeconds) {
                        Duration elapsed = Duration.between(start, now);
                        double seconds = Math.max(1, elapsed.toMillis() / 1000.0);
                        double opsPerSec = processed / seconds;
                        long latestVer = tree.latestVersion().orElse(-1L);
                        long heapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                        double heapMb = heapBytes / 1024.0 / 1024.0;
                        double dbMb = options.inMemory ? 0.0 : (dirSize(options.rocksDbPath) / 1024.0 / 1024.0);
                        long pendingCompMb = 0;
                        int runningComp = 0;
                        int runningFlush = 0;
                        long immMem = 0;
                        long activeMemMb = 0;
                        long allMemMb = 0;
                        if (!options.inMemory) {
                            try {
                                Class<?> clazz = Class.forName("com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtStore");
                                if (clazz.isInstance(store)) {
                                    Object props = clazz.getMethod("sampleDbProperties").invoke(store);
                                    Class<?> pClazz = props.getClass();
                                    long pendingBytes = (Long) pClazz.getMethod("pendingCompactionBytes").invoke(props);
                                    int rc = (Integer) pClazz.getMethod("runningCompactions").invoke(props);
                                    int rf = (Integer) pClazz.getMethod("runningFlushes").invoke(props);
                                    long imm = (Long) pClazz.getMethod("numImmutableMemTables").invoke(props);
                                    long activeBytes = (Long) pClazz.getMethod("curSizeActiveMemTable").invoke(props);
                                    long allBytes = (Long) pClazz.getMethod("curSizeAllMemTables").invoke(props);
                                    pendingCompMb = pendingBytes / 1024 / 1024;
                                    runningComp = rc;
                                    runningFlush = rf;
                                    immMem = imm;
                                    activeMemMb = activeBytes / 1024 / 1024;
                                    allMemMb = allBytes / 1024 / 1024;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        String line = String.format("%s,%d,%.0f,%d,%d,%d,%.2f,%.2f,%b,%b,%d,%d,%d,%d,%d",
                                java.time.ZonedDateTime.now().toString(),
                                processed, opsPerSec, deletesIssued, proofChecks, latestVer, heapMb, dbMb,
                                options.noWal, options.syncCommit,
                                pendingCompMb, runningComp, runningFlush, immMem, activeMemMb, allMemMb);
                        statsWriter.write(line);
                        statsWriter.newLine();
                        statsWriter.flush();
                        lastStats = now;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed writing version log", e);
        } finally {
            if (versionLogWriter != null) {
                try {
                    versionLogWriter.close();
                } catch (IOException ignored) {
                }
            }
            if (statsWriter != null) {
                try { statsWriter.close(); } catch (IOException ignored) { }
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double seconds = Math.max(1, elapsed.toMillis() / 1000.0);
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("==== Load Summary ====");
        // In duration mode, processed is tracked independently of remaining.
        System.out.printf("Total operations: %,d%n", processed);
        System.out.printf("Duration: %.2f s (%.0f ops/s)%n", seconds, processed / seconds);
        System.out.printf("Deletes issued: %,d%n", deletesIssued);
        System.out.printf("Proof checks: %,d%n", proofChecks);
        System.out.printf("Latest version: %d%n", tree.latestVersion().orElse(-1L));
        System.out.printf("Heap used: %.2f MB%n", usedHeap / 1024.0 / 1024.0);
    }

    private static final class LoadOptions {
        final long totalRecords;
        final int batchSize;
        final int valueSize;
        final boolean inMemory;
        final Path rocksDbPath;
        final int nodeCacheSize;
        final int valueCacheSize;
        final int resultNodeLimit;
        final long progressPeriod;
        final long proofEvery;
        final double deleteRatio;
        final Path versionLogPath;
        final boolean noWal;
        final boolean syncCommit;
        final long durationSeconds;
        final boolean useMix;
        final double mixPut;
        final double mixUpdate;
        final double mixDelete;
        final double mixProof;
        final long pruneInterval;
        final long pruneToAbsolute; // -1 when not set
        final long pruneWindow;     // rolling window; 0 when disabled
        final Path statsCsvPath;
        final long statsPeriodSeconds;
        final int liveKeyCap;

        private LoadOptions(long totalRecords,
                            int batchSize,
                            int valueSize,
                            boolean inMemory,
                            Path rocksDbPath,
                            int nodeCacheSize,
                            int valueCacheSize,
                            int resultNodeLimit,
                            long progressPeriod,
                            long proofEvery,
                            double deleteRatio,
                            Path versionLogPath,
                            boolean noWal,
                            boolean syncCommit,
                            long durationSeconds,
                            boolean useMix,
                            double mixPut,
                            double mixUpdate,
                            double mixDelete,
                            double mixProof,
                            long pruneInterval,
                            long pruneToAbsolute,
                            long pruneWindow,
                            Path statsCsvPath,
                            long statsPeriodSeconds,
                            int liveKeyCap) {
            this.totalRecords = totalRecords;
            this.batchSize = batchSize;
            this.valueSize = valueSize;
            this.inMemory = inMemory;
            this.rocksDbPath = rocksDbPath;
            this.nodeCacheSize = nodeCacheSize;
            this.valueCacheSize = valueCacheSize;
            this.resultNodeLimit = resultNodeLimit;
            this.progressPeriod = progressPeriod;
            this.proofEvery = proofEvery;
            this.deleteRatio = deleteRatio;
            this.versionLogPath = versionLogPath;
            this.noWal = noWal;
            this.syncCommit = syncCommit;
            this.durationSeconds = durationSeconds;
            this.useMix = useMix;
            this.mixPut = mixPut;
            this.mixUpdate = mixUpdate;
            this.mixDelete = mixDelete;
            this.mixProof = mixProof;
            this.pruneInterval = pruneInterval;
            this.pruneToAbsolute = pruneToAbsolute;
            this.pruneWindow = pruneWindow;
            this.statsCsvPath = statsCsvPath;
            this.statsPeriodSeconds = statsPeriodSeconds;
            this.liveKeyCap = liveKeyCap;
        }

        static LoadOptions parse(String[] args) {
            long records = 1_000_000L;
            int batch = 1_000;
            int valueSize = 128;
            boolean inMemory = false;
            Path rocksPath = Path.of("./jmt-load-db");
            int nodeCache = 0;
            int valueCache = 0;
            int resultLimit = Integer.MAX_VALUE;
            long progress = 100_000L;
            long proofEvery = 0L;
            double deleteRatio = 0.0d;
            Path versionLog = null;
            boolean noWal = false;
            boolean syncCommit = true;
            long durationSeconds = 0L;
            boolean useMix = false;
            double mixPut = 0.0, mixUpdate = 0.0, mixDelete = 0.0, mixProof = 0.0;
            long pruneInterval = 0L;
            long pruneToAbsolute = -1L;
            long pruneWindow = 0L;
            Path statsCsv = null;
            long statsPeriodSec = 0L;
            int liveKeyCap = 1_000_000; // default cap for live key pool

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
                } else if (arg.startsWith("--node-cache=")) {
                    nodeCache = Integer.parseInt(arg.substring("--node-cache=".length()));
                } else if (arg.startsWith("--value-cache=")) {
                    valueCache = Integer.parseInt(arg.substring("--value-cache=".length()));
                } else if (arg.startsWith("--result-node-limit=")) {
                    resultLimit = Integer.parseInt(arg.substring("--result-node-limit=".length()));
                } else if (arg.startsWith("--progress=")) {
                    progress = Long.parseLong(arg.substring("--progress=".length()));
                } else if (arg.startsWith("--proof-every=")) {
                    proofEvery = Long.parseLong(arg.substring("--proof-every=".length()));
                } else if (arg.startsWith("--delete-ratio=")) {
                    deleteRatio = Double.parseDouble(arg.substring("--delete-ratio=".length()));
                } else if (arg.startsWith("--version-log=")) {
                    versionLog = Path.of(arg.substring("--version-log=".length()));
                } else if (arg.equals("--no-wal")) {
                    noWal = true;
                } else if (arg.startsWith("--sync-commit=")) {
                    syncCommit = Boolean.parseBoolean(arg.substring("--sync-commit=".length()));
                } else if (arg.startsWith("--duration=")) {
                    durationSeconds = Long.parseLong(arg.substring("--duration=".length()));
                } else if (arg.startsWith("--mix=")) {
                    String spec = arg.substring("--mix=".length());
                    String[] parts = spec.split(":");
                    if (parts.length != 4) throw new IllegalArgumentException("--mix requires 4 fields: put:update:delete:proof");
                    double p = Double.parseDouble(parts[0]);
                    double u = Double.parseDouble(parts[1]);
                    double d = Double.parseDouble(parts[2]);
                    double pr = Double.parseDouble(parts[3]);
                    double sum = p + u + d + pr;
                    if (sum == 0.0) throw new IllegalArgumentException("--mix cannot all be zero");
                    if (sum > 1.0 + 1e-6) { p /= 100.0; u /= 100.0; d /= 100.0; pr /= 100.0; sum = p + u + d + pr; }
                    if (Math.abs(sum - 1.0) > 1e-6) throw new IllegalArgumentException("--mix must sum to 1.0 (or 100)");
                    useMix = true; mixPut = p; mixUpdate = u; mixDelete = d; mixProof = pr;
                } else if (arg.startsWith("--prune-interval=")) {
                    pruneInterval = Long.parseLong(arg.substring("--prune-interval=".length()));
                } else if (arg.startsWith("--prune-to=")) {
                    String v = arg.substring("--prune-to=".length());
                    if (v.startsWith("window:")) {
                        pruneWindow = Long.parseLong(v.substring("window:".length()));
                    } else {
                        pruneToAbsolute = Long.parseLong(v);
                    }
                } else if (arg.startsWith("--stats-csv=")) {
                    statsCsv = Path.of(arg.substring("--stats-csv=".length()));
                } else if (arg.startsWith("--stats-period=")) {
                    statsPeriodSec = Long.parseLong(arg.substring("--stats-period=".length()));
                } else if (arg.startsWith("--live-keys=")) {
                    liveKeyCap = Integer.parseInt(arg.substring("--live-keys=".length()));
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                }
            }

            if (!inMemory) {
                Objects.requireNonNull(rocksPath, "rocksDbPath");
            }

            if (deleteRatio < 0.0d || deleteRatio > 1.0d) {
                throw new IllegalArgumentException("--delete-ratio must be between 0.0 and 1.0");
            }

            return new LoadOptions(records,
                    batch,
                    valueSize,
                    inMemory,
                    rocksPath,
                    nodeCache,
                    valueCache,
                    resultLimit,
                    progress,
                    proofEvery,
                    deleteRatio,
                    versionLog,
                    noWal,
                    syncCommit,
                    durationSeconds,
                    useMix,
                    mixPut, mixUpdate, mixDelete, mixProof,
                    pruneInterval,
                    pruneToAbsolute,
                    pruneWindow,
                    statsCsv,
                    statsPeriodSec,
                    liveKeyCap);
        }

        private static void printUsageAndExit() {
            System.out.println("Usage: JmtLoadTester [options]\n" +
                    "  --records=N           Total operations to issue (default 1_000_000)\n" +
                    "  --duration=SEC        Run for at most SEC seconds (time-bound)\n" +
                    "  --batch=N             Updates per commit (default 1000)\n" +
                    "  --value-size=N        Value size in bytes (default 128)\n" +
                    "  --memory              Use in-memory store (default RocksDB)\n" +
                    "  --rocksdb=PATH        RocksDB directory (default ./jmt-load-db)\n" +
                    "  --node-cache=N        Node cache size (0 disables)\n" +
                    "  --value-cache=N       Value cache size (0 disables)\n" +
                    "  --result-node-limit=N CommitResult node cap (default unlimited)\n" +
                    "  --delete-ratio=F      Fraction of each batch as deletes (0-1) [deprecated if --mix is used]\n" +
                    "  --progress=N          Progress interval (default 100_000 operations)\n" +
                    "  --proof-every=N       Fetch value+proof every N commits\n" +
                    "  --mix=P:U:D:R         Mix ratios for put:update:delete:proof (sum 1.0 or 100)\n" +
                    "  --prune-interval=N    Prune every N commits (0 disables)\n" +
                    "  --prune-to=V|window:W Prune target version V or rolling window W (keep last W versions)\n" +
                    "  --stats-csv=PATH      Write periodic CSV stats (time-based)\n" +
                    "  --stats-period=SEC    Stats row period in seconds (default 0=off)\n" +
                    "  --live-keys=N         Cap for live key pool used by --mix/delete (default 1_000_000)\n" +
                    "  --version-log=PATH    Write version/root CSV to PATH\n" +
                    "  --no-wal              Disable WAL for faster ingest (unsafe)\n" +
                    "  --sync-commit=BOOL    fsync commit batches (default true)\n" +
                    "  --help                Show this message and exit");
            System.exit(0);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = digits[v >>> 4];
            hex[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(hex);
    }

    private static long dirSize(Path path) {
        if (path == null) return 0L;
        if (!Files.exists(path)) return 0L;
        try {
            final long[] total = {0L};
            Files.walk(path).filter(Files::isRegularFile).forEach(p -> {
                try { total[0] += Files.size(p); } catch (IOException ignored) { }
            });
            return total[0];
        } catch (IOException e) {
            return 0L;
        }
    }

    private static final class ByteArrayWrapper {
        private final byte[] bytes;
        private final int hash;

        private ByteArrayWrapper(byte[] bytes) {
            this.bytes = java.util.Arrays.copyOf(bytes, bytes.length);
            this.hash = java.util.Arrays.hashCode(this.bytes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ByteArrayWrapper)) return false;
            ByteArrayWrapper that = (ByteArrayWrapper) o;
            return java.util.Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
