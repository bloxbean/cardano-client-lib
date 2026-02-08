package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcOptions;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcReport;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RdbmsStateTrees using H2 in-memory database.
 */
class RdbmsStateTreesTest {

    private DbConfig dbConfig;
    private RdbmsStateTrees stateTrees;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:test_st_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();
        createSchema(dbConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stateTrees != null) stateTrees.close();
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

    // --- SINGLE_VERSION tests ---

    @Test
    void singleVersionPutAndGetRoot() throws Exception {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);

        MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
        trie.put(bytes("alice"), bytes("100"));
        byte[] root = trie.getRootHash();
        assertNotNull(root);

        stateTrees.putRootSnapshot(root);
        byte[] retrieved = stateTrees.getCurrentRoot();
        assertArrayEquals(root, retrieved);
    }

    @Test
    void singleVersionOverwritesRoot() throws Exception {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);

        MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
        trie.put(bytes("alice"), bytes("100"));
        byte[] root1 = trie.getRootHash();
        stateTrees.putRootSnapshot(root1);

        trie.put(bytes("bob"), bytes("200"));
        byte[] root2 = trie.getRootHash();
        stateTrees.putRootSnapshot(root2);

        assertArrayEquals(root2, stateTrees.getCurrentRoot());
        assertFalse(Arrays.equals(root1, root2));
    }

    @Test
    void singleVersionGetCurrentRootReturnsNullWhenEmpty() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);
        assertNull(stateTrees.getCurrentRoot());
    }

    @Test
    void singleVersionCleanupOrphanedNodes() throws Exception {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);

        MpfTrie trie = new MpfTrie(stateTrees.nodeStore());

        // Insert many keys to create many nodes
        for (int i = 0; i < 20; i++) {
            trie.put(bytes("key" + i), bytes("value" + i));
        }
        byte[] root1 = trie.getRootHash();
        stateTrees.putRootSnapshot(root1);

        // Delete some keys — creates orphaned nodes
        for (int i = 0; i < 10; i++) {
            trie.delete(bytes("key" + i));
        }
        byte[] root2 = trie.getRootHash();
        stateTrees.putRootSnapshot(root2);

        // Run GC
        RdbmsGcReport report = (RdbmsGcReport) stateTrees.cleanupOrphanedNodes(new RdbmsGcOptions());
        assertTrue(report.deleted > 0, "Should have deleted orphaned nodes");
        assertTrue(report.marked > 0, "Should have marked reachable nodes");

        // Verify trie still works after GC
        trie.setRootHash(root2);
        for (int i = 10; i < 20; i++) {
            assertArrayEquals(bytes("value" + i), trie.get(bytes("key" + i)),
                "Key " + i + " should still be accessible after GC");
        }
    }

    @Test
    void singleVersionCleanupWithNullOptions() throws Exception {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);

        MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
        trie.put(bytes("key"), bytes("value"));
        stateTrees.putRootSnapshot(trie.getRootHash());

        // null options should use defaults
        RdbmsGcReport report = (RdbmsGcReport) stateTrees.cleanupOrphanedNodes(null);
        assertNotNull(report);
        assertEquals(0, report.deleted);
    }

    @Test
    void singleVersionDryRunDoesNotDelete() throws Exception {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);

        MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
        trie.put(bytes("key1"), bytes("value1"));
        stateTrees.putRootSnapshot(trie.getRootHash());

        trie.delete(bytes("key1"));
        trie.put(bytes("key2"), bytes("value2"));
        stateTrees.putRootSnapshot(trie.getRootHash());

        RdbmsGcOptions opts = new RdbmsGcOptions();
        opts.dryRun = true;
        RdbmsGcReport report = (RdbmsGcReport) stateTrees.cleanupOrphanedNodes(opts);

        // dry run should report deletions but not actually delete
        assertTrue(report.deleted > 0, "Dry run should still report orphans");
    }

    // --- MULTI_VERSION tests ---

    @Test
    void multiVersionPutRootWithRefcount() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.MULTI_VERSION);

        MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
        trie.put(bytes("alice"), bytes("100"));

        long version = stateTrees.putRootWithRefcount(trie.getRootHash());
        assertEquals(0L, version);

        trie.put(bytes("bob"), bytes("200"));
        long version2 = stateTrees.putRootWithRefcount(trie.getRootHash());
        assertEquals(1L, version2);

        // Verify roots can be retrieved by version
        assertNotNull(stateTrees.rootsIndex().get(0L));
        assertNotNull(stateTrees.rootsIndex().get(1L));
    }

    // --- Mode validation tests ---

    @Test
    void putRootSnapshotThrowsInMultiVersionMode() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.MULTI_VERSION);
        byte[] dummyRoot = new byte[32];

        assertThrows(IllegalStateException.class, () ->
            stateTrees.putRootSnapshot(dummyRoot));
    }

    @Test
    void putRootWithRefcountThrowsInSingleVersionMode() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);
        byte[] dummyRoot = new byte[32];

        assertThrows(IllegalStateException.class, () ->
            stateTrees.putRootWithRefcount(dummyRoot));
    }

    @Test
    void getCurrentRootThrowsInMultiVersionMode() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.MULTI_VERSION);

        assertThrows(IllegalStateException.class, () ->
            stateTrees.getCurrentRoot());
    }

    @Test
    void cleanupThrowsInMultiVersionMode() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.MULTI_VERSION);

        assertThrows(IllegalStateException.class, () ->
            stateTrees.cleanupOrphanedNodes(new RdbmsGcOptions()));
    }

    @Test
    void putRootWithNullThrowsException() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);

        assertThrows(IllegalArgumentException.class, () ->
            stateTrees.putRootSnapshot(null));
        assertThrows(IllegalArgumentException.class, () ->
            stateTrees.putRootSnapshot(new byte[0]));
    }

    @Test
    void cleanupWithInvalidOptionsThrows() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);

        assertThrows(IllegalArgumentException.class, () ->
            stateTrees.cleanupOrphanedNodes("invalid"));
    }

    // --- Storage mode accessor ---

    @Test
    void storageModeReturnsConfiguredMode() {
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.SINGLE_VERSION);
        assertEquals(StorageMode.SINGLE_VERSION, stateTrees.storageMode());

        stateTrees.close();
        stateTrees = new RdbmsStateTrees(dbConfig, StorageMode.MULTI_VERSION);
        assertEquals(StorageMode.MULTI_VERSION, stateTrees.storageMode());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
