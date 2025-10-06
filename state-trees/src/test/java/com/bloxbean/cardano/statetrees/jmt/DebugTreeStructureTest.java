package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug test to trace tree structure and hash computation.
 */
class DebugTreeStructureTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void debugTreeStructure() throws Exception {
        // New implementation
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Same update
            Map<byte[], byte[]> updates = new HashMap<>();
            updates.put("key1".getBytes(), "value1".getBytes());

            // Commit
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            // Print tree structure
            System.out.println("=== V2 Tree Structure ===");
            System.out.println("Root hash: " + bytesToHex(v2Result.rootHash()));
            System.out.println("Nodes created: " + v2Result.nodes().size());

            for (Map.Entry<NodeKey, JmtNode> entry : v2Result.nodes().entrySet()) {
                NodeKey key = entry.getKey();
                JmtNode node = entry.getValue();
                System.out.println("\nNode at path=" + key.path() + ", version=" + key.version());
                if (node instanceof JmtLeafNode) {
                    JmtLeafNode leaf = (JmtLeafNode) node;
                    System.out.println("  Type: LEAF");
                    System.out.println("  KeyHash: " + bytesToHex(leaf.keyHash()));
                    System.out.println("  ValueHash: " + bytesToHex(leaf.valueHash()));

                    // Compute what the hash should be
                    int[] fullNibbles = Nibbles.toNibbles(leaf.keyHash());
                    NibblePath fullPath = NibblePath.of(fullNibbles);
                    int pathLen = key.path().getNibbles().length;
                    NibblePath suffix = fullPath.slice(pathLen, fullPath.length());
                    byte[] computedHash = COMMITMENTS.commitLeaf(suffix, leaf.valueHash());
                    System.out.println("  Path length: " + pathLen);
                    System.out.println("  Suffix length: " + suffix.getNibbles().length);
                    System.out.println("  Computed hash: " + bytesToHex(computedHash));
                } else if (node instanceof JmtInternalNode) {
                    JmtInternalNode internal = (JmtInternalNode) node;
                    System.out.println("  Type: INTERNAL");
                    System.out.println("  Bitmap: " + Integer.toBinaryString(internal.bitmap()));
                    System.out.println("  Child count: " + internal.childHashes().length);
                }
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
