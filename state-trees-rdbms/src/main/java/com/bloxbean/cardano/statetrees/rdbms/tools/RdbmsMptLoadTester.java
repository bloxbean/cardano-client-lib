package com.bloxbean.cardano.statetrees.rdbms.tools;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.api.NodeStore;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.rdbms.common.DbConfig;
import com.bloxbean.cardano.statetrees.rdbms.mpt.RdbmsNodeStore;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RDBMS-focused load generator for the MPT (MPF mode via SecureTrie by default).
 *
 * <p>Tests sustained write performance and proof generation under realistic workloads
 * using H2, PostgreSQL, or SQLite backends.
 *
 * <p>Example usage:</p>
 * <pre>
 * # H2 database (default)
 * java -cp ... RdbmsMptLoadTester --records=100000 --batch=1000 --db=h2 --path=/tmp/mpt-h2
 *
 * # SQLite with deletes
 * java -cp ... RdbmsMptLoadTester --records=100000 --batch=1000 --db=sqlite \
 *     --path=/tmp/mpt.db --delete-ratio=0.1 --secure
 *
 * # PostgreSQL (requires running PostgreSQL)
 * java -cp ... RdbmsMptLoadTester --records=100000 --batch=1000 \
 *     --jdbc-url="jdbc:postgresql://localhost/testdb?user=postgres&password=postgres"
 *
 * # In-memory performance baseline
 * java -cp ... RdbmsMptLoadTester --records=100000 --batch=1000 --memory
 * </pre>
 *
 * <p><b>Note:</b> GC features (refcount, mark-sweep) are RocksDB-specific and not available in RDBMS mode.
 */
public final class RdbmsMptLoadTester {

    private RdbmsMptLoadTester() {}

    public static void main(String[] args) throws Exception {
        LoadOptions options = LoadOptions.parse(args);
        HashFunction hash = Blake2b256::digest;

        if (options.inMemory) {
            // In-memory mode
            NodeStore mem = new MemoryNodeStore();
            runLoad(mem, hash, options);
        } else {
            // RDBMS mode
            DbConfig dbConfig;

            if (options.jdbcUrl != null) {
                // Custom JDBC URL
                dbConfig = DbConfig.builder()
                    .simpleJdbcUrl(options.jdbcUrl)
                    .build();
            } else {
                // Database-specific setup
                String jdbcUrl = buildJdbcUrl(options);
                dbConfig = DbConfig.builder()
                    .simpleJdbcUrl(jdbcUrl)
                    .build();
            }

            // Create schema
            createSchema(dbConfig, options.dbType);

            try (RdbmsNodeStore nodeStore = new RdbmsNodeStore(dbConfig)) {
                runLoad(nodeStore, hash, options);
            }
        }
    }

    private static String buildJdbcUrl(LoadOptions options) {
        switch (options.dbType.toLowerCase()) {
            case "h2":
                return "jdbc:h2:" + options.dbPath + ";DB_CLOSE_DELAY=-1";
            case "sqlite":
                return "jdbc:sqlite:" + options.dbPath;
            case "postgresql":
            case "postgres":
                throw new IllegalArgumentException("PostgreSQL requires --jdbc-url parameter");
            default:
                throw new IllegalArgumentException("Unsupported database type: " + options.dbType);
        }
    }

