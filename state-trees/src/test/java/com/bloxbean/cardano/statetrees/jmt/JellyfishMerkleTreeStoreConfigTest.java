package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JellyfishMerkleTreeStoreConfigTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);

    @Test
    void commitResultRespectsLimits() {
        JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                .resultNodeLimit(0)
                .resultStaleLimit(0)
                .build();

        JellyfishMerkleTreeStore store = new JellyfishMerkleTreeStore(new InMemoryJmtStore(), COMMITMENTS, HASH,
                JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("k1"), bytes("v1"));
        updates.put(bytes("k2"), bytes("v2"));

        JellyfishMerkleTree.CommitResult result = store.commit(1, updates);
        assertEquals(0, result.nodes().size(), "node details should be omitted");
        assertEquals(0, result.staleNodes().size(), "stale nodes should be omitted");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
