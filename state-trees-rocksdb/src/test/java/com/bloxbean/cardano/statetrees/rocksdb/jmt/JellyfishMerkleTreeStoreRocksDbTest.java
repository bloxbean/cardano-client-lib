package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.NodeKey;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JellyfishMerkleTreeStoreRocksDbTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    @Test
    void commitPersistPruneWithFacade() {
        String dbPath = tempDir.resolve("jmt-facade-db").toString();
        try (RocksDbJmtStore store = new RocksDbJmtStore(dbPath)) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            v1.put(bytes("bob"), bytes("200"));
            JellyfishMerkleTree.CommitResult r1 = tree.commit(1, v1);
            assertArrayEquals(r1.rootHash(), store.rootHash(1).orElseThrow());

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("alice"), bytes("150"));
            JellyfishMerkleTree.CommitResult r2 = tree.commit(2, v2);
            assertArrayEquals(r2.rootHash(), store.rootHash(2).orElseThrow());

            Map<byte[], byte[]> v3 = new LinkedHashMap<>();
            v3.put(bytes("bob"), null);
            JellyfishMerkleTree.CommitResult r3 = tree.commit(3, v3);
            assertArrayEquals(r3.rootHash(), store.rootHash(3).orElseThrow());

            List<NodeKey> staleUpTo3 = store.staleNodesUpTo(3);
            assertFalse(staleUpTo3.isEmpty());

            int pruned = store.pruneUpTo(3);
            assertTrue(pruned > 0);
            for (NodeKey nk : staleUpTo3) {
                assertTrue(store.getNode(nk).isEmpty());
            }
        }
    }

    private static byte[] bytes(String v) {
        return v.getBytes(StandardCharsets.UTF_8);
    }
}

