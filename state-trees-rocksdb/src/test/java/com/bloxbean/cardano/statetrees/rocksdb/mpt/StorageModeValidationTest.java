package com.bloxbean.cardano.statetrees.rocksdb.mpt;

import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.rocksdb.namespace.NamespaceOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for storage mode validation and persistence.
 */
class StorageModeValidationTest {

    @TempDir
    Path tempDir;

    private RocksDbStateTrees stateTrees;

    @AfterEach
    void tearDown() {
        if (stateTrees != null) {
            stateTrees.close();
        }
    }

    private byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testModePersistedOnFirstOpen() {
        String dbPath = tempDir.resolve("test-db").toString();

        // First open - creates database with SINGLE_VERSION mode
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);
        assertEquals(StorageMode.SINGLE_VERSION, stateTrees.storageMode());
        stateTrees.close();

        // Reopen with same mode - should succeed
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);
        assertEquals(StorageMode.SINGLE_VERSION, stateTrees.storageMode());
    }

    @Test
    void testModeValidationFailsOnMismatch() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create database with MULTI_VERSION mode
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);
        stateTrees.close();

        // Try to open with SINGLE_VERSION mode - should fail
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION)
        );
        assertTrue(exception.getMessage().contains("Storage mode mismatch"));
        assertTrue(exception.getMessage().contains("MULTI_VERSION"));
        assertTrue(exception.getMessage().contains("SINGLE_VERSION"));
    }

    @Test
    void testModeValidationFailsOppositeDirection() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create database with SINGLE_VERSION mode
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);
        stateTrees.close();

        // Try to open with MULTI_VERSION mode - should fail
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION)
        );
        assertTrue(exception.getMessage().contains("Storage mode mismatch"));
        assertTrue(exception.getMessage().contains("SINGLE_VERSION"));
        assertTrue(exception.getMessage().contains("MULTI_VERSION"));
    }

    @Test
    void testDefaultConstructorUsesMultiVersion() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Default constructor
        stateTrees = new RocksDbStateTrees(dbPath);
        assertEquals(StorageMode.MULTI_VERSION, stateTrees.storageMode());
        stateTrees.close();

        // Reopen with explicit MULTI_VERSION - should succeed
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);
        assertEquals(StorageMode.MULTI_VERSION, stateTrees.storageMode());
    }

    @Test
    void testNamespaceConstructorUsesMultiVersion() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Namespace constructor without mode
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults());
        assertEquals(StorageMode.MULTI_VERSION, stateTrees.storageMode());
        stateTrees.close();

        // Reopen with explicit MULTI_VERSION - should succeed
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);
        assertEquals(StorageMode.MULTI_VERSION, stateTrees.storageMode());
    }

    @Test
    void testMultiVersionMethodsWorkInMultiVersionMode() {
        String dbPath = tempDir.resolve("test-db").toString();
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);

        MerklePatriciaTrie trie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash);
        trie.put("key1".getBytes(), "value1".getBytes());
        byte[] root = trie.getRootHash();

        // Should work in MULTI_VERSION mode
        long version = stateTrees.putRootWithRefcount(root);
        assertEquals(0L, version);
    }

    @Test
    void testSingleVersionMethodsWorkInSingleVersionMode() {
        String dbPath = tempDir.resolve("test-db").toString();
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);

        MerklePatriciaTrie trie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash);
        trie.put("key1".getBytes(), "value1".getBytes());
        byte[] root = trie.getRootHash();

        // Should work in SINGLE_VERSION mode
        assertDoesNotThrow(() -> stateTrees.putRootSnapshot(root));
        assertDoesNotThrow(() -> stateTrees.getCurrentRoot());
        assertDoesNotThrow(() -> stateTrees.cleanupOrphanedNodes(new com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.GcOptions()));
    }

    @Test
    void testGetCurrentRootThrowsInMultiVersionMode() {
        String dbPath = tempDir.resolve("test-db").toString();
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> stateTrees.getCurrentRoot()
        );
        assertTrue(exception.getMessage().contains("SINGLE_VERSION mode"));
    }

    @Test
    void testPutRootSnapshotThrowsInMultiVersionMode() {
        String dbPath = tempDir.resolve("test-db").toString();
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);

        MerklePatriciaTrie trie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash);
        trie.put("key1".getBytes(), "value1".getBytes());
        byte[] root = trie.getRootHash();

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> stateTrees.putRootSnapshot(root)
        );
        assertTrue(exception.getMessage().contains("SINGLE_VERSION mode"));
    }

    @Test
    void testCleanupOrphanedNodesThrowsInMultiVersionMode() {
        String dbPath = tempDir.resolve("test-db").toString();
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> stateTrees.cleanupOrphanedNodes(new com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.GcOptions())
        );
        assertTrue(exception.getMessage().contains("SINGLE_VERSION mode"));
    }

    @Test
    void testModePersistedAcrossMultipleReopens() {
        String dbPath = tempDir.resolve("test-db").toString();

        // Create with SINGLE_VERSION
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);
        stateTrees.close();

        // Reopen multiple times
        for (int i = 0; i < 3; i++) {
            stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);
            assertEquals(StorageMode.SINGLE_VERSION, stateTrees.storageMode());
            stateTrees.close();
        }

        // Try to open with wrong mode - should still fail
        assertThrows(
            IllegalStateException.class,
            () -> new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION)
        );
    }
}