    private static void createSchema(DbConfig config, String dbType) throws Exception {
        String schemaResource;
        switch (dbType.toLowerCase()) {
            case "h2":
                schemaResource = "/schema/mpt/h2/V1__mpt_base_schema.sql";
                break;
            case "sqlite":
                schemaResource = "/schema/mpt/sqlite/V1__mpt_base_schema.sql";
                break;
            case "postgresql":
            case "postgres":
                schemaResource = "/schema/mpt/postgres/V1__mpt_base_schema.sql";
                break;
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }

        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                RdbmsMptLoadTester.class.getResourceAsStream(schemaResource).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8
            );
            stmt.execute(schema);
        }
    }

    private static void runLoad(NodeStore nodeStore, HashFunction hash, LoadOptions options) {
        final boolean useSecure = options.secure;

        MerklePatriciaTrie rawTrie = useSecure ? null : new MerklePatriciaTrie(nodeStore, hash);
        SecureTrie trie = useSecure ? new SecureTrie(nodeStore, hash) : null;

        Random random = new SecureRandom();
        long remaining = options.totalRecords;
        Instant start = Instant.now();
        long proofChecks = 0;
        long deletesIssued = 0;
        long commits = 0;

        // Track live keys to support deletes
        final boolean trackLiveKeys = options.deleteRatio > 0.0d;
        ArrayList<byte[]> liveKeys = trackLiveKeys ? new ArrayList<>() : null;
        Map<ByteArrayWrapper, Integer> liveIndex = trackLiveKeys ? new HashMap<>() : null;

        // Statistics accumulators
        long totalCommitTimeMs = 0;
        long totalProofTimeMs = 0;

        System.out.println("==== MPT RDBMS Load Test ====");
        System.out.printf("Backend: %s%n", options.inMemory ? "InMemory" :
                options.jdbcUrl != null ? "RDBMS (Custom JDBC)" : options.dbType.toUpperCase());
        if (!options.inMemory && options.jdbcUrl == null) {
            System.out.printf("Database path: %s%n", options.dbPath);
        }
        System.out.printf("Trie mode: %s%n", useSecure ? "SecureTrie (MPF)" : "Plain MPT");
        System.out.printf("Total operations: %,d%n", options.totalRecords);
        System.out.printf("Batch size: %,d%n", options.batchSize);
        System.out.printf("Value size: %d bytes%n", options.valueSize);
        System.out.printf("Delete ratio: %.2f%n", options.deleteRatio);
        System.out.println("==========================\n");

        while (remaining > 0) {
            int batchSize = (int) Math.min(options.batchSize, remaining);

            Map<byte[], byte[]> updates = new LinkedHashMap<>(batchSize);
            int desiredDeletes = 0;
            if (trackLiveKeys && liveKeys != null && !liveKeys.isEmpty()) {
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

            // Apply updates
            // For RDBMS, use transaction callback for atomic batch operations
            long commitStart = System.currentTimeMillis();
            if (nodeStore instanceof RdbmsNodeStore) {
                RdbmsNodeStore rdbmsStore = (RdbmsNodeStore) nodeStore;
                rdbmsStore.withTransaction(() -> {
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
            } else {
                // In-memory mode
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
            long commitElapsed = System.currentTimeMillis() - commitStart;
            totalCommitTimeMs += commitElapsed;
            commits++;

            // Maintain live set
            if (trackLiveKeys && liveKeys != null && liveIndex != null) {
                for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
                    ByteArrayWrapper key = new ByteArrayWrapper(entry.getKey());
                    if (entry.getValue() == null) {
                        // Delete
                        Integer idx = liveIndex.remove(key);
                        if (idx != null && liveKeys.size() > 0) {
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
                        // Insert or update
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

            // Optional proof exercise
            if (options.proofEvery > 0 && (commits % options.proofEvery) == 0 && !updates.isEmpty()) {
                Map.Entry<byte[], byte[]> sample = updates.entrySet().iterator().next();
                long proofStart = System.currentTimeMillis();
                if (useSecure) {
                    trie.get(sample.getKey());
                    trie.getProofWire(sample.getKey());
                } else {
                    rawTrie.get(sample.getKey());
                    rawTrie.getProofWire(sample.getKey());
                }
                long proofElapsed = System.currentTimeMillis() - proofStart;
                totalProofTimeMs += proofElapsed;
                proofChecks++;
            }

            remaining -= batchSize;

            // Progress reporting
            if (options.progressPeriod > 0 && (options.totalRecords - remaining) % options.progressPeriod == 0) {
                Duration elapsed = Duration.between(start, Instant.now());
                double throughput = (options.totalRecords - remaining) / Math.max(1, elapsed.toMillis() / 1000.0);
                double avgCommitMs = totalCommitTimeMs / (double) commits;

                System.out.printf(
                        "Progress: %,d / %,d (%.2f%%) | throughput=%.0f ops/s | avg_commit=%.2fms | deletes=%,d%n",
                        options.totalRecords - remaining,
                        options.totalRecords,
                        (options.totalRecords - remaining) * 100.0 / options.totalRecords,
                        throughput,
                        avgCommitMs,
                        deletesIssued);
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double seconds = Math.max(1, elapsed.toMillis() / 1000.0);
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();

        // Final statistics
        System.out.println("\n==== Load Test Summary ====");
        System.out.printf("Total operations: %,d%n", options.totalRecords);
        System.out.printf("Duration: %.2f s (%.0f ops/s)%n", seconds, options.totalRecords / seconds);
        System.out.printf("Total commits: %,d%n", commits);
        System.out.printf("Deletes issued: %,d%n", deletesIssued);
        if (trackLiveKeys && liveKeys != null) {
            System.out.printf("Live keys remaining: %,d%n", liveKeys.size());
        }
        System.out.println();

        // Performance metrics
        System.out.println("==== Performance Metrics ====");
        System.out.printf("Commit latency (avg): %.2f ms%n", totalCommitTimeMs / (double) commits);
        System.out.printf("Commit throughput: %.0f commits/s%n", commits / seconds);
        if (proofChecks > 0) {
            System.out.printf("Proof generation (avg): %.2f ms (%,d proofs)%n",
                    totalProofTimeMs / (double) proofChecks, proofChecks);
        }
        System.out.println();

        // Memory usage
        System.out.println("==== Memory Usage ====");
        System.out.printf("Heap used: %.2f MB%n", usedHeap / 1024.0 / 1024.0);

        System.out.println("\n==== Test Complete ====");
    }

    private static final class LoadOptions {
        final long totalRecords;
        final int batchSize;
        final int valueSize;
        final boolean inMemory;
        final String dbType;
        final String dbPath;
        final String jdbcUrl;
        final long progressPeriod;
        final long proofEvery;
        final double deleteRatio;
        final boolean secure;

        private LoadOptions(long totalRecords,
                            int batchSize,
                            int valueSize,
                            boolean inMemory,
                            String dbType,
                            String dbPath,
                            String jdbcUrl,
                            long progressPeriod,
                            long proofEvery,
                            double deleteRatio,
                            boolean secure) {
            this.totalRecords = totalRecords;
            this.batchSize = batchSize;
            this.valueSize = valueSize;
            this.inMemory = inMemory;
            this.dbType = dbType;
            this.dbPath = dbPath;
            this.jdbcUrl = jdbcUrl;
            this.progressPeriod = progressPeriod;
            this.proofEvery = proofEvery;
            this.deleteRatio = deleteRatio;
            this.secure = secure;
        }

        static LoadOptions parse(String[] args) {
            long records = 1_000_000L;
            int batch = 1_000;
            int valueSize = 128;
            boolean inMemory = false;
            String dbType = "h2";
            String dbPath = "./mpt-load-db";
            String jdbcUrl = null;
            long progress = 10_000L;
            long proofEvery = 0L;
            double deleteRatio = 0.0d;
            boolean secure = true;

            for (String arg : args) {
                if (arg.startsWith("--records=")) {
                    records = Long.parseLong(arg.substring("--records=".length()));
                } else if (arg.startsWith("--batch=")) {
                    batch = Integer.parseInt(arg.substring("--batch=".length()));
                } else if (arg.startsWith("--value-size=")) {
                    valueSize = Integer.parseInt(arg.substring("--value-size=".length()));
                } else if (arg.equals("--memory")) {
                    inMemory = true;
                } else if (arg.startsWith("--db=")) {
                    dbType = arg.substring("--db=".length());
                } else if (arg.startsWith("--path=")) {
                    dbPath = arg.substring("--path=".length());
                } else if (arg.startsWith("--jdbc-url=")) {
                    jdbcUrl = arg.substring("--jdbc-url=".length());
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
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                }
            }

            if (deleteRatio < 0.0d || deleteRatio > 1.0d) {
                throw new IllegalArgumentException("--delete-ratio must be between 0.0 and 1.0");
            }

            return new LoadOptions(records, batch, valueSize, inMemory, dbType, dbPath, jdbcUrl,
                    progress, proofEvery, deleteRatio, secure);
        }

        private static void printUsageAndExit() {
            System.out.println("MPT RDBMS Load Tester - Performance and stress testing for Merkle Patricia Trie with RDBMS\n");
            System.out.println("Usage: RdbmsMptLoadTester [options]\n");
            System.out.println("Basic Options:");
            System.out.println("  --records=N           Total operations to perform (default: 1,000,000)");
            System.out.println("  --batch=N             Updates per batch (default: 1000)");
            System.out.println("  --value-size=N        Value size in bytes (default: 128)");
            System.out.println("  --delete-ratio=F      Fraction of deletes per batch, 0-1 (default: 0.0)");
            System.out.println();
            System.out.println("Storage Options:");
            System.out.println("  --memory              Use in-memory store (default: RDBMS)");
            System.out.println("  --db=TYPE             Database type: h2, sqlite, postgresql (default: h2)");
            System.out.println("  --path=PATH           Database file path (default: ./mpt-load-db)");
            System.out.println("  --jdbc-url=URL        Custom JDBC URL (overrides --db and --path)");
            System.out.println();
            System.out.println("Trie Options:");
            System.out.println("  --secure              Use SecureTrie/MPF mode with hashed keys (default)");
            System.out.println("  --plain               Use plain MerklePatriciaTrie with raw keys");
            System.out.println();
            System.out.println("Testing & Monitoring Options:");
            System.out.println("  --progress=N          Progress reporting interval in operations (default: 10,000)");
            System.out.println("  --proof-every=N       Generate proof every N batches (default: 0 = disabled)");
            System.out.println();
            System.out.println("Examples:");
            System.out.println();
            System.out.println("  # H2 database");
            System.out.println("  RdbmsMptLoadTester --records=100000 --batch=1000 --db=h2 --path=/tmp/mpt-h2");
            System.out.println();
            System.out.println("  # SQLite with deletes");
            System.out.println("  RdbmsMptLoadTester --records=100000 --batch=1000 --db=sqlite --path=/tmp/mpt.db \\");
            System.out.println("      --delete-ratio=0.1 --secure");
            System.out.println();
            System.out.println("  # PostgreSQL with custom JDBC URL");
            System.out.println("  RdbmsMptLoadTester --records=100000 --batch=1000 \\");
            System.out.println("      --jdbc-url=\"jdbc:postgresql://localhost/testdb?user=postgres&password=postgres\"");
            System.out.println();
            System.out.println("  # In-memory baseline");
            System.out.println("  RdbmsMptLoadTester --records=100000 --batch=1000 --memory");
            System.out.println();
            System.out.println("Note: GC features (refcount, mark-sweep) are RocksDB-specific and not available in RDBMS mode.");
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
