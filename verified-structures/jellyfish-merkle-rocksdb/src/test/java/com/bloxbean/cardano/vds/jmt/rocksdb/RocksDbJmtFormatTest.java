package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityChecker;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityMode;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatDescriptor;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatMismatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbJmtFormatTest {

    private static final HashFunction HASH = Blake2b256::digest;

    @TempDir
    Path tempDir;

    @Test
    void formatDescriptorPersistsAcrossRestart() {
        Path dbPath = tempDir.resolve("format-restart");
        try (RocksDbJmtStore store = new RocksDbJmtStore(dbPath.toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            tree.put(0, Map.of(bytes("key"), bytes("value")));
            assertEquals(JmtFormatDescriptor.classicBlake2b256V1(),
                    store.formatDescriptor().orElseThrow());
            assertTrue(new JmtIntegrityChecker(store, JmtProfile.classicBlake2b256V1())
                    .check(JmtIntegrityMode.FULL).healthy());
        }

        try (RocksDbJmtStore reopened = new RocksDbJmtStore(dbPath.toString())) {
            assertEquals(JmtFormatDescriptor.classicBlake2b256V1(),
                    reopened.formatDescriptor().orElseThrow());
            new JellyfishMerkleTree(reopened);
        }
    }

    @Test
    void persistentStoreRejectsUnversionedTreeConstructor() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(
                tempDir.resolve("unversioned").toString())) {
            assertThrows(JmtFormatMismatchException.class,
                    () -> new JellyfishMerkleTree(store, HASH));

            // Failed unversioned initialization does not poison the empty namespace.
            new JellyfishMerkleTree(store);
        }
    }

    @Test
    void reopeningWithDifferentRollbackFeatureFailsClosed() {
        Path dbPath = tempDir.resolve("rollback-mismatch");
        RocksDbJmtStore.Options rollbackEnabled = RocksDbJmtStore.Options.builder()
                .enableRollbackIndex(true)
                .build();
        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString(), rollbackEnabled)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            tree.put(0, Map.of(bytes("key"), bytes("value")));
            assertTrue(new JmtIntegrityChecker(store, JmtProfile.classicBlake2b256V1())
                    .check(JmtIntegrityMode.FULL).healthy());
        }

        assertThrows(JmtFormatMismatchException.class,
                () -> RocksDbJmtStore.open(dbPath.toString(), RocksDbJmtStore.Options.defaults()));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
