package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree.CommitResult;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug test to compare hash computation.
 */
class DebugHashTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void debugSingleInsert() throws Exception {
        // Reference implementation
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        // New implementation
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Same update
            Map<byte[], byte[]> updates = new HashMap<>();
            updates.put("key1".getBytes(), "value1".getBytes());

            // Commit to both
            CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            // Create error message with hash values
            String refHash = bytesToHex(refResult.rootHash());
            String v2Hash = bytesToHex(v2Result.rootHash());
            byte[] keyHash = HASH.digest("key1".getBytes());
            byte[] valueHash = HASH.digest("value1".getBytes());

            String message = String.format("\nReference root hash: %s\nV2 root hash:        %s\nKey hash:   %s\nValue hash: %s\nMatches: %b",
                refHash, v2Hash, bytesToHex(keyHash), bytesToHex(valueHash), refHash.equals(v2Hash));

            if (!refHash.equals(v2Hash)) {
                throw new AssertionError(message);
            } else {
                System.out.println("SUCCESS: " + message);
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
