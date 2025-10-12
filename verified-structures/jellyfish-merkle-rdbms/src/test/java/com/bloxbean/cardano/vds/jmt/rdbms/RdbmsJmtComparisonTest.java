package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comparison test that runs the same operations on both RDBMS and in-memory stores,
 * with detailed SQL logging to identify discrepancies.
 */
class RdbmsJmtComparisonTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);
    private static final Random RNG = new Random(0xCAFE); // Same seed as property test

    private DbConfig dbConfig;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:test_jmt_compare_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();

        // Create schema
        try (Connection conn = dbConfig.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                getClass().getResourceAsStream("/schema/jmt/h2/V1__jmt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );
            stmt.execute(schema);
        }
    }

    @Test
    void compare_rdbms_vs_memory_small_scale() throws Exception {
        int keyCount = 20;
        int versions = 3;
        int maxUpdatesPerVersion = 5;
        int queriesPerVersion = 10;

        runComparison("Small scale", keyCount, versions, maxUpdatesPerVersion, queriesPerVersion, true);
    }

    @Test
    void compare_rdbms_vs_memory_property_test_scale() throws Exception {
        // Same parameters as failing property test
        int keyCount = 150;
        int versions = 40;
        int maxUpdatesPerVersion = 12;
        int queriesPerVersion = 10; // Reduced from 30 to speed up test

        runComparison("Property test scale", keyCount, versions, maxUpdatesPerVersion, queriesPerVersion, false);
    }

    private void runComparison(String testName,
                               int keyCount,
                               int versions,
                               int maxUpdatesPerVersion,
                               int queriesPerVersion,
                               boolean debugLogging) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST: " + testName);
        System.out.println("Parameters: keys=" + keyCount + ", versions=" + versions +
                         ", maxUpdates=" + maxUpdatesPerVersion + ", queries=" + queriesPerVersion);
        System.out.println("=".repeat(80));

        List<byte[]> keyPool = generateKeys(keyCount);

        // Stores for comparison
        RdbmsJmtStore rdbmsStore = new RdbmsJmtStore(dbConfig);
        InMemoryJmtStore memoryStore = new InMemoryJmtStore();
        File rocksDbDir = new File(tempDir, "rocksdb");
        RocksDbJmtStore rocksDbStore = new RocksDbJmtStore(rocksDbDir.getAbsolutePath());

        try {
            JellyfishMerkleTree rdbmsTree = new JellyfishMerkleTree(rdbmsStore, COMMITMENTS, HASH);
            JellyfishMerkleTree memoryTree = new JellyfishMerkleTree(memoryStore, COMMITMENTS, HASH);
            JellyfishMerkleTree rocksDbTree = new JellyfishMerkleTree(rocksDbStore, COMMITMENTS, HASH);

            List<VersionSnapshot> snapshots = new ArrayList<>();

            // Run identical operations on both stores
            for (long v = 0; v < versions; v++) {
                System.out.println("\n========================================");
                System.out.println("VERSION " + v);
                System.out.println("========================================");

                Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion);

                if (debugLogging || keyCount <= 20) {
                    System.out.println("\nUpdates for version " + v + ":");
                    for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
                        byte[] keyHash = HASH.digest(entry.getKey());
                        System.out.println("  Key: " + Bytes.toHex(entry.getKey()) +
                                         " -> KeyHash: " + Bytes.toHex(keyHash) +
                                         " -> Value: " + new String(entry.getValue(), StandardCharsets.UTF_8));
                    }
                }

                JellyfishMerkleTree.CommitResult rdbmsResult = rdbmsTree.put(v, updates);
                JellyfishMerkleTree.CommitResult memoryResult = memoryTree.put(v, updates);
                JellyfishMerkleTree.CommitResult rocksDbResult = rocksDbTree.put(v, updates);

                System.out.println("\nCommit results:");
                System.out.println("  RDBMS root:   " + Bytes.toHex(rdbmsResult.rootHash()));
                System.out.println("  Memory root:  " + Bytes.toHex(memoryResult.rootHash()));
                System.out.println("  RocksDB root: " + Bytes.toHex(rocksDbResult.rootHash()));
                System.out.println("  RDBMS vs Memory: " + Arrays.equals(rdbmsResult.rootHash(), memoryResult.rootHash()));
                System.out.println("  RDBMS vs RocksDB: " + Arrays.equals(rdbmsResult.rootHash(), rocksDbResult.rootHash()));
                System.out.println("  Memory vs RocksDB: " + Arrays.equals(memoryResult.rootHash(), rocksDbResult.rootHash()));

                // Dump nodes table at version 9 BEFORE assertions for debugging
                if (debugLogging && v == 9) {
                    dumpNodesTable(v);
                    dumpStaleTable(v);

                    // Compare root node content across all three stores
                    System.out.println("\n========== ROOT NODE CONTENT COMPARISON AT VERSION 9 ==========");
                    compareRootNodeContent("RDBMS", rdbmsStore, v);
                    compareRootNodeContent("Memory", memoryStore, v);
                    compareRootNodeContent("RocksDB", rocksDbStore, v);
                    System.out.println("===============================================================");

                }

                assertArrayEquals(memoryResult.rootHash(), rdbmsResult.rootHash(),
                    "RDBMS root must match Memory at version " + v);
                assertArrayEquals(rocksDbResult.rootHash(), rdbmsResult.rootHash(),
                    "RDBMS root must match RocksDB at version " + v);

                snapshots.add(new VersionSnapshot(v, rdbmsResult.rootHash()));

                // Only dump value table for small tests
                if (keyCount <= 20) {
                    dumpValueTable(v);
                }

                // Compare queries
                System.out.println("\nQuerying " + queriesPerVersion + " random keys:");
                for (int i = 0; i < queriesPerVersion; i++) {
                    byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
                    byte[] keyHash = HASH.digest(key);

                    if (keyCount <= 20) {
                        System.out.println("\n  Query " + i + " - Key: " + Bytes.toHex(key));
                        System.out.println("           KeyHash: " + Bytes.toHex(keyHash));
                    }

                    // Get values from both stores
                    Optional<byte[]> rdbmsValue = rdbmsTree.get(key, v);
                    Optional<byte[]> memoryValue = memoryTree.get(key, v);

                    // Also check store directly
                    Optional<byte[]> rdbmsStoreValue = rdbmsStore.getValueAt(keyHash, v);
                    Optional<byte[]> memoryStoreValue = memoryStore.getValueAt(keyHash, v);

                    if (keyCount <= 20) {
                        System.out.println("    RDBMS tree.get(): " + (rdbmsValue.isPresent() ? "PRESENT" : "EMPTY"));
                        System.out.println("    Memory tree.get(): " + (memoryValue.isPresent() ? "PRESENT" : "EMPTY"));
                        System.out.println("    RDBMS store.getValueAt(): " + (rdbmsStoreValue.isPresent() ? "PRESENT" : "EMPTY"));
                        System.out.println("    Memory store.getValueAt(): " + (memoryStoreValue.isPresent() ? "PRESENT" : "EMPTY"));
                    }

                    // Check proofs
                    Optional<JmtProof> rdbmsProof = rdbmsTree.getProof(key, v);
                    Optional<JmtProof> memoryProof = memoryTree.getProof(key, v);

                    assertTrue(rdbmsProof.isPresent(), "RDBMS proof should always exist");
                    assertTrue(memoryProof.isPresent(), "Memory proof should always exist");

                    if (keyCount <= 20) {
                        System.out.println("    RDBMS proof type: " + rdbmsProof.get().type());
                        System.out.println("    Memory proof type: " + memoryProof.get().type());
                    }

                    // Assertions
                    assertEquals(memoryValue.isPresent(), rdbmsValue.isPresent(),
                        "Value presence must match between RDBMS and memory at query " + i + ", version " + v);

                    assertEquals(memoryStoreValue.isPresent(), rdbmsStoreValue.isPresent(),
                        "Store value presence must match between RDBMS and memory at query " + i + ", version " + v);

                    assertEquals(memoryProof.get().type(), rdbmsProof.get().type(),
                        "Proof types must match between RDBMS and memory at query " + i + ", version " + v);

                    if (rdbmsValue.isPresent() && memoryValue.isPresent()) {
                        assertArrayEquals(memoryValue.get(), rdbmsValue.get(),
                            "Values must match when both present");
                    }

                    // The critical assertion
                    if (rdbmsValue.isPresent()) {
                        assertEquals(JmtProof.ProofType.INCLUSION, rdbmsProof.get().type(),
                            "RDBMS: Proof should be INCLUSION when value exists at query " + i + ", version " + v);
                    }
                }
            }

            System.out.println("\n========================================");
            System.out.println("ALL VERSIONS COMPLETED SUCCESSFULLY");
            System.out.println("========================================");

        } finally {
            rdbmsStore.close();
            memoryStore.close();
            rocksDbStore.close();
        }
    }

    private void dumpValueTable(long upToVersion) throws Exception {
        System.out.println("\n  Value table contents (up to version " + upToVersion + "):");
        String sql = "SELECT namespace, key_hash, version, value_data, is_tombstone " +
                     "FROM jmt_values " +
                     "WHERE namespace = 0 AND version <= ? " +
                     "ORDER BY key_hash, version";

        try (Connection conn = dbConfig.dataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, upToVersion);

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    byte[] keyHash = rs.getBytes("key_hash");
                    long version = rs.getLong("version");
                    byte[] valueData = rs.getBytes("value_data");
                    boolean isTombstone = rs.getBoolean("is_tombstone");

                    System.out.println("    KeyHash: " + Bytes.toHex(keyHash) +
                                     ", Version: " + version +
                                     ", Value: " + (isTombstone ? "<tombstone>" :
                                                   (valueData == null ? "<null>" : new String(valueData, StandardCharsets.UTF_8))) +
                                     ", Tombstone: " + isTombstone);
                    count++;
                }
                System.out.println("  Total value records: " + count);
            }
        }
    }

    private void dumpNodesTable(long atVersion) throws Exception {
        System.out.println("\n  DEBUG: Nodes table contents at version " + atVersion + ":");
        String sql = "SELECT namespace, node_path, version, node_data " +
                     "FROM jmt_nodes " +
                     "WHERE namespace = 0 AND version <= ? " +
                     "ORDER BY node_path, version";

        try (Connection conn = dbConfig.dataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, atVersion);

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    byte[] nodePath = rs.getBytes("node_path");
                    long version = rs.getLong("version");
                    byte[] nodeData = rs.getBytes("node_data");

                    System.out.println("    Path: " + Bytes.toHex(nodePath) +
                                     ", Version: " + version +
                                     ", Data length: " + (nodeData == null ? 0 : nodeData.length));
                    count++;
                }
                System.out.println("  Total node records: " + count);
            }
        }
    }

    private void dumpStaleTable(long atVersion) throws Exception {
        System.out.println("\n  DEBUG: Stale markers table contents at version " + atVersion + ":");
        String sql = "SELECT namespace, stale_since, node_path, node_version " +
                     "FROM jmt_stale " +
                     "WHERE namespace = 0 AND stale_since <= ? " +
                     "ORDER BY stale_since, node_path, node_version";

        try (Connection conn = dbConfig.dataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, atVersion);

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    long staleSince = rs.getLong("stale_since");
                    byte[] nodePath = rs.getBytes("node_path");
                    long nodeVersion = rs.getLong("node_version");

                    System.out.println("    StaleSince: " + staleSince +
                                     ", Path: " + Bytes.toHex(nodePath) +
                                     ", NodeVersion: " + nodeVersion);
                    count++;
                }
                System.out.println("  Total stale markers: " + count);
            }
        }
    }

    private List<byte[]> generateKeys(int count) {
        List<byte[]> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String s = "jmt-key-" + i + "-" + RNG.nextInt(1_000_000);
            keys.add(s.getBytes(StandardCharsets.UTF_8));
        }
        return keys;
    }

    private Map<byte[], byte[]> randomUpdates(List<byte[]> keyPool, int maxUpdates) {
        int n = 1 + RNG.nextInt(Math.max(1, maxUpdates));
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            String val = "v-" + RNG.nextInt(100_000);
            updates.put(key, val.getBytes(StandardCharsets.UTF_8));
        }
        return updates;
    }

    private void compareRootNodeContent(String label, com.bloxbean.cardano.vds.jmt.store.JmtStore store, long version) throws Exception {
        System.out.println("\n  " + label + " root node at version " + version + ":");
        Optional<com.bloxbean.cardano.vds.jmt.store.JmtStore.NodeEntry> rootEntry =
            store.getNode(version, NibblePath.EMPTY);

        if (rootEntry.isPresent()) {
            JmtNode rootNode = rootEntry.get().node();
            System.out.println("    NodeKey: " + rootEntry.get().nodeKey());
            System.out.println("    Node type: " + rootNode.getClass().getSimpleName());
            System.out.println("    Node encoded length: " + rootNode.encode().length);
            System.out.println("    Node hash: " + Bytes.toHex(HASH.digest(rootNode.encode())));

            // Print encoded node for comparison
            byte[] encoded = rootNode.encode();
            String hex = Bytes.toHex(encoded);
            System.out.println("    Full encoding (" + hex.length() + " chars / " + encoded.length + " bytes):");
            // Print in 100-char chunks for easier comparison
            for (int i = 0; i < hex.length(); i += 100) {
                int end = Math.min(i + 100, hex.length());
                System.out.println("      [" + i + "-" + end + "]: " + hex.substring(i, end));
            }

        } else {
            System.out.println("    ROOT NODE NOT FOUND!");
        }
    }

    private static class VersionSnapshot {
        final long version;
        final byte[] rootHash;

        VersionSnapshot(long version, byte[] rootHash) {
            this.version = version;
            this.rootHash = rootHash.clone();
        }
    }
}
