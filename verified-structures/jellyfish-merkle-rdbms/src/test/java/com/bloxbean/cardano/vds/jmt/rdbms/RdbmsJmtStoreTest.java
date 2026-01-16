package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RdbmsJmtStore using JellyfishMerkleTree (TDD approach).
 *
 * <p>These tests mirror the RocksDbJmtStoreTest to ensure functional equivalence.
 */
class RdbmsJmtStoreTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    private DbConfig dbConfig;
    private RdbmsJmtStore store;

    @BeforeEach
    void setUp() throws Exception {
        // Use H2 in-memory database for testing
        String jdbcUrl = "jdbc:h2:mem:test_jmt_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();

        // Create schema
        createSchema(dbConfig);

        // Create store
        store = new RdbmsJmtStore(dbConfig);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    private void createSchema(DbConfig config) throws Exception {
        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            // Read schema from resources
            String schema = new String(
                getClass().getResourceAsStream("/ddl/jmt/h2/schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );

            // H2 can execute the entire script at once
            stmt.execute(schema);
        }
    }

    @Test
    void commitPersistsRootsNodesAndValues() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("alice"), bytes("100"));
        updates.put(bytes("bob"), bytes("200"));

        JellyfishMerkleTree.CommitResult v1 = tree.put(1, updates);

        // Verify root and values persisted
        assertArrayEquals(v1.rootHash(), store.rootHash(1).orElseThrow(),
            "Root hash for version 1 should match");
        assertArrayEquals(v1.rootHash(), store.latestRoot().orElseThrow().rootHash(),
            "Latest root should match version 1");

        byte[] keyHash = HASH.digest(bytes("alice"));
        byte[] persistedValue = store.getValue(keyHash).orElseThrow();
        assertArrayEquals(bytes("100"), persistedValue,
            "Persisted value for alice should be 100");

        // Verify nodes are accessible
        assertTrue(store.getNode(v1.nodes().keySet().iterator().next()).isPresent(),
            "Node should be accessible");

        // Second commit updates alice
        Map<byte[], byte[]> updates2 = new LinkedHashMap<>();
        updates2.put(bytes("alice"), bytes("150"));
        JellyfishMerkleTree.CommitResult v2 = tree.put(2, updates2);

        // Verify stale nodes tracked
        assertFalse(store.staleNodesUpTo(2).isEmpty(),
            "Stale nodes should be tracked after update");
        assertArrayEquals(v2.rootHash(), store.latestRoot().orElseThrow().rootHash(),
            "Latest root should match version 2");

        // Prune old version
        int pruned = store.pruneUpTo(2);
        assertTrue(pruned > 0, "Pruning should remove stale nodes");
        assertTrue(store.staleNodesUpTo(2).isEmpty(),
            "Stale nodes should be cleared after pruning");
    }

    @Test
    void crashMidCommitLeavesStoreUntouched() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("alice"), bytes("100"));

        // Simulate crash by starting a commit but not completing it
        long version = 1;
        byte[] aliceHash = HASH.digest(bytes("alice"));

        // Start a batch and abandon it (crash simulation)
        try (JmtStore.CommitBatch batch = store.beginCommit(version, JmtStore.CommitConfig.defaults())) {
            batch.putValue(aliceHash, bytes("100"));
            // No batch.commit() call - simulates crash
        }

        // Store should be untouched
        assertTrue(store.latestRoot().isEmpty(),
            "Root should not be published when commit fails");
        assertTrue(store.rootHash(1).isEmpty(),
            "Version root should not exist after aborted batch");
        assertTrue(store.getValue(aliceHash).isEmpty(),
            "Value writes must not leak without commit");

        // Successful commit should work normally
        JellyfishMerkleTree.CommitResult result = tree.put(version, updates);
        assertArrayEquals(result.rootHash(), store.rootHash(1).orElseThrow(),
            "Root should be persisted after successful commit");
        assertArrayEquals(bytes("100"), store.getValue(aliceHash).orElseThrow(),
            "Value should be persisted after successful commit");
    }

    @Test
    void pruneRetentionSurvivesDbRestart() throws Exception {
        byte[] root3;

        // Initial writes and prune
        {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            v1.put(bytes("bob"), bytes("200"));
            tree.put(1, v1);

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("alice"), bytes("150")); // update alice
            tree.put(2, v2);

            Map<byte[], byte[]> v3 = new LinkedHashMap<>();
            v3.put(bytes("carol"), bytes("50")); // add carol
            JellyfishMerkleTree.CommitResult c3 = tree.put(3, v3);
            root3 = c3.rootHash();

            // Prune up to version 2
            int pruned = store.pruneUpTo(2);
            assertTrue(pruned > 0, "Prune should remove stale nodes");

            assertArrayEquals(root3, store.latestRoot().orElseThrow().rootHash(),
                "Latest root should be version 3");
            assertArrayEquals(bytes("150"), store.getValue(HASH.digest(bytes("alice"))).orElseThrow(),
                "Alice's value should be 150");
        }

        // Close and reopen store (simulates restart)
        store.close();
        store = new RdbmsJmtStore(dbConfig);

        // Verify data persisted
        assertArrayEquals(root3, store.latestRoot().orElseThrow().rootHash(),
            "Latest root should survive restart");
        assertArrayEquals(bytes("150"), store.getValue(HASH.digest(bytes("alice"))).orElseThrow(),
            "Alice's value should survive restart");
        assertArrayEquals(bytes("200"), store.getValue(HASH.digest(bytes("bob"))).orElseThrow(),
            "Bob's value should survive restart");
        assertArrayEquals(bytes("50"), store.getValue(HASH.digest(bytes("carol"))).orElseThrow(),
            "Carol's value should survive restart");
    }

    @Test
    void emptyStoreReturnsEmptyLatestRoot() {
        assertTrue(store.latestRoot().isEmpty(),
            "Empty store should have no latest root");
        assertTrue(store.rootHash(1).isEmpty(),
            "Empty store should have no root for version 1");
    }

    @Test
    void getValueReturnsEmptyForNonExistentKey() {
        byte[] fakeHash = HASH.digest(bytes("nonexistent"));
        assertTrue(store.getValue(fakeHash).isEmpty(),
            "Non-existent key should return empty");
    }

    @Test
    void namespaceIsolation() {
        // Create two stores with different namespaces
        RdbmsJmtStore store1 = new RdbmsJmtStore(dbConfig, (byte) 0x01);
        RdbmsJmtStore store2 = new RdbmsJmtStore(dbConfig, (byte) 0x02);

        try {
            JellyfishMerkleTree tree1 = new JellyfishMerkleTree(store1, COMMITMENTS, HASH);
            JellyfishMerkleTree tree2 = new JellyfishMerkleTree(store2, COMMITMENTS, HASH);

            // Put different values in each namespace
            Map<byte[], byte[]> updates1 = new LinkedHashMap<>();
            updates1.put(bytes("alice"), bytes("100"));
            JellyfishMerkleTree.CommitResult v1 = tree1.put(1, updates1);

            Map<byte[], byte[]> updates2 = new LinkedHashMap<>();
            updates2.put(bytes("alice"), bytes("200"));
            JellyfishMerkleTree.CommitResult v2 = tree2.put(1, updates2);

            // Verify namespaces are isolated
            assertFalse(java.util.Arrays.equals(v1.rootHash(), v2.rootHash()),
                "Different namespaces should have different roots");

            byte[] aliceHash = HASH.digest(bytes("alice"));
            assertArrayEquals(bytes("100"), store1.getValue(aliceHash).orElseThrow(),
                "Namespace 1 should have value 100");
            assertArrayEquals(bytes("200"), store2.getValue(aliceHash).orElseThrow(),
                "Namespace 2 should have value 200");

        } finally {
            store1.close();
            store2.close();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
