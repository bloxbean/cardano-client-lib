package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug test to understand hash computation differences.
 */
class JellyfishMerkleTreeV2DebugTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void debugSingleKeyHashing() throws Exception {
        String key = "key1";
        String value = "value1";

        // Reference implementation
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);
        Map<byte[], byte[]> updates = new HashMap<>();
        updates.put(key.getBytes(), value.getBytes());
        JellyfishMerkleTree.CommitResult refResult = reference.commit(0, updates);

        System.out.println("=== Reference Implementation ===");
        System.out.println("Root hash: " + HexUtil.encodeHexString(refResult.rootHash()));
        System.out.println("Nodes created: " + refResult.nodes().size());
        refResult.nodes().forEach((nodeKey, node) -> {
            System.out.println("  Node at " + nodeKey + ": " + node.getClass().getSimpleName());
        });

        // V2 implementation
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            System.out.println("\n=== V2 Implementation ===");
            System.out.println("Root hash: " + HexUtil.encodeHexString(v2Result.rootHash()));
            System.out.println("Nodes created: " + v2Result.nodes().size());
            v2Result.nodes().forEach((nodeKey, node) -> {
                System.out.println("  Node at " + nodeKey + ": " + node.getClass().getSimpleName());
            });

            // Hash comparison
            System.out.println("\n=== Hash Comparison ===");
            System.out.println("Hashes match: " + java.util.Arrays.equals(refResult.rootHash(), v2Result.rootHash()));

            // Compute expected hashes manually
            byte[] keyHash = HASH.digest(key.getBytes());
            byte[] valueHash = HASH.digest(value.getBytes());
            System.out.println("\nKey hash: " + HexUtil.encodeHexString(keyHash));
            System.out.println("Value hash: " + HexUtil.encodeHexString(valueHash));
        }
    }
}
