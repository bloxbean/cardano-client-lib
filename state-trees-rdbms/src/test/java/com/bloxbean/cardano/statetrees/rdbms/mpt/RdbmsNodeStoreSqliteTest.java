package com.bloxbean.cardano.statetrees.rdbms.mpt;

import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.nio.file.Path;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQLite-specific tests for RdbmsNodeStore.
 *
 * <p>These tests verify that the RDBMS implementation works correctly with SQLite,
 * which has different characteristics compared to H2 (used in standard tests).
 *
 * <p>Key SQLite differences to test:
 * <ul>
 *   <li>Type affinity system (BLOB type handling)</li>
 *   <li>Limited ALTER TABLE support</li>
 *   <li>Different transaction semantics</li>
 *   <li>AUTOINCREMENT behavior</li>
 * </ul>
 */
class RdbmsNodeStoreSqliteTest {

    private DbConfig dbConfig;
    private RdbmsNodeStore store;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("mpt-sqlite.db").toFile();
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();

        // Create schema
        createSchema(dbConfig);

        // Create store
        store = new RdbmsNodeStore(dbConfig);
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
                getClass().getResourceAsStream("/schema/mpt/sqlite/V1__mpt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );

            // SQLite JDBC driver doesn't support executing multiple statements at once
            // Split statements by semicolon, handling multi-line statements
            String[] statements = schema.split(";");
            for (String sql : statements) {
                // Remove line comments and trim
                String cleaned = sql.replaceAll("--[^\n]*", "").trim();
                if (!cleaned.isEmpty()) {
                    stmt.execute(cleaned);
                }
            }
        }
    }

    @Test
    void sqlite_putAndGetNode() {
        byte[] hash = Blake2b256.digest(bytes("test-node-sqlite"));
        byte[] nodeData = bytes("sqlite-node-data-content");

        // Put node
        store.put(hash, nodeData);

        // Get node back
        byte[] retrieved = store.get(hash);
        assertArrayEquals(nodeData, retrieved,
            "SQLite should persist and retrieve node data correctly");
    }

    @Test
    void sqlite_blobTypeHandling() {
        // Test various blob sizes to ensure SQLite BLOB type works correctly
        byte[] hash1 = Blake2b256.digest(bytes("small"));
        byte[] small = bytes("x");

        byte[] hash2 = Blake2b256.digest(bytes("medium"));
        byte[] medium = new byte[1024];
        Arrays.fill(medium, (byte) 0x42);

        byte[] hash3 = Blake2b256.digest(bytes("large"));
        byte[] large = new byte[64 * 1024]; // 64KB
        Arrays.fill(large, (byte) 0xFF);

        store.put(hash1, small);
        store.put(hash2, medium);
        store.put(hash3, large);

        assertArrayEquals(small, store.get(hash1), "SQLite should handle small blobs");
        assertArrayEquals(medium, store.get(hash2), "SQLite should handle medium blobs");
        assertArrayEquals(large, store.get(hash3), "SQLite should handle large blobs");
    }

    @Test
    void sqlite_deleteNode() {
        byte[] hash = Blake2b256.digest(bytes("to-delete-sqlite"));
        byte[] nodeData = bytes("data-to-delete-from-sqlite");

        // Put node
        store.put(hash, nodeData);
        assertNotNull(store.get(hash), "Node should exist after put");

        // Delete node
        store.delete(hash);
        assertNull(store.get(hash),
            "SQLite should correctly delete nodes");
    }

    @Test
    void sqlite_trieOperations() {
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, Blake2b256::digest);

        // Put some values
        trie.put(bytes("alice"), bytes("100"));
        trie.put(bytes("bob"), bytes("200"));
        trie.put(bytes("charlie"), bytes("300"));

        byte[] root1 = trie.getRootHash();
        assertNotNull(root1, "Root hash should not be null after puts");

        // Verify values can be retrieved
        assertArrayEquals(bytes("100"), trie.get(bytes("alice")),
            "Alice's value should be 100");
        assertArrayEquals(bytes("200"), trie.get(bytes("bob")),
            "Bob's value should be 200");
        assertArrayEquals(bytes("300"), trie.get(bytes("charlie")),
            "Charlie's value should be 300");

        // Update a value
        trie.put(bytes("alice"), bytes("150"));

        byte[] root2 = trie.getRootHash();
        assertFalse(Arrays.equals(root1, root2),
            "Root should change after update in SQLite");

        assertArrayEquals(bytes("150"), trie.get(bytes("alice")),
            "Alice's updated value should be 150");
    }

    @Test
    void sqlite_triePersistence() throws Exception {
        byte[] root;

        // First session: create trie and store data
        {
            MerklePatriciaTrie trie = new MerklePatriciaTrie(store, Blake2b256::digest);
            trie.put(bytes("alice"), bytes("100"));
            trie.put(bytes("bob"), bytes("200"));
            root = trie.getRootHash();
        }

        // Close and reopen store with same SQLite connection
        store.close();
        store = new RdbmsNodeStore(dbConfig);

        // Second session: verify data persisted
        {
            MerklePatriciaTrie trie = new MerklePatriciaTrie(store, Blake2b256::digest);
            trie.setRootHash(root);

            assertArrayEquals(bytes("100"), trie.get(bytes("alice")),
                "Alice's value should persist in SQLite");
            assertArrayEquals(bytes("200"), trie.get(bytes("bob")),
                "Bob's value should persist in SQLite");
        }
    }

    @Test
    void sqlite_namespaceIsolation() {
        // Create two stores with different namespaces
        RdbmsNodeStore store1 = new RdbmsNodeStore(dbConfig, (byte) 0x01);
        RdbmsNodeStore store2 = new RdbmsNodeStore(dbConfig, (byte) 0x02);

        try {
            byte[] hash = Blake2b256.digest(bytes("shared-hash-sqlite"));
            byte[] data1 = bytes("data-from-namespace-1");
            byte[] data2 = bytes("data-from-namespace-2");

            // Put different data in each namespace
            store1.put(hash, data1);
            store2.put(hash, data2);

            // Verify namespace isolation in SQLite
            assertArrayEquals(data1, store1.get(hash),
                "Namespace 1 should have its own data in SQLite");
            assertArrayEquals(data2, store2.get(hash),
                "Namespace 2 should have its own data in SQLite");

            // Verify tries with different namespaces produce different roots
            MerklePatriciaTrie trie1 = new MerklePatriciaTrie(store1, Blake2b256::digest);
            MerklePatriciaTrie trie2 = new MerklePatriciaTrie(store2, Blake2b256::digest);

            trie1.put(bytes("alice"), bytes("100"));
            trie2.put(bytes("alice"), bytes("200"));

            byte[] root1 = trie1.getRootHash();
            byte[] root2 = trie2.getRootHash();

            assertFalse(Arrays.equals(root1, root2),
                "SQLite namespaces should produce different roots for different data");

        } finally {
            store1.close();
            store2.close();
        }
    }

    @Test
    void sqlite_transactionSemantics() {
        // Test that operations within a transaction are atomic in SQLite
        byte[] hash1 = Blake2b256.digest(bytes("tx-test-1"));
        byte[] hash2 = Blake2b256.digest(bytes("tx-test-2"));
        byte[] data1 = bytes("tx-data-1");
        byte[] data2 = bytes("tx-data-2");

        // Use transaction callback
        store.withTransaction(() -> {
            store.put(hash1, data1);
            store.put(hash2, data2);
            return null;
        });

        // Both operations should be committed
        assertArrayEquals(data1, store.get(hash1),
            "First operation should be committed in SQLite transaction");
        assertArrayEquals(data2, store.get(hash2),
            "Second operation should be committed in SQLite transaction");

        // Test rollback on exception
        byte[] hash3 = Blake2b256.digest(bytes("tx-test-3"));
        byte[] data3 = bytes("tx-data-3");

        try {
            store.withTransaction(() -> {
                store.put(hash3, data3);
                throw new RuntimeException("Simulated failure");
            });
            fail("Should have thrown exception");
        } catch (RuntimeException e) {
            assertEquals("Transaction failed", e.getMessage());
        }

        // Operation should be rolled back
        assertNull(store.get(hash3),
            "Failed transaction should be rolled back in SQLite");
    }

    @Test
    void sqlite_readYourWrites() {
        // Test that writes within a transaction are visible to subsequent reads
        byte[] hash1 = Blake2b256.digest(bytes("ryw-test-1"));
        byte[] hash2 = Blake2b256.digest(bytes("ryw-test-2"));
        byte[] data1 = bytes("ryw-data-1");
        byte[] data2 = bytes("ryw-data-2");

        byte[] result = store.withTransaction(() -> {
            store.put(hash1, data1);

            // Should be able to read what we just wrote
            byte[] read1 = store.get(hash1);
            assertArrayEquals(data1, read1,
                "Should see own writes within SQLite transaction");

            store.put(hash2, data2);
            byte[] read2 = store.get(hash2);
            assertArrayEquals(data2, read2,
                "Should see multiple writes within SQLite transaction");

            return store.get(hash1);
        });

        assertArrayEquals(data1, result,
            "Transaction callback should return value from read-your-writes");
    }

    @Test
    void sqlite_emptyTrieHandling() {
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, Blake2b256::digest);

        byte[] emptyRoot = trie.getRootHash();
        assertNull(emptyRoot, "Empty trie should have null root in SQLite");

        // After adding data, should have a root
        trie.put(bytes("key"), bytes("value"));
        byte[] root1 = trie.getRootHash();
        assertNotNull(root1, "Trie with data should have a root hash in SQLite");

        // After removing all data, should return to null root
        trie.delete(bytes("key"));
        assertNull(trie.getRootHash(),
            "Root should return to null after deleting all data in SQLite");
    }

    @Test
    void sqlite_specialCharactersInData() {
        // Test that SQLite handles binary data with special bytes correctly
        byte[] hash = Blake2b256.digest(bytes("special-chars"));
        byte[] specialData = new byte[] {
            0x00, 0x01, 0x0A, 0x0D, 0x1A, // control chars
            (byte) 0x80, (byte) 0xFF,      // high bytes
            0x22, 0x27, 0x5C              // quotes and backslash
        };

        store.put(hash, specialData);
        byte[] retrieved = store.get(hash);

        assertArrayEquals(specialData, retrieved,
            "SQLite should handle special binary characters correctly");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
