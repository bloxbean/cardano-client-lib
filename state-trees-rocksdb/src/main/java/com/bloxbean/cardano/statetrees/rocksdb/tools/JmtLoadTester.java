package com.bloxbean.cardano.statetrees.rocksdb.tools;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStoreConfig;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
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

    private JmtLoadTester() {}

    public static void main(String[] args) throws Exception {
        LoadOptions options = LoadOptions.parse(args);
        HashFunction hash = Blake2b256::digest;
        CommitmentScheme commitments = new MpfCommitmentScheme(hash);

        JellyfishMerkleTreeStoreConfig.Builder cfg = JellyfishMerkleTreeStoreConfig.builder();
        if (options.nodeCacheSize > 0) {
            cfg.enableNodeCache(true).nodeCacheSize(options.nodeCacheSize);
        }
        if (options.valueCacheSize > 0) {
            cfg.enableValueCache(true).valueCacheSize(options.valueCacheSize);
        }
        cfg.resultNodeLimit(options.resultNodeLimit);
        JellyfishMerkleTreeStoreConfig config = cfg.build();

        try (JmtStore store = options.inMemory ? new InMemoryJmtStore() : createRocksStore(options.rocksDbPath)) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, commitments, hash,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);
            runLoad(tree, options);
        }
    }

    private static JmtStore createRocksStore(Path path) throws Exception {
        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
        try {
            Class<?> clazz = Class.forName("com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtStore");
            return (JmtStore) clazz.getConstructor(String.class).newInstance(path.toString());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("RocksDbJmtStore not found on classpath. Add the state-trees-rocksdb module.", e);
        }
    }

    private static void runLoad(JellyfishMerkleTreeStore tree, LoadOptions options) {
        Random random = new SecureRandom();
        long remaining = options.totalRecords;
        long version = tree.latestVersion().orElse(0L);
        Instant start = Instant.now();
        long proofChecks = 0;
        long deletesIssued = 0;

        final boolean trackLiveKeys = options.deleteRatio > 0.0d;
        ArrayList<byte[]> liveKeys = trackLiveKeys ? new ArrayList<>() : null;
        Map<ByteArrayWrapper, Integer> liveIndex = trackLiveKeys ? new HashMap<>() : null;

        BufferedWriter versionLogWriter = null;
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
                                liveKeys.add(entry.getKey());
                                liveIndex.put(key, liveKeys.size() - 1);
                            } else {
                                liveKeys.set(idx, entry.getKey());
                            }
                        }
                    }
                }

                if (options.proofEvery > 0 && (version % options.proofEvery) == 0) {
                    Map.Entry<byte[], byte[]> sample = updates.entrySet().iterator().next();
                    tree.get(sample.getKey());
                    tree.getProof(sample.getKey(), version);
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
        } catch (IOException e) {
            throw new RuntimeException("Failed writing version log", e);
        } finally {
            if (versionLogWriter != null) {
                try {
                    versionLogWriter.close();
                } catch (IOException ignored) {
                }
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
                            Path versionLogPath) {
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
                    versionLog);
        }

        private static void printUsageAndExit() {
            System.out.println("Usage: JmtLoadTester [options]\n" +
                    "  --records=N           Total operations to issue (default 1_000_000)\n" +
                    "  --batch=N             Updates per commit (default 1000)\n" +
                    "  --value-size=N        Value size in bytes (default 128)\n" +
                    "  --memory              Use in-memory store (default RocksDB)\n" +
                    "  --rocksdb=PATH        RocksDB directory (default ./jmt-load-db)\n" +
                    "  --node-cache=N        Node cache size (0 disables)\n" +
                    "  --value-cache=N       Value cache size (0 disables)\n" +
                    "  --result-node-limit=N CommitResult node cap (default unlimited)\n" +
                    "  --delete-ratio=F      Fraction of each batch to send as deletes (0-1)\n" +
                    "  --progress=N          Progress interval (default 100_000 operations)\n" +
                    "  --proof-every=N       Fetch value+proof every N commits\n" +
                    "  --version-log=PATH    Write version/root CSV to PATH\n" +
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
