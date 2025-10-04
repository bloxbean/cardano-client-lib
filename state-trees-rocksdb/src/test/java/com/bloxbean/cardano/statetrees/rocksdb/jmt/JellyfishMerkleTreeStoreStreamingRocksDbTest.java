package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStoreConfig;
import com.bloxbean.cardano.statetrees.jmt.NodeKey;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JellyfishMerkleTreeStoreStreamingRocksDbTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    @Test
    void streamingModeFacadeWithRocksDb() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-stream-db").toString())) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING);
            JellyfishMerkleTree expected = new JellyfishMerkleTree(COMMITMENTS, HASH);

            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            JellyfishMerkleTree.CommitResult r1 = tree.commit(1, v1);
            expected.commit(1, v1);
            assertArrayEquals(expected.rootHash(1), r1.rootHash());
            assertArrayEquals(r1.rootHash(), store.rootHash(1).orElseThrow());

            Optional<com.bloxbean.cardano.statetrees.jmt.JmtProof> inc = tree.getProof(bytes("alice"), 1);
            assertTrue(inc.isPresent());

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            // collision case: insert alex causing branch at prefix
            v2.put(bytes("alex"), bytes("175"));
            tree.commit(2, v2);
            expected.commit(2, v2);
            assertArrayEquals(expected.rootHash(2), tree.rootHash(2));
            assertArrayEquals(bytes("175"), tree.get(bytes("alex")));
        }
    }

    @Test
    void abortedStreamingCommitDoesNotPublishAndCachesRecover() {
        Path dbPath = tempDir.resolve("jmt-stream-crash-db");

        try (RocksDbJmtStore backend = new RocksDbJmtStore(dbPath.toString())) {
            JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                    .enableNodeCache(true).nodeCacheSize(64)
                    .enableValueCache(true).valueCacheSize(64)
                    .build();
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);
            JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            tree.commit(1, v1);
            reference.commit(1, v1);

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("bob"), bytes("200"));

            JellyfishMerkleTree.CommitResult pending = reference.commit(2, v2);
            try (JmtStore.CommitBatch batch = backend.beginCommit(pending.version(), JmtStore.CommitConfig.defaults())) {
                for (Map.Entry<NodeKey, com.bloxbean.cardano.statetrees.jmt.JmtNode> entry : pending.nodes().entrySet()) {
                    batch.putNode(entry.getKey(), entry.getValue());
                }
                for (JellyfishMerkleTree.CommitResult.ValueOperation op : pending.valueOperations()) {
                    switch (op.type()) {
                        case PUT:
                            batch.putValue(op.keyHash(), op.value());
                            break;
                        case DELETE:
                            batch.deleteValue(op.keyHash());
                            break;
                    }
                }
                for (NodeKey stale : pending.staleNodes()) {
                    batch.markStale(stale);
                }
                batch.setRootHash(pending.rootHash());
                // no batch.commit()
            }

            assertArrayEquals(tree.rootHash(1), backend.latestRoot().orElseThrow().rootHash());
            assertNull(tree.get(bytes("bob")), "aborted commit must not expose new values");
        }

        try (RocksDbJmtStore backend = new RocksDbJmtStore(dbPath.toString())) {
            JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                    .enableNodeCache(true).nodeCacheSize(64)
                    .enableValueCache(true).valueCacheSize(64)
                    .build();
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

            assertNull(tree.get(bytes("bob")), "bob should remain absent after restart");

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("bob"), bytes("200"));
            tree.commit(2, v2);
            assertArrayEquals(bytes("200"), tree.get(bytes("bob")));
        }
    }

    @Test
    void pruneEvictsCachesAcrossRestart() {
        Path dbPath = tempDir.resolve("jmt-stream-prune-db");

        try (RocksDbJmtStore backend = new RocksDbJmtStore(dbPath.toString())) {
            JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                    .enableNodeCache(true).nodeCacheSize(32)
                    .enableValueCache(true).valueCacheSize(32)
                    .build();
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            tree.commit(1, v1);

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("alice"), bytes("150"));
            tree.commit(2, v2);

            assertArrayEquals(bytes("150"), tree.get(bytes("alice")));
            assertTrue(tree.getProof(bytes("alice"), 2).isPresent());

            JellyfishMerkleTreeStore.PruneReport report = tree.prune(1);
            assertTrue(report.valueCacheCleared() > 0 || report.cacheEntriesEvicted() > 0,
                    "prune should evict cached entries");

            assertArrayEquals(bytes("150"), tree.get(bytes("alice")));
            assertTrue(tree.getProof(bytes("alice"), 2).isPresent());
        }

        try (RocksDbJmtStore backend = new RocksDbJmtStore(dbPath.toString())) {
            JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                    .enableNodeCache(true).nodeCacheSize(32)
                    .enableValueCache(true).valueCacheSize(32)
                    .build();
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

            assertArrayEquals(bytes("150"), tree.get(bytes("alice")));
            assertTrue(tree.getProof(bytes("alice"), 2).isPresent());
            assertNull(tree.get(bytes("bob")));
        }
    }

    private static byte[] bytes(String v) {
        return v.getBytes(StandardCharsets.UTF_8);
    }
}
