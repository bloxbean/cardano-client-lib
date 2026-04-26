package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.client.test.ByteArrayWrapper;
import com.bloxbean.cardano.client.test.vds.MpfArbitraries;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import net.jqwik.api.*;
import org.junit.jupiter.api.Assumptions;
import org.rocksdb.WriteOptions;
import org.rocksdb.WriteBatch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for MpfTrie backed by RocksDB.
 * Tests R1–R3 covering persistence, cross-backend parity, and batch operations.
 */
class MpfRocksDbPropertyTest {

    @Provide
    Arbitrary<HashFunction> hashFunctions() {
        return MpfArbitraries.hashFunctions();
    }

    @Provide
    Arbitrary<List<Map.Entry<byte[], byte[]>>> entries() {
        return MpfArbitraries.trieKeyValuesAlphanumeric(10, 50);
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("mpf-rocksdb-prop-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void cleanupTempDir(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignore) {}
            });
        } catch (Exception ignore) {}
    }

    // ---- R1: Persistence Across Reopen ----
    @Property(tries = 50)
    void r1_persistenceAcrossReopen(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        Path tempDir = createTempDir();
        try {
            String dbPath = tempDir.resolve("r1-db").toString();

            // Deduplicate
            Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);

            byte[] savedRoot;
            // Phase 1: insert and close
            try (RocksDbNodeStore store = new RocksDbNodeStore(dbPath)) {
                MpfTrie trie = new MpfTrie(store, hashFn);
                for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
                    trie.put(e.getKey().getData(), e.getValue());
                }
                savedRoot = trie.getRootHash();
                assertNotNull(savedRoot);
            }

            // Phase 2: reopen and verify
            try (RocksDbNodeStore store = new RocksDbNodeStore(dbPath)) {
                MpfTrie trie = new MpfTrie(store, hashFn, savedRoot);
                assertArrayEquals(savedRoot, trie.getRootHash());

                for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
                    byte[] key = e.getKey().getData();
                    byte[] value = e.getValue();
                    assertArrayEquals(value, trie.get(key),
                            "value must survive close/reopen");

                    Optional<byte[]> wire = trie.getProofWire(key);
                    assertTrue(wire.isPresent(), "proof must exist after reopen");
                    assertTrue(wire.get().length > 0, "proof must be non-empty after reopen");
                }
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    // ---- R2: Cross-Backend Parity ----
    @Property(tries = 50)
    void r2_crossBackendParity(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        Path tempDir = createTempDir();
        try {
            String dbPath = tempDir.resolve("r2-db").toString();

            // Deduplicate
            Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);

            // In-memory trie
            TestNodeStore memStore = new TestNodeStore();
            MpfTrie memTrie = new MpfTrie(memStore, hashFn);

            // RocksDB trie
            try (RocksDbNodeStore rocksStore = new RocksDbNodeStore(dbPath)) {
                MpfTrie rocksTrie = new MpfTrie(rocksStore, hashFn);

                for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
                    memTrie.put(e.getKey().getData(), e.getValue());
                    rocksTrie.put(e.getKey().getData(), e.getValue());
                }

                assertArrayEquals(memTrie.getRootHash(), rocksTrie.getRootHash(),
                        "In-memory and RocksDB tries must produce the same root hash");

                // Verify individual value retrieval parity
                for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
                    byte[] key = e.getKey().getData();
                    assertArrayEquals(memTrie.get(key), rocksTrie.get(key),
                            "Value retrieval must match between in-memory and RocksDB backends");
                }
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    // ---- R3: Batch Operation Consistency ----
    @Property(tries = 50)
    void r3_batchOperationConsistency(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        Path tempDir = createTempDir();
        try (RocksDbNodeStore store = new RocksDbNodeStore(tempDir.resolve("r3-db").toString())) {
            MpfTrie trie = new MpfTrie(store, hashFn);

            // Deduplicate
            Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);

            // Insert all entries within a batch
            try (WriteBatch wb = new WriteBatch();
                 WriteOptions wo = new WriteOptions()) {
                store.withBatch(wb, () -> {
                    for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
                        trie.put(e.getKey().getData(), e.getValue());
                    }
                    return null;
                });
                store.db().write(wo, wb);
            }

            byte[] root = trie.getRootHash();
            assertNotNull(root);

            // Verify all entries are retrievable and proofs exist
            for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
                byte[] key = e.getKey().getData();
                byte[] value = e.getValue();
                assertArrayEquals(value, trie.get(key),
                        "value must be retrievable after batch insert");

                Optional<byte[]> wire = trie.getProofWire(key);
                assertTrue(wire.isPresent(), "proof must exist after batch insert");
                assertTrue(wire.get().length > 0, "proof must be non-empty after batch insert");
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Batch operation failed", e);
        } finally {
            cleanupTempDir(tempDir);
        }
    }
}
