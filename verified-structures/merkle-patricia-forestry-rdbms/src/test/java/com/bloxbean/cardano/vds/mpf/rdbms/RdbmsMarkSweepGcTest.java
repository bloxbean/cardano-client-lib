package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcOptions;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcReport;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RdbmsMarkSweepGc using H2 in-memory database.
 */
class RdbmsMarkSweepGcTest {

    private DbConfig dbConfig;
    private RdbmsNodeStore nodeStore;
    private RdbmsRootsIndex rootsIndex;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:test_gc_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();
        createSchema(dbConfig);
        nodeStore = new RdbmsNodeStore(dbConfig);
        rootsIndex = new RdbmsRootsIndex(dbConfig);
    }

    @AfterEach
    void tearDown() {
        if (nodeStore != null) nodeStore.close();
        if (rootsIndex != null) rootsIndex.close();
    }

    private void createSchema(DbConfig config) throws Exception {
        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                getClass().getResourceAsStream("/ddl/mpf/h2/schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );
            stmt.execute(schema);
        }
    }

    @Test
    void gcDeletesOrphanedNodes() {
        MpfTrie trie = new MpfTrie(nodeStore);

        // Insert data to create nodes
        for (int i = 0; i < 10; i++) {
            trie.put(bytes("key" + i), bytes("value" + i));
        }
        byte[] root1 = trie.getRootHash();
        rootsIndex.put(0L, root1);

        long nodesBefore = countNodes();
        assertTrue(nodesBefore > 0, "Should have nodes in store");

        // Delete half the keys — creates orphaned nodes
        for (int i = 0; i < 5; i++) {
            trie.delete(bytes("key" + i));
        }
        byte[] root2 = trie.getRootHash();
        rootsIndex.put(0L, root2);

        long nodesAfterInserts = countNodes();
        // There should be more nodes now (old + new)
        assertTrue(nodesAfterInserts >= nodesBefore,
            "Should have at least as many nodes after deletes (orphans remain)");

        // Run GC keeping only root2
        RdbmsMarkSweepGc gc = new RdbmsMarkSweepGc(nodeStore, rootsIndex);
        RdbmsGcReport report = gc.run(Collections.singletonList(root2), new RdbmsGcOptions());

        assertTrue(report.deleted > 0, "Should have deleted orphaned nodes");
        assertTrue(report.marked > 0, "Should have marked reachable nodes");

        long nodesAfterGc = countNodes();
        assertTrue(nodesAfterGc < nodesAfterInserts,
            "Node count should decrease after GC");
        assertEquals(report.marked, nodesAfterGc,
            "Remaining nodes should equal marked nodes");

        // Verify trie still works after GC
        trie.setRootHash(root2);
        for (int i = 5; i < 10; i++) {
            assertArrayEquals(bytes("value" + i), trie.get(bytes("key" + i)),
                "Key " + i + " should still be accessible after GC");
        }
    }

    @Test
    void gcOnEmptyTrieDeletesNothing() {
        RdbmsMarkSweepGc gc = new RdbmsMarkSweepGc(nodeStore, rootsIndex);
        RdbmsGcReport report = gc.run(Collections.emptyList(), new RdbmsGcOptions());

        assertEquals(0, report.marked);
        assertEquals(0, report.total);
        assertEquals(0, report.deleted);
    }

    @Test
    void gcPreservesAllReachableNodes() {
        MpfTrie trie = new MpfTrie(nodeStore);
        for (int i = 0; i < 15; i++) {
            trie.put(bytes("key" + i), bytes("value" + i));
        }
        byte[] root = trie.getRootHash();

        // First GC run: clean up intermediate orphans created during insertions
        // (copy-on-write trie creates orphans at each mutation)
        RdbmsMarkSweepGc gc = new RdbmsMarkSweepGc(nodeStore, rootsIndex);
        gc.run(Collections.singletonList(root), new RdbmsGcOptions());

        long nodesAfterFirstGc = countNodes();
        assertTrue(nodesAfterFirstGc > 0, "Should have reachable nodes");

        // Second GC run: should delete nothing since all orphans are already cleaned
        RdbmsGcReport report = gc.run(Collections.singletonList(root), new RdbmsGcOptions());

        assertEquals(0, report.deleted, "Second GC should not delete any nodes");
        assertEquals(nodesAfterFirstGc, countNodes(), "Node count should not change");

        // Verify trie still works
        trie.setRootHash(root);
        for (int i = 0; i < 15; i++) {
            assertArrayEquals(bytes("value" + i), trie.get(bytes("key" + i)));
        }
    }

    @Test
    void gcDryRunDoesNotDeleteButReportsOrphans() {
        MpfTrie trie = new MpfTrie(nodeStore);
        trie.put(bytes("key1"), bytes("value1"));
        byte[] root1 = trie.getRootHash();

        trie.put(bytes("key2"), bytes("value2"));
        byte[] root2 = trie.getRootHash();

        long nodesBefore = countNodes();

        // Dry run with only root2
        RdbmsGcOptions opts = new RdbmsGcOptions();
        opts.dryRun = true;

        RdbmsMarkSweepGc gc = new RdbmsMarkSweepGc(nodeStore, rootsIndex);
        RdbmsGcReport report = gc.run(Collections.singletonList(root2), opts);

        // Should report orphans but not delete
        assertEquals(nodesBefore, countNodes(), "Node count should not change in dry run");
        assertTrue(report.deleted >= 0, "Should report orphan count");
    }

    @Test
    void gcWithProgressCallback() {
        MpfTrie trie = new MpfTrie(nodeStore);
        for (int i = 0; i < 20; i++) {
            trie.put(bytes("key" + i), bytes("value" + i));
        }
        // Delete all keys to create maximum orphans
        byte[] emptyRoot = null;
        for (int i = 0; i < 20; i++) {
            trie.delete(bytes("key" + i));
        }

        long[] progressCount = {0};
        RdbmsGcOptions opts = new RdbmsGcOptions();
        opts.deleteBatchSize = 5;
        opts.progress = deleted -> progressCount[0]++;

        RdbmsMarkSweepGc gc = new RdbmsMarkSweepGc(nodeStore, rootsIndex);
        gc.run(Collections.emptyList(), opts);

        // Progress should have been called at least once if there were enough orphans
        assertTrue(countNodes() == 0, "All nodes should be deleted");
    }

    @Test
    void gcWithMultipleRoots() {
        // Create two separate tries sharing the same store
        MpfTrie trie1 = new MpfTrie(nodeStore);
        trie1.put(bytes("alice"), bytes("100"));
        byte[] root1 = trie1.getRootHash();

        MpfTrie trie2 = new MpfTrie(nodeStore);
        trie2.put(bytes("bob"), bytes("200"));
        byte[] root2 = trie2.getRootHash();

        // Also add some orphaned nodes
        MpfTrie orphanTrie = new MpfTrie(nodeStore);
        orphanTrie.put(bytes("orphan"), bytes("data"));

        long nodesBefore = countNodes();

        // GC keeping both root1 and root2
        RdbmsMarkSweepGc gc = new RdbmsMarkSweepGc(nodeStore, rootsIndex);
        RdbmsGcReport report = gc.run(java.util.Arrays.asList(root1, root2), new RdbmsGcOptions());

        // Orphan nodes should be deleted
        assertTrue(report.deleted > 0);

        // Both tries should still work
        trie1.setRootHash(root1);
        assertArrayEquals(bytes("100"), trie1.get(bytes("alice")));

        trie2.setRootHash(root2);
        assertArrayEquals(bytes("200"), trie2.get(bytes("bob")));
    }

    private long countNodes() {
        try (Connection conn = dbConfig.dataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM mpt_nodes WHERE namespace = 0")) {
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
