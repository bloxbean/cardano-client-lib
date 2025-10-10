package com.bloxbean.cardano.statetrees.rocksdb.mpt;

import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.rocksdb.namespace.NamespaceOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for storage mode isolation across different namespaces.
 */
class NamespaceStorageModeTest {

    @TempDir
    Path tempDir;

    private final List<RocksDbStateTrees> openDatabases = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (RocksDbStateTrees db : openDatabases) {
            if (db != null) {
                db.close();
            }
        }
        openDatabases.clear();
    }

    private byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDifferentNamespacesDifferentModes() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create and use namespace1 with MULTI_VERSION mode
        RocksDbStateTrees db1 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("namespace1"),
            StorageMode.MULTI_VERSION
        );
        assertEquals(StorageMode.MULTI_VERSION, db1.storageMode());

        // Verify multi-version operations work on db1
        MerklePatriciaTrie trie1 = new MerklePatriciaTrie(db1.nodeStore(), this::hash);
        trie1.put("key1".getBytes(), "value1".getBytes());
        byte[] root1 = trie1.getRootHash();
        assertDoesNotThrow(() -> db1.putRootWithRefcount(root1));
        db1.close();

        // Create and use namespace2 with SINGLE_VERSION mode in the SAME database file
        RocksDbStateTrees db2 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("namespace2"),
            StorageMode.SINGLE_VERSION
        );
        openDatabases.add(db2);
        assertEquals(StorageMode.SINGLE_VERSION, db2.storageMode());

        // Verify single-version operations work on db2
        MerklePatriciaTrie trie2 = new MerklePatriciaTrie(db2.nodeStore(), this::hash);
        trie2.put("key2".getBytes(), "value2".getBytes());
        byte[] root2 = trie2.getRootHash();
        assertDoesNotThrow(() -> db2.putRootSnapshot(root2));
    }

    @Test
    void testSameNamespaceMustHaveSameMode() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create namespace with SINGLE_VERSION mode
        RocksDbStateTrees db1 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("myspace"),
            StorageMode.SINGLE_VERSION
        );
        openDatabases.add(db1);
        db1.close();
        openDatabases.remove(db1);

        // Try to reopen same namespace with MULTI_VERSION - should fail
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new RocksDbStateTrees(
                dbPath,
                NamespaceOptions.columnFamily("myspace"),
                StorageMode.MULTI_VERSION
            )
        );
        assertTrue(exception.getMessage().contains("Storage mode mismatch"));
    }

    @Test
    void testDifferentNamespacesIndependentValidation() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create ns1 with SINGLE_VERSION
        RocksDbStateTrees db1 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("ns1"),
            StorageMode.SINGLE_VERSION
        );
        openDatabases.add(db1);
        db1.close();
        openDatabases.remove(db1);

        // Create ns2 with MULTI_VERSION (should work - different namespace)
        RocksDbStateTrees db2 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("ns2"),
            StorageMode.MULTI_VERSION
        );
        openDatabases.add(db2);
        db2.close();
        openDatabases.remove(db2);

        // Reopen ns1 with SINGLE_VERSION - should succeed
        db1 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("ns1"),
            StorageMode.SINGLE_VERSION
        );
        assertEquals(StorageMode.SINGLE_VERSION, db1.storageMode());
        db1.close();

        // Reopen ns2 with MULTI_VERSION - should succeed
        db2 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("ns2"),
            StorageMode.MULTI_VERSION
        );
        openDatabases.add(db2);
        assertEquals(StorageMode.MULTI_VERSION, db2.storageMode());
    }

    @Test
    void testKeyPrefixNamespacesHaveIndependentModes() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create namespace with key prefix 0x01 in SINGLE_VERSION mode
        RocksDbStateTrees db1 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.keyPrefix((byte) 0x01),
            StorageMode.SINGLE_VERSION
        );
        assertEquals(StorageMode.SINGLE_VERSION, db1.storageMode());
        db1.close();

        // Create namespace with key prefix 0x02 in MULTI_VERSION mode
        RocksDbStateTrees db2 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.keyPrefix((byte) 0x02),
            StorageMode.MULTI_VERSION
        );
        assertEquals(StorageMode.MULTI_VERSION, db2.storageMode());
        db2.close();

        // Reopen to verify persistence
        db1 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.keyPrefix((byte) 0x01),
            StorageMode.SINGLE_VERSION
        );
        assertEquals(StorageMode.SINGLE_VERSION, db1.storageMode());
        db1.close();

        db2 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.keyPrefix((byte) 0x02),
            StorageMode.MULTI_VERSION
        );
        openDatabases.add(db2);
        assertEquals(StorageMode.MULTI_VERSION, db2.storageMode());
    }

    @Test
    void testDefaultNamespaceCanCoexistWithCustomNamespace() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create default namespace (key prefix 0x00) with MULTI_VERSION
        RocksDbStateTrees defaultDb = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.defaults(),
            StorageMode.MULTI_VERSION
        );
        assertEquals(StorageMode.MULTI_VERSION, defaultDb.storageMode());

        // Test operations on default namespace
        MerklePatriciaTrie defaultTrie = new MerklePatriciaTrie(defaultDb.nodeStore(), this::hash);
        defaultTrie.put("default-key".getBytes(), "default-value".getBytes());
        assertDoesNotThrow(() -> defaultDb.putRootWithRefcount(defaultTrie.getRootHash()));
        defaultDb.close();

        // Create custom namespace with SINGLE_VERSION
        RocksDbStateTrees customDb = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("custom"),
            StorageMode.SINGLE_VERSION
        );
        openDatabases.add(customDb);
        assertEquals(StorageMode.SINGLE_VERSION, customDb.storageMode());

        // Test operations on custom namespace
        MerklePatriciaTrie customTrie = new MerklePatriciaTrie(customDb.nodeStore(), this::hash);
        customTrie.put("custom-key".getBytes(), "custom-value".getBytes());
        assertDoesNotThrow(() -> customDb.putRootSnapshot(customTrie.getRootHash()));
    }

    @Test
    void testThreeNamespacesThreeDifferentModes() {
        String dbPath = tempDir.resolve("test-db").toString();

        // ns1: SINGLE_VERSION
        RocksDbStateTrees db1 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("ns1"),
            StorageMode.SINGLE_VERSION
        );
        assertEquals(StorageMode.SINGLE_VERSION, db1.storageMode());

        MerklePatriciaTrie trie1 = new MerklePatriciaTrie(db1.nodeStore(), this::hash);
        trie1.put("ns1-key".getBytes(), "ns1-value".getBytes());
        db1.putRootSnapshot(trie1.getRootHash());
        db1.close();

        // ns2: MULTI_VERSION
        RocksDbStateTrees db2 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("ns2"),
            StorageMode.MULTI_VERSION
        );
        assertEquals(StorageMode.MULTI_VERSION, db2.storageMode());

        MerklePatriciaTrie trie2 = new MerklePatriciaTrie(db2.nodeStore(), this::hash);
        trie2.put("ns2-key".getBytes(), "ns2-value".getBytes());
        db2.putRootWithRefcount(trie2.getRootHash());
        db2.close();

        // ns3: SINGLE_VERSION (same mode as ns1, different namespace)
        RocksDbStateTrees db3 = new RocksDbStateTrees(
            dbPath,
            NamespaceOptions.columnFamily("ns3"),
            StorageMode.SINGLE_VERSION
        );
        assertEquals(StorageMode.SINGLE_VERSION, db3.storageMode());

        MerklePatriciaTrie trie3 = new MerklePatriciaTrie(db3.nodeStore(), this::hash);
        trie3.put("ns3-key".getBytes(), "ns3-value".getBytes());
        db3.putRootSnapshot(trie3.getRootHash());
        db3.close();

        // Reopen each to verify persistence
        db1 = new RocksDbStateTrees(dbPath, NamespaceOptions.columnFamily("ns1"), StorageMode.SINGLE_VERSION);
        assertEquals(StorageMode.SINGLE_VERSION, db1.storageMode());
        db1.close();

        db2 = new RocksDbStateTrees(dbPath, NamespaceOptions.columnFamily("ns2"), StorageMode.MULTI_VERSION);
        assertEquals(StorageMode.MULTI_VERSION, db2.storageMode());
        db2.close();

        db3 = new RocksDbStateTrees(dbPath, NamespaceOptions.columnFamily("ns3"), StorageMode.SINGLE_VERSION);
        openDatabases.add(db3);
        assertEquals(StorageMode.SINGLE_VERSION, db3.storageMode());
    }
}
