package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.JmtProofVerifier;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style randomized tests for JMT backed by RDBMS.
 *
 * <p>The test builds multiple versioned roots by applying random put operations and validates:
 * <ul>
 *   <li>Proofs verify for random keys against each saved root at each version</li>
 *   <li>Proofs continue to verify after reopening database (persistence)</li>
 *   <li>Historical versioned reads work correctly</li>
 *   <li>At least one multi-level proof exists at the final version</li>
 *   <li>Stale node tracking is correct</li>
 * </ul>
 *
 * <p>This follows the same pattern as {@code JmtPropertyRocksDbTest} but adapted for RDBMS.
 */
class RdbmsJmtPropertyTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);
    // Using fixed seed (0xCAFE) intentionally for reproducible test data generation
    private static final Random RNG = new Random(0xCAFE); // NOSONAR - deterministic testing requires fixed seed

    private DbConfig dbConfig;
    private String jdbcUrl;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Use H2 in-memory database for testing
        jdbcUrl = "jdbc:h2:mem:test_jmt_property_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();

        // Create schema
        createSchema(dbConfig);
    }

    @AfterEach
    void tearDown() {
        // H2 in-memory database will be automatically closed
    }

    private void createSchema(DbConfig config) throws Exception {
        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            // Read schema from resources
            String schema = new String(
                getClass().getResourceAsStream("/schema/jmt/h2/V1__jmt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );

            // H2 can execute the entire script at once
            stmt.execute(schema);
        }
    }

    @Test
    void randomizedProofs_multipleVersions_persistAndVerify() throws Exception {
        int keyCount = 150;
        int versions = 30;
        int maxUpdatesPerVersion = 12;
        int queriesPerVersion = 30;

        List<byte[]> keyPool = generateKeys(keyCount);
        List<VersionSnapshot> snapshots = new ArrayList<>();

        // Phase 1: Build multiple versions with random updates
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            for (long v = 0; v < versions; v++) {
                Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion);

                JellyfishMerkleTree.CommitResult result = tree.put(v, updates);

                // Save snapshot
                VersionSnapshot snapshot = new VersionSnapshot(
                        v,
                        result.rootHash(),
                        result.nodes().size(),
                        result.staleNodes().size()
                );
                snapshots.add(snapshot);

                // Verify immediately after commit
                assertRandomProofs(tree, store, snapshot, keyPool, Math.min(10, queriesPerVersion));

                // Verify stale node count is reasonable
                assertTrue(result.staleNodes().size() <= result.nodes().size(),
                        String.format("Stale nodes (%d) should not exceed new nodes (%d) at version %d",
                                result.staleNodes().size(), result.nodes().size(), v));
            }

            // Phase 2: Full verification at final version
            VersionSnapshot latestSnapshot = snapshots.get(snapshots.size() - 1);
            assertRandomProofs(tree, store, latestSnapshot, keyPool, queriesPerVersion);

            // Verify multi-level proofs exist
            assertTrue(existsMultiLevelProof(tree, latestSnapshot.version, keyPool),
                    "Expected at least one multi-level proof at final version");

            // Phase 3: Verify historical versions (spot check)
            verifyHistoricalVersions(tree, store, snapshots, keyPool, 5);
        }

        // Phase 4: Reopen and verify persistence
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            // Verify latest version after reopen
            VersionSnapshot latestSnapshot = snapshots.get(snapshots.size() - 1);
            byte[] persistedRoot = store.rootHash(latestSnapshot.version).orElseThrow();
            assertArrayEquals(latestSnapshot.rootHash, persistedRoot,
                    "Root hash should persist after reopening");

            assertRandomProofs(tree, store, latestSnapshot, keyPool, queriesPerVersion);

            // Verify a few historical versions persist
            for (int i = 0; i < Math.min(5, snapshots.size()); i++) {
                VersionSnapshot snapshot = snapshots.get(RNG.nextInt(snapshots.size()));
                byte[] historicalRoot = store.rootHash(snapshot.version).orElseThrow();
                assertArrayEquals(snapshot.rootHash, historicalRoot,
                        "Historical root at version " + snapshot.version + " should persist");
            }
        }
    }

    @Test
    void randomizedProofs_withPruning() throws Exception {
        int keyCount = 100;
        int versions = 20;
        int maxUpdatesPerVersion = 10;
        int pruneEveryNVersions = 5;
        int keepLastNVersions = 10;

        List<byte[]> keyPool = generateKeys(keyCount);
        List<VersionSnapshot> snapshots = new ArrayList<>();

        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            for (long v = 0; v < versions; v++) {
                Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion);
                JellyfishMerkleTree.CommitResult result = tree.put(v, updates);

                snapshots.add(new VersionSnapshot(v, result.rootHash(),
                        result.nodes().size(), result.staleNodes().size()));

                // Periodic pruning
                if (v > 0 && v % pruneEveryNVersions == 0) {
                    long pruneUpTo = Math.max(0, v - keepLastNVersions);
                    int prunedCount = store.pruneUpTo(pruneUpTo);

                    // Verify pruning doesn't break recent versions
                    if (v >= keepLastNVersions) {
                        VersionSnapshot recentSnapshot = snapshots.get((int) (v - keepLastNVersions + 1));
                        assertRandomProofs(tree, store, recentSnapshot, keyPool, 5);
                    }
                }
            }

            // Verify latest version after pruning
            VersionSnapshot latestSnapshot = snapshots.get(snapshots.size() - 1);
            assertRandomProofs(tree, store, latestSnapshot, keyPool, 20);
        }
    }

    @Test
    void randomizedProofs_sqlite() throws Exception {
        File sqliteFile = tempDir.resolve("jmt-property.sqlite").toFile();
        String sqliteUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
        DbConfig sqliteConfig = DbConfig.builder()
            .simpleJdbcUrl(sqliteUrl)
            .build();

        // Create schema for SQLite
        try (Connection conn = sqliteConfig.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                getClass().getResourceAsStream("/schema/jmt/sqlite/V1__jmt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );

            String[] statements = schema.split(";");
            for (String sql : statements) {
                String cleaned = sql.replaceAll("--[^\n]*", "").trim();
                if (!cleaned.isEmpty()) {
                    stmt.execute(cleaned);
                }
            }
        }

        int keyCount = 80;
        int versions = 10;
        int maxUpdatesPerVersion = 8;
        int queriesPerVersion = 20;

        List<byte[]> keyPool = generateKeys(keyCount);
        List<VersionSnapshot> snapshots = new ArrayList<>();

        try (RdbmsJmtStore store = new RdbmsJmtStore(sqliteConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            for (long v = 0; v < versions; v++) {
                Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion);
                JellyfishMerkleTree.CommitResult result = tree.put(v, updates);

                VersionSnapshot snapshot = new VersionSnapshot(v, result.rootHash(),
                        result.nodes().size(), result.staleNodes().size());
                snapshots.add(snapshot);

                // Verify immediately
                assertRandomProofs(tree, store, snapshot, keyPool, Math.min(5, queriesPerVersion));
            }

            // Verify final version
            VersionSnapshot latestSnapshot = snapshots.get(snapshots.size() - 1);
            assertRandomProofs(tree, store, latestSnapshot, keyPool, queriesPerVersion);

            // Verify multi-level proofs exist
            assertTrue(existsMultiLevelProof(tree, latestSnapshot.version, keyPool),
                    "Expected at least one multi-level proof with SQLite backend");
        }
    }

    private static void assertRandomProofs(JellyfishMerkleTree tree,
                                          RdbmsJmtStore store,
                                          VersionSnapshot snapshot,
                                          List<byte[]> keyPool,
                                          int queries) {
        for (int i = 0; i < queries; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));

            // Get value at this version
            Optional<byte[]> valueOpt = tree.get(key, snapshot.version);
            byte[] value = valueOpt.orElse(null);

            // Get proof object
            Optional<JmtProof> proofOpt = tree.getProof(key, snapshot.version);
            assertTrue(proofOpt.isPresent(), "Proof should always be present for version " + snapshot.version);

            JmtProof proof = proofOpt.get();

            // Verify proof matches value presence
            if (value != null) {
                assertEquals(JmtProof.ProofType.INCLUSION, proof.type(),
                        "Proof type should be INCLUSION when value exists");
                assertArrayEquals(value, proof.value(),
                        "Proof value should match retrieved value");
            } else {
                assertTrue(proof.type() == JmtProof.ProofType.NON_INCLUSION_EMPTY ||
                                proof.type() == JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF,
                        "Proof type should be non-inclusion when value doesn't exist");
            }

            // Verify proof object cryptographically
            boolean valid = JmtProofVerifier.verify(
                    snapshot.rootHash,
                    key,
                    value,
                    proof,
                    HASH,
                    COMMITMENTS
            );

            if (!valid) {
                fail(buildProofMismatchMessage(snapshot, key, value, proof));
            }

            // Also test wire format proof generation and verification
            Optional<byte[]> wireOpt = tree.getProofWire(key, snapshot.version);
            assertTrue(wireOpt.isPresent(), "Wire proof should be present for version " + snapshot.version);

            byte[] wire = wireOpt.get();
            boolean wireValid = tree.verifyProofWire(
                    snapshot.rootHash,
                    key,
                    value,
                    value != null,  // including = true if value exists
                    wire
            );

            if (!wireValid) {
                fail("Wire proof verification failed at version " + snapshot.version +
                        " for key " + Bytes.toHex(key) + ", wire length=" + wire.length);
            }
        }
    }

    private static void verifyHistoricalVersions(JellyfishMerkleTree tree,
                                                 RdbmsJmtStore store,
                                                 List<VersionSnapshot> snapshots,
                                                 List<byte[]> keyPool,
                                                 int versionsToCheck) {
        int checksPerformed = 0;
        for (int i = 0; i < versionsToCheck && i < snapshots.size(); i++) {
            int idx = RNG.nextInt(snapshots.size());
            VersionSnapshot snapshot = snapshots.get(idx);

            // Pick a random key and verify at this historical version
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            Optional<byte[]> historicalValue = tree.get(key, snapshot.version);
            Optional<JmtProof> proofOpt = tree.getProof(key, snapshot.version);

            assertTrue(proofOpt.isPresent(), "Historical proof should exist for version " + snapshot.version);

            boolean valid = JmtProofVerifier.verify(
                    snapshot.rootHash,
                    key,
                    historicalValue.orElse(null),
                    proofOpt.get(),
                    HASH,
                    COMMITMENTS
            );

            assertTrue(valid, "Historical proof should verify at version " + snapshot.version);
            checksPerformed++;
        }

        assertTrue(checksPerformed > 0, "Should perform at least one historical verification");
    }

    private static boolean existsMultiLevelProof(JellyfishMerkleTree tree, long version, List<byte[]> keyPool) {
        int checks = Math.min(keyPool.size(), 80);
        for (int i = 0; i < checks; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            Optional<JmtProof> proofOpt = tree.getProof(key, version);
            if (proofOpt.isEmpty()) continue;

            JmtProof proof = proofOpt.get();
            // Multi-level proof has at least 2 branch steps
            if (proof.steps().size() >= 2) {
                return true;
            }
        }
        return false;
    }

    private static List<byte[]> generateKeys(int count) {
        List<byte[]> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String s = "jmt-key-" + i + "-" + RNG.nextInt(1_000_000);
            keys.add(s.getBytes(StandardCharsets.UTF_8));
        }
        return keys;
    }

    private static Map<byte[], byte[]> randomUpdates(List<byte[]> keyPool, int maxUpdates) {
        int n = 1 + RNG.nextInt(Math.max(1, maxUpdates));
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            String val = "v-" + RNG.nextInt(100_000);
            updates.put(key, val.getBytes(StandardCharsets.UTF_8));
        }
        return updates;
    }

    private static String buildProofMismatchMessage(VersionSnapshot snapshot,
                                                   byte[] key,
                                                   byte[] value,
                                                   JmtProof proof) {
        StringBuilder sb = new StringBuilder();
        sb.append("JMT proof verification failed\n")
          .append("  Version: ").append(snapshot.version).append("\n")
          .append("  Root hash: ").append(Bytes.toHex(snapshot.rootHash)).append("\n")
          .append("  Key: ").append(Bytes.toHex(key)).append("\n")
          .append("  Key hash: ").append(Bytes.toHex(HASH.digest(key))).append("\n")
          .append("  Value: ").append(value == null ? "<null>" : Bytes.toHex(value)).append("\n")
          .append("  Proof type: ").append(proof.type()).append("\n")
          .append("  Branch steps: ").append(proof.steps().size()).append("\n");

        if (proof.type() == JmtProof.ProofType.INCLUSION) {
            sb.append("  Proof value: ").append(Bytes.toHex(proof.value())).append("\n")
              .append("  Proof value hash: ").append(Bytes.toHex(proof.valueHash())).append("\n")
              .append("  Proof leaf key: ").append(Bytes.toHex(proof.leafKeyHash())).append("\n");
        } else if (proof.type() == JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF) {
            sb.append("  Different leaf key: ").append(Bytes.toHex(proof.conflictingKeyHash())).append("\n")
              .append("  Different leaf value hash: ").append(Bytes.toHex(proof.conflictingValueHash())).append("\n");
        }

        // Add branch step details
        for (int i = 0; i < proof.steps().size(); i++) {
            JmtProof.BranchStep step = proof.steps().get(i);
            sb.append("  Step ").append(i).append(": prefix=").append(step.prefix())
              .append(", childIndex=").append(step.childIndex())
              .append(", childHashes=").append(step.childHashes().length).append("\n");
        }

        return sb.toString();
    }

    /**
     * Snapshot of a specific version's state for verification.
     */
    private static class VersionSnapshot {
        final long version;
        final byte[] rootHash;
        final int nodeCount;
        final int staleNodeCount;

        VersionSnapshot(long version, byte[] rootHash, int nodeCount, int staleNodeCount) {
            this.version = version;
            this.rootHash = rootHash.clone();
            this.nodeCount = nodeCount;
            this.staleNodeCount = staleNodeCount;
        }
    }
}
