package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JellyfishMerkleTreeStoreCacheTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    private TestCountingStore backend;
    private JellyfishMerkleTreeStore tree;

    @BeforeEach
    void setUp() {
        backend = new TestCountingStore();
        JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                .enableNodeCache(true)
                .nodeCacheSize(64)
                .enableValueCache(true)
                .valueCacheSize(64)
                .build();
        tree = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH,
                JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("alice"), bytes("100"));
        tree.commit(1, updates);
    }

    @Test
    void getUsesValueCache() {
        backend.resetCounters();
        byte[] key = bytes("alice");

        assertNotNull(tree.get(key));
        long afterFirstGet = backend.valueLookups;

        assertNotNull(tree.get(key));
        assertEquals(afterFirstGet, backend.valueLookups, "cached get should avoid store call");
    }

    @Test
    void proofUsesNodeCache() {
        backend.resetCounters();
        byte[] key = bytes("alice");

        assertTrue(tree.getProof(key, 1).isPresent());
        long afterFirstProof = backend.nodeLookups;

        assertTrue(tree.getProof(key, 1).isPresent());
        assertEquals(afterFirstProof, backend.nodeLookups, "cached proof should avoid additional node lookups");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
