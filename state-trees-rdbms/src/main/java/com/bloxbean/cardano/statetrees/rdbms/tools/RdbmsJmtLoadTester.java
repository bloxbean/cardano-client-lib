package com.bloxbean.cardano.statetrees.rdbms.tools;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import com.bloxbean.cardano.statetrees.rdbms.common.DbConfig;
import com.bloxbean.cardano.statetrees.rdbms.jmt.RdbmsJmtStore;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RDBMS-focused load generator for JMT V2 (Diem-style implementation).
 *
 * <p>Tests sustained write performance, proof generation, and pruning under realistic workloads
 * using H2, PostgreSQL, or SQLite backends.
 *
 * <p>Example usage:</p>
 * <pre>
 * # H2 database (default)
 * java -cp ... RdbmsJmtLoadTester --records=100000 --batch=1000 --db=h2 --path=/tmp/jmt-h2
 *
 * # PostgreSQL (requires running PostgreSQL)
 * java -cp ... RdbmsJmtLoadTester --records=100000 --batch=1000 \
 *     --jdbc-url="jdbc:postgresql://localhost/testdb?user=postgres&password=postgres"
 *
 * # SQLite
 * java -cp ... RdbmsJmtLoadTester --records=100000 --batch=1000 --db=sqlite --path=/tmp/jmt.db
 *
 * # In-memory H2 performance baseline
 * java -cp ... RdbmsJmtLoadTester --records=100000 --batch=1000 --memory
 * </pre>
 *
 * <p><b>Note:</b> JMT V2 does not support delete operations (Diem-compatible design).
 * All operations are inserts or updates to existing keys.
 */
public final class RdbmsJmtLoadTester {

    private RdbmsJmtLoadTester() {}

    public static void main(String[] args) throws Exception {
        LoadOptions options = LoadOptions.parse(args);
        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);

        if (options.inMemory) {
            // In-memory mode
            InMemoryJmtStore store = new InMemoryJmtStore();
            runLoad(store, hashFn, commitments, options);
        } else {
            // RDBMS mode
            DbConfig dbConfig;

            if (options.jdbcUrl != null) {
                // Custom JDBC URL
                dbConfig = DbConfig.builder()
                        .jdbcUrl(options.jdbcUrl, "yaci", "dbpass")
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

            try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
                runLoad(store, hashFn, commitments, options);
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
                schemaResource = "/schema/jmt/h2/V1__jmt_base_schema.sql";
                break;
            case "sqlite":
                schemaResource = "/schema/jmt/sqlite/V1__jmt_base_schema.sql";
                break;
            case "postgresql":
            case "postgres":
                schemaResource = "/schema/jmt/postgres/V1__jmt_base_schema.sql";
                break;
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }

        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                RdbmsJmtLoadTester.class.getResourceAsStream(schemaResource).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8
            );

