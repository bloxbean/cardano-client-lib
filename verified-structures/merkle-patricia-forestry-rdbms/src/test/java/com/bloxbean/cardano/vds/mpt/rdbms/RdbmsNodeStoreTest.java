package com.bloxbean.cardano.vds.mpt.rdbms;

import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.MpfTrie;
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
 * Tests for RdbmsNodeStore using MerklePatriciaTrie (TDD approach).
 *
 * <p>These tests verify the NodeStore contract implementation for RDBMS.
 */
class RdbmsNodeStoreTest {

    private DbConfig dbConfig;
    private RdbmsNodeStore store;

    @BeforeEach
    void setUp() throws Exception {
        // Use H2 in-memory database for testing
        String jdbcUrl = "jdbc:h2:mem:test_mpt_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
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
                getClass().getResourceAsStream("/schema/mpt/h2/V1__mpt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );

            // H2 can execute the entire script at once
            stmt.execute(schema);
        }
    }

    @Test
    void putAndGetNode() {
        byte[] hash = Blake2b256.digest(bytes("test-node"));
        byte[] nodeData = bytes("node-data-content");

        // Put node
        store.put(hash, nodeData);

        // Get node back
        byte[] retrieved = store.get(hash);
        assertArrayEquals(nodeData, retrieved,
            "Retrieved node data should match what was put");
    }

    @Test
    void getNonExistentNodeReturnsNull() {
        byte[] fakeHash = Blake2b256.digest(bytes("nonexistent"));

        byte[] result = store.get(fakeHash);
        assertNull(result,
            "Getting non-existent node should return null");
    }

    @Test
    void deleteNodeRemovesItFromStore() {
        byte[] hash = Blake2b256.digest(bytes("to-delete"));
        byte[] nodeData = bytes("data-to-delete");

        // Put node
        store.put(hash, nodeData);
        assertNotNull(store.get(hash), "Node should exist after put");

        // Delete node
        store.delete(hash);
        assertNull(store.get(hash),
            "Node should not exist after delete");
    }

    @Test
    void trieOperationsWorkWithNodeStore() {
        MpfTrie trie = new MpfTrie(store);

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
            "Root should change after update");

        assertArrayEquals(bytes("150"), trie.get(bytes("alice")),
            "Alice's updated value should be 150");
    }

    @Test
    void trieWithMultipleKeysProducesConsistentRoot() {
        MpfTrie trie1 = new MpfTrie(store);

        trie1.put(bytes("key1"), bytes("value1"));
        trie1.put(bytes("key2"), bytes("value2"));
        trie1.put(bytes("key3"), bytes("value3"));

        byte[] root1 = trie1.getRootHash();

        // Create a new trie with same data in different order
        RdbmsNodeStore store2 = new RdbmsNodeStore(dbConfig);
        MpfTrie trie2 = new MpfTrie(store2);

        trie2.put(bytes("key3"), bytes("value3"));
        trie2.put(bytes("key1"), bytes("value1"));
        trie2.put(bytes("key2"), bytes("value2"));

        byte[] root2 = trie2.getRootHash();

        assertArrayEquals(root1, root2,
            "Tries with same data should have same root regardless of insertion order");

        store2.close();
    }

    @Test
    void triePersistsAcrossStoreReopen() throws Exception {
        byte[] root;

        // First session: create trie and store data
        {
            MpfTrie trie = new MpfTrie(store);
            trie.put(bytes("alice"), bytes("100"));
            trie.put(bytes("bob"), bytes("200"));
            root = trie.getRootHash();
        }

        // Close and reopen store
        store.close();
        store = new RdbmsNodeStore(dbConfig);

        // Second session: verify data persisted
        {
            MpfTrie trie = new MpfTrie(store);
            trie.setRootHash(root);

            assertArrayEquals(bytes("100"), trie.get(bytes("alice")),
                "Alice's value should persist across store reopen");
            assertArrayEquals(bytes("200"), trie.get(bytes("bob")),
                "Bob's value should persist across store reopen");
        }
    }

    @Test
    void namespaceIsolation() {
        // Create two stores with different namespaces
        RdbmsNodeStore store1 = new RdbmsNodeStore(dbConfig, (byte) 0x01);
        RdbmsNodeStore store2 = new RdbmsNodeStore(dbConfig, (byte) 0x02);

        try {
            byte[] hash = Blake2b256.digest(bytes("shared-hash"));
            byte[] data1 = bytes("data-from-namespace-1");
            byte[] data2 = bytes("data-from-namespace-2");

            // Put different data in each namespace
            store1.put(hash, data1);
            store2.put(hash, data2);

            // Verify namespace isolation
            assertArrayEquals(data1, store1.get(hash),
                "Namespace 1 should have its own data");
            assertArrayEquals(data2, store2.get(hash),
                "Namespace 2 should have its own data");

            // Verify tries with different namespaces produce different roots
            MpfTrie trie1 = new MpfTrie(store1);
            MpfTrie trie2 = new MpfTrie(store2);

            trie1.put(bytes("alice"), bytes("100"));
            trie2.put(bytes("alice"), bytes("200"));

            byte[] root1 = trie1.getRootHash();
            byte[] root2 = trie2.getRootHash();

            assertFalse(Arrays.equals(root1, root2),
                "Different namespaces should produce different roots for different data");

        } finally {
            store1.close();
            store2.close();
        }
    }

    @Test
    void emptyTrieHasNullRoot() {
        MpfTrie trie = new MpfTrie(store);

        byte[] emptyRoot = trie.getRootHash();
        assertNull(emptyRoot, "Empty trie should have null root");

        // After adding data, should have a root
        trie.put(bytes("key"), bytes("value"));
        byte[] root1 = trie.getRootHash();
        assertNotNull(root1, "Trie with data should have a root hash");

        // After removing all data, should return to null root
        trie.delete(bytes("key"));
        assertNull(trie.getRootHash(),
            "Root should return to null after deleting all data");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
