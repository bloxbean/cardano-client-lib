package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtLeafNode;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.NodeKey;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityChecker;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityMode;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatDescriptor;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatMismatchException;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtWriteConflictException;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);

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
    void latestReplayAndPruneCannotDeleteLiveNodes() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("alice"), bytes("100"));
        updates.put(bytes("bob"), bytes("200"));
        byte[] root = tree.put(1L, updates).rootHash();

        JellyfishMerkleTree.CommitResult replay = tree.put(1L, updates);
        assertArrayEquals(root, replay.rootHash());
        assertTrue(replay.staleNodes().stream().noneMatch(replay.nodes()::containsKey));

        store.pruneUpTo(1L);
        assertTrue(tree.verifyProofWire(root, bytes("alice"), bytes("100"), true,
                tree.getProofWire(bytes("alice"), 1L).orElseThrow()));
        assertTrue(new JmtIntegrityChecker(store, JmtProfile.classicBlake2b256V1())
                .check(JmtIntegrityMode.FULL).healthy());
    }

    @Test
    void rawCommitReplayCannotMutateCommittedValuesOrRegressLatest() {
        new JellyfishMerkleTree(store);
        byte[] keyHash = HASH.digest(bytes("alice"));
        byte[] root1 = root(1);
        byte[] root2 = root(2);
        commitValue(store, 1L, root1, keyHash, bytes("100"));
        commitValue(store, 2L, root2, keyHash, bytes("200"));

        try (JmtStore.CommitBatch historical = store.beginCommit(
                1L, JmtStore.CommitConfig.defaults())) {
            historical.putValue(keyHash, bytes("stale"));
            historical.setRootHash(root1);
            assertThrows(JmtWriteConflictException.class, historical::commit);
        }
        try (JmtStore.CommitBatch replay = store.beginCommit(
                2L, JmtStore.CommitConfig.defaults())) {
            replay.putValue(keyHash, bytes("conflict"));
            replay.setRootHash(root2);
            assertDoesNotThrow(replay::commit);
        }

        assertEquals(2L, store.latestRoot().orElseThrow().version());
        assertArrayEquals(root2, store.latestRoot().orElseThrow().rootHash());
        assertArrayEquals(bytes("200"), store.getValue(keyHash).orElseThrow());
        assertArrayEquals(bytes("100"), store.getValueAt(keyHash, 1L).orElseThrow());
    }

    @Test
    void rawNewVersionCannotOverwriteAnExistingNodeKeyOnH2() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        JellyfishMerkleTree.CommitResult committed = tree.put(
                1L, Map.of(bytes("alice"), bytes("100")));
        NodeKey existingKey = committed.nodes().keySet().iterator().next();
        byte[] originalNode = store.getNode(existingKey).orElseThrow().encode();

        try (JmtStore.CommitBatch collision = store.beginCommit(
                2L, JmtStore.CommitConfig.defaults())) {
            collision.putNode(existingKey, JmtLeafNode.of(root(7), root(8)));
            collision.setRootHash(root(2));
            assertThrows(RuntimeException.class, collision::commit);
        }

        assertTrue(store.rootHash(2L).isEmpty());
        assertEquals(1L, store.latestRoot().orElseThrow().version());
        assertArrayEquals(originalNode, store.getNode(existingKey).orElseThrow().encode());
    }

    @Test
    void storageCasRejectsOneOfTwoCrossInstanceWritersFromTheSameBase() throws Exception {
        store.close();
        store = null;
        CountDownLatch commitsReady = new CountDownLatch(2);
        CountDownLatch releaseCommits = new CountDownLatch(1);

        try (BarrierRdbmsJmtStore first = new BarrierRdbmsJmtStore(
                     dbConfig, commitsReady, releaseCommits);
             BarrierRdbmsJmtStore second = new BarrierRdbmsJmtStore(
                     dbConfig, commitsReady, releaseCommits)) {
            JellyfishMerkleTree firstTree = new JellyfishMerkleTree(first);
            JellyfishMerkleTree secondTree = new JellyfishMerkleTree(second);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<JellyfishMerkleTree.CommitResult> firstCommit = executor.submit(
                        () -> firstTree.put(1L, Map.of(bytes("alice"), bytes("100"))));
                Future<JellyfishMerkleTree.CommitResult> secondCommit = executor.submit(
                        () -> secondTree.put(2L, Map.of(bytes("bob"), bytes("200"))));

                assertTrue(commitsReady.await(10, TimeUnit.SECONDS));
                releaseCommits.countDown();

                int successes = 0;
                int conflicts = 0;
                for (Future<JellyfishMerkleTree.CommitResult> commit
                        : List.of(firstCommit, secondCommit)) {
                    try {
                        commit.get(10, TimeUnit.SECONDS);
                        successes++;
                    } catch (ExecutionException e) {
                        if (hasWriteConflict(e.getCause())) {
                            conflicts++;
                        } else {
                            throw e;
                        }
                    }
                }

                assertEquals(1, successes);
                assertEquals(1, conflicts);
                assertTrue(first.rootHash(1L).isPresent() ^ first.rootHash(2L).isPresent());
                assertTrue(new JmtIntegrityChecker(first, JmtProfile.classicBlake2b256V1())
                        .check(JmtIntegrityMode.FULL).healthy());
            } finally {
                releaseCommits.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void pruneWatermarkRejectsUnsafeRollbackAndUnqueryableOldRoots() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        tree.put(1L, Map.of(bytes("alice"), bytes("100")));
        tree.put(2L, Map.of(bytes("alice"), bytes("200")));

        assertThrows(IllegalArgumentException.class, () -> store.pruneUpTo(-1L));
        store.pruneUpTo(2L);

        assertTrue(store.rootHash(1L).isEmpty());
        assertThrows(IllegalStateException.class, () -> store.truncateAfter(1L));
        RdbmsJmtStore reopened = new RdbmsJmtStore(dbConfig);
        try {
            assertThrows(IllegalStateException.class, () -> reopened.truncateAfter(1L));
        } finally {
            reopened.close();
        }
    }

    @Test
    void h2PrefixCannotChangeIdempotencyKeySelection() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:prefixed_jmt_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1";
        try (DbConfig prefixedConfig = DbConfig.builder()
                .simpleJdbcUrl(jdbcUrl)
                .tablePrefix("values_a")
                .build()) {
            try (Connection conn = prefixedConfig.dataSource().getConnection();
                 Statement stmt = conn.createStatement()) {
                String schema = new String(getClass().getResourceAsStream(
                        "/ddl/jmt/h2/schema.sql").readAllBytes(), StandardCharsets.UTF_8);
                stmt.execute(schema.replace("jmt_", "values_a_jmt_"));
            }
            try (RdbmsJmtStore prefixedStore = new RdbmsJmtStore(prefixedConfig)) {
                JellyfishMerkleTree tree = new JellyfishMerkleTree(prefixedStore);
                tree.put(1L, Map.of(bytes("alice"), bytes("100")));
                assertDoesNotThrow(() -> tree.put(2L,
                        Map.of(bytes("alice"), bytes("200"))));
            }
        }
    }

    private static void commitValue(RdbmsJmtStore store,
                                    long version,
                                    byte[] root,
                                    byte[] keyHash,
                                    byte[] value) {
        try (JmtStore.CommitBatch batch = store.beginCommit(
                version, JmtStore.CommitConfig.defaults())) {
            batch.putValue(keyHash, value);
            batch.setRootHash(root);
            batch.commit();
        }
    }

    private static byte[] root(int marker) {
        byte[] root = new byte[32];
        root[0] = (byte) marker;
        return root;
    }

    private static boolean hasWriteConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JmtWriteConflictException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class BarrierRdbmsJmtStore extends RdbmsJmtStore {
        private final CountDownLatch commitsReady;
        private final CountDownLatch releaseCommits;

        private BarrierRdbmsJmtStore(DbConfig config,
                                     CountDownLatch commitsReady,
                                     CountDownLatch releaseCommits) {
            super(config);
            this.commitsReady = commitsReady;
            this.releaseCommits = releaseCommits;
        }

        @Override
        public JmtStore.CommitBatch beginCommit(long version, JmtStore.CommitConfig config) {
            commitsReady.countDown();
            try {
                if (!releaseCommits.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release concurrent commits");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while coordinating concurrent commits", e);
            }
            return super.beginCommit(version, config);
        }
    }

    @Test
    void crashMidCommitLeavesStoreUntouched() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);

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
    void formatDescriptorPersistsAndIsValidatedOnReopen() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        tree.put(0, Map.of(bytes("key"), bytes("value")));
        assertEquals(JmtFormatDescriptor.classicBlake2b256V1(),
                store.formatDescriptor().orElseThrow());
        assertTrue(new JmtIntegrityChecker(store, JmtProfile.classicBlake2b256V1())
                .check(JmtIntegrityMode.FULL).healthy());

        RdbmsJmtStore reopened = new RdbmsJmtStore(dbConfig);
        try {
            assertEquals(JmtFormatDescriptor.classicBlake2b256V1(),
                    reopened.formatDescriptor().orElseThrow());
            new JellyfishMerkleTree(reopened);
        } finally {
            reopened.close();
        }
    }

    @Test
    void nonEmptyNamespaceWithoutFormatMarkerFailsClosed() throws Exception {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        tree.put(0, Map.of(bytes("key"), bytes("value")));

        try (Connection conn = dbConfig.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM jmt_metadata WHERE namespace = 0");
        }

        assertThrows(JmtFormatMismatchException.class, () -> new RdbmsJmtStore(dbConfig));
    }

    @Test
    void persistentStoreRejectsUnversionedTreeConstructor() {
        assertThrows(JmtFormatMismatchException.class,
                () -> new JellyfishMerkleTree(store, HASH));
        new JellyfishMerkleTree(store);
    }

    @Test
    void pruneRetentionSurvivesDbRestart() throws Exception {
        byte[] root3;

        // Initial writes and prune
        {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);

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
            JellyfishMerkleTree tree1 = new JellyfishMerkleTree(store1);
            JellyfishMerkleTree tree2 = new JellyfishMerkleTree(store2);

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