            // H2 supports multi-statement execution, but PostgreSQL and SQLite do not
            if (dbType.equalsIgnoreCase("h2")) {
                stmt.execute(schema);
            } else {
                // Split by semicolon and execute statements individually
                String[] statements = schema.split(";");
                for (String sql : statements) {
                    // Remove SQL comments and trim whitespace
                    String cleaned = sql.replaceAll("--[^\n]*", "").trim();
                    if (!cleaned.isEmpty()) {
                        stmt.execute(cleaned);
                    }
                }
            }
        }
    }

    private static final int MAX_UPDATE_POOL = 100_000;  // Cap update key pool to 100K keys

    private static void runLoad(JmtStore store, HashFunction hashFn,
                                 CommitmentScheme commitments, LoadOptions options) {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, commitments, hashFn);
        Random random = new SecureRandom();

        long version = 0;
        long remaining = options.totalRecords;
        Instant start = Instant.now();
        long proofChecks = 0;
        long pruneOperations = 0;
        long totalPruned = 0;
        long totalCommits = 0;
        long totalInserts = 0;
        long totalUpdates = 0;

        // Track live keys to support updates (only if updateRatio > 0)
        List<byte[]> liveKeys = options.updateRatio > 0.0 ? new ArrayList<>(MAX_UPDATE_POOL) : null;
        Map<ByteArrayWrapper, Integer> liveIndex = options.updateRatio > 0.0 ? new HashMap<>(MAX_UPDATE_POOL) : null;

        // Statistics accumulators
        long totalCommitTimeMs = 0;
        long totalProofTimeMs = 0;
        long totalPruneTimeMs = 0;

        System.out.println("==== JMT V2 RDBMS Load Test ====");
        System.out.printf("Backend: %s%n", options.inMemory ? "InMemory" :
                options.jdbcUrl != null ? "RDBMS (Custom JDBC)" : options.dbType.toUpperCase());
        if (!options.inMemory && options.jdbcUrl == null) {
            System.out.printf("Database path: %s%n", options.dbPath);
        }
        System.out.printf("Total operations: %,d%n", options.totalRecords);
        System.out.printf("Batch size: %,d%n", options.batchSize);
        System.out.printf("Value size: %d bytes%n", options.valueSize);
        System.out.printf("Update ratio: %.2f%n", options.updateRatio);
        if (options.updateRatio > 0.0) {
            System.out.printf("Update pool size: %,d keys (bounded)%n", MAX_UPDATE_POOL);
        }
        if (options.pruneEvery > 0) {
            System.out.printf("Pruning: every %,d batches, keep latest %,d versions%n",
                    options.pruneEvery, options.keepLatest);
        }
        System.out.println("==========================\n");

        while (remaining > 0) {
            int batchSize = (int) Math.min(options.batchSize, remaining);
            Map<byte[], byte[]> updates = new LinkedHashMap<>(batchSize);

            // Decide how many updates vs inserts
            int numUpdates = 0;
            if (liveKeys != null && !liveKeys.isEmpty() && options.updateRatio > 0.0) {
                numUpdates = (int) Math.round(batchSize * options.updateRatio);
                numUpdates = Math.min(numUpdates, liveKeys.size());
            }

            // Generate updates to existing keys
            if (liveKeys != null) {
                for (int i = 0; i < numUpdates; i++) {
                    int idx = ThreadLocalRandom.current().nextInt(liveKeys.size());
                    byte[] existingKey = liveKeys.get(idx);
                    byte[] newValue = new byte[options.valueSize];
                    random.nextBytes(newValue);
                    updates.put(existingKey, newValue);
                    totalUpdates++;
                }
            }

            // Generate inserts for new keys
            while (updates.size() < batchSize) {
                byte[] key = new byte[32];
                random.nextBytes(key);
                byte[] value = new byte[options.valueSize];
                random.nextBytes(value);
                updates.put(key, value);
                totalInserts++;
            }

            // Commit batch
            long commitStart = System.currentTimeMillis();
            version++;
            JellyfishMerkleTree.CommitResult result = tree.put(version, updates);
            long commitElapsed = System.currentTimeMillis() - commitStart;
            totalCommitTimeMs += commitElapsed;
            totalCommits++;

            // Maintain live keys index (bounded pool)
            if (liveKeys != null && liveIndex != null) {
                for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
                    ByteArrayWrapper keyWrapper = new ByteArrayWrapper(entry.getKey());
                    Integer existingIdx = liveIndex.get(keyWrapper);
                    if (existingIdx == null) {
                        // New key - enforce pool limit
                        if (liveKeys.size() >= MAX_UPDATE_POOL) {
                            // Remove oldest 10% when limit reached
                            int removeCount = MAX_UPDATE_POOL / 10;
                            for (int i = 0; i < removeCount; i++) {
                                byte[] removedKey = liveKeys.remove(0);
                                liveIndex.remove(new ByteArrayWrapper(removedKey));
                            }
                            // Rebuild index to fix indices after removals
                            liveIndex.clear();
                            for (int i = 0; i < liveKeys.size(); i++) {
                                liveIndex.put(new ByteArrayWrapper(liveKeys.get(i)), i);
                            }
                        }
                        liveKeys.add(entry.getKey());
                        liveIndex.put(keyWrapper, liveKeys.size() - 1);
                    } else {
                        // Update existing key (already in index)
                        liveKeys.set(existingIdx, entry.getKey());
                    }
                }
            }

            // Optional proof generation exercise
            if (options.proofEvery > 0 && (totalCommits % options.proofEvery) == 0 && liveKeys != null && !liveKeys.isEmpty()) {
                byte[] sampleKey = liveKeys.get(ThreadLocalRandom.current().nextInt(liveKeys.size()));
                byte[] keyHash = hashFn.digest(sampleKey);

                long proofStart = System.currentTimeMillis();
                Optional<JmtProof> proofOpt = tree.getProof(keyHash, version);
                long proofElapsed = System.currentTimeMillis() - proofStart;
                if (proofOpt.isPresent()) {
                    totalProofTimeMs += proofElapsed;
                    proofChecks++;
                }
            }

            // Optional pruning
            if (options.pruneEvery > 0 && (totalCommits % options.pruneEvery) == 0
                    && version > options.keepLatest && store instanceof RdbmsJmtStore) {
                long pruneUpTo = version - options.keepLatest;

                long pruneStart = System.currentTimeMillis();
                int pruned = ((RdbmsJmtStore) store).pruneUpTo(pruneUpTo);
                long pruneElapsed = System.currentTimeMillis() - pruneStart;

                totalPruneTimeMs += pruneElapsed;
                totalPruned += pruned;
                pruneOperations++;
            }

            remaining -= batchSize;

            // Progress reporting
            if (options.progressPeriod > 0 && (options.totalRecords - remaining) % options.progressPeriod == 0) {
                Duration elapsed = Duration.between(start, Instant.now());
                double throughput = (options.totalRecords - remaining) / Math.max(1, elapsed.toMillis() / 1000.0);
                double avgCommitMs = totalCommitTimeMs / (double) totalCommits;

                System.out.printf(
                        "Progress: %,d / %,d (%.2f%%) | throughput=%.0f ops/s | avg_commit=%.2fms | inserts=%,d updates=%,d%n",
                        options.totalRecords - remaining,
                        options.totalRecords,
                        (options.totalRecords - remaining) * 100.0 / options.totalRecords,
                        throughput,
                        avgCommitMs,
                        totalInserts,
                        totalUpdates);
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double seconds = Math.max(1, elapsed.toMillis() / 1000.0);
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();

        // Final statistics
        System.out.println("\n==== Load Test Summary ====");
        System.out.printf("Total operations: %,d%n", options.totalRecords);
        System.out.printf("  Inserts: %,d%n", totalInserts);
        System.out.printf("  Updates: %,d%n", totalUpdates);
        System.out.printf("Duration: %.2f s (%.0f ops/s)%n", seconds, options.totalRecords / seconds);
        System.out.printf("Total commits: %,d%n", totalCommits);
        System.out.printf("Final version: %d%n", version);
        System.out.println();

        // Performance metrics
        System.out.println("==== Performance Metrics ====");
        System.out.printf("Commit latency (avg): %.2f ms%n", totalCommitTimeMs / (double) totalCommits);
        System.out.printf("Commit throughput: %.0f commits/s%n", totalCommits / seconds);
        if (proofChecks > 0) {
            System.out.printf("Proof generation (avg): %.2f ms (%,d proofs)%n",
                    totalProofTimeMs / (double) proofChecks, proofChecks);
        }
        if (pruneOperations > 0) {
            System.out.printf("Pruning (avg): %.2f ms per operation (%,d operations, %,d entries pruned)%n",
                    totalPruneTimeMs / (double) pruneOperations, pruneOperations, totalPruned);
        }
        System.out.println();

        // Memory usage
        System.out.println("==== Memory Usage ====");
        System.out.printf("Heap used: %.2f MB%n", usedHeap / 1024.0 / 1024.0);
        if (liveKeys != null) {
            System.out.printf("Live keys tracked: %,d%n", liveKeys.size());
        } else {
            System.out.println("Live keys tracked: 0 (tracking disabled, insert-only mode)");
        }

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
        final double updateRatio;
        final long pruneEvery;
        final int keepLatest;

        private LoadOptions(long totalRecords,
                            int batchSize,
                            int valueSize,
                            boolean inMemory,
                            String dbType,
                            String dbPath,
                            String jdbcUrl,
                            long progressPeriod,
                            long proofEvery,
                            double updateRatio,
                            long pruneEvery,
                            int keepLatest) {
            this.totalRecords = totalRecords;
            this.batchSize = batchSize;
            this.valueSize = valueSize;
            this.inMemory = inMemory;
            this.dbType = dbType;
            this.dbPath = dbPath;
            this.jdbcUrl = jdbcUrl;
            this.progressPeriod = progressPeriod;
            this.proofEvery = proofEvery;
            this.updateRatio = updateRatio;
            this.pruneEvery = pruneEvery;
            this.keepLatest = keepLatest;
        }

        static LoadOptions parse(String[] args) {
            long records = 1_000_000L;
            int batch = 1_000;
            int valueSize = 128;
            boolean inMemory = false;
            String dbType = "h2";
            String dbPath = "./jmt-load-db";
            String jdbcUrl = null;
            long progress = 10_000L;
            long proofEvery = 0L;
            double updateRatio = 0.2; // 20% updates by default
            long pruneEvery = 0L;     // No pruning by default
            int keepLatest = 1000;    // Keep 1000 versions when pruning

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
                } else if (arg.startsWith("--update-ratio=")) {
                    updateRatio = Double.parseDouble(arg.substring("--update-ratio=".length()));
                } else if (arg.startsWith("--prune-every=")) {
                    pruneEvery = Long.parseLong(arg.substring("--prune-every=".length()));
                } else if (arg.startsWith("--keep-latest=")) {
                    keepLatest = Integer.parseInt(arg.substring("--keep-latest=".length()));
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                }
            }

            if (updateRatio < 0.0 || updateRatio > 1.0) {
                throw new IllegalArgumentException("--update-ratio must be between 0.0 and 1.0");
            }

            return new LoadOptions(records, batch, valueSize, inMemory, dbType, dbPath, jdbcUrl,
                    progress, proofEvery, updateRatio, pruneEvery, keepLatest);
        }

        private static void printUsageAndExit() {
            System.out.println("JMT V2 RDBMS Load Tester - Performance and stress testing for Jellyfish Merkle Tree with RDBMS\n");
            System.out.println("Usage: RdbmsJmtLoadTester [options]\n");
            System.out.println("Basic Options:");
            System.out.println("  --records=N           Total operations to perform (default: 1,000,000)");
            System.out.println("  --batch=N             Updates per batch commit (default: 1000)");
            System.out.println("  --value-size=N        Value size in bytes (default: 128)");
            System.out.println("  --update-ratio=F      Fraction of updates vs inserts, 0-1 (default: 0.2)");
            System.out.println();
            System.out.println("Storage Options:");
            System.out.println("  --memory              Use in-memory store (default: RDBMS)");
            System.out.println("  --db=TYPE             Database type: h2, sqlite, postgresql (default: h2)");
            System.out.println("  --path=PATH           Database file path (default: ./jmt-load-db)");
            System.out.println("  --jdbc-url=URL        Custom JDBC URL (overrides --db and --path)");
            System.out.println();
            System.out.println("Pruning Options:");
            System.out.println("  --prune-every=N       Run pruning every N batches (default: 0 = disabled)");
            System.out.println("  --keep-latest=N       Retention window: keep N latest versions (default: 1000)");
            System.out.println();
            System.out.println("Testing & Monitoring Options:");
            System.out.println("  --progress=N          Progress reporting interval in operations (default: 10,000)");
            System.out.println("  --proof-every=N       Generate proof every N batches (default: 0 = disabled)");
            System.out.println();
            System.out.println("Examples:");
            System.out.println();
            System.out.println("  # H2 database");
            System.out.println("  RdbmsJmtLoadTester --records=100000 --batch=1000 --db=h2 --path=/tmp/jmt-h2");
            System.out.println();
            System.out.println("  # SQLite database");
            System.out.println("  RdbmsJmtLoadTester --records=100000 --batch=1000 --db=sqlite --path=/tmp/jmt.db");
            System.out.println();
            System.out.println("  # PostgreSQL with custom JDBC URL");
            System.out.println("  RdbmsJmtLoadTester --records=100000 --batch=1000 \\");
            System.out.println("      --jdbc-url=\"jdbc:postgresql://localhost/testdb?user=postgres&password=postgres\"");
            System.out.println();
            System.out.println("  # With pruning enabled");
            System.out.println("  RdbmsJmtLoadTester --records=100000 --prune-every=100 --keep-latest=1000");
            System.out.println();
            System.out.println("  # In-memory baseline");
            System.out.println("  RdbmsJmtLoadTester --records=100000 --batch=1000 --memory");
            System.out.println();
            System.out.println("Note: JMT V2 does not support delete operations (Diem-compatible design).");
            System.out.println("      All operations are inserts or updates to existing keys.");
            System.exit(0);
        }
    }

    /**
     * Wrapper for byte arrays to use as HashMap keys.
     */
    private static final class ByteArrayWrapper {
        private final byte[] bytes;
        private final int hash;

        private ByteArrayWrapper(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.hash = Arrays.hashCode(this.bytes);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof ByteArrayWrapper) && Arrays.equals(bytes, ((ByteArrayWrapper) o).bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
