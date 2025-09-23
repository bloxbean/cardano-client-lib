package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JellyfishMerkleTreeStorePruneTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);

    private TestCountingStore backend;
    private JellyfishMerkleTreeStore store;

    @BeforeEach
    void setup() {
        backend = new TestCountingStore();
        JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                .enableNodeCache(true).nodeCacheSize(64)
                .enableValueCache(true).valueCacheSize(64)
                .build();
        store = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH,
                JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

        Map<byte[], byte[]> v1 = new LinkedHashMap<>();
        v1.put(bytes("alice"), bytes("100"));
        store.commit(1, v1);

        Map<byte[], byte[]> v2 = new LinkedHashMap<>();
        v2.put(bytes("bob"), bytes("200"));
        store.commit(2, v2);

        Map<byte[], byte[]> v3 = new LinkedHashMap<>();
        v3.put(bytes("alice"), bytes("150"));
        store.commit(3, v3);

        // prime caches
        store.get(bytes("alice"));
        store.getProof(bytes("bob"), 2);
    }

    @Test
    void pruneUpToVersionClearsCaches() {
        backend.resetCounters();
        JellyfishMerkleTreeStore.PruneReport report = store.prune(2);
        assertTrue(report.valueCacheCleared() > 0 || report.cacheEntriesEvicted() > 0,
                "prune should report cache eviction when caches are primed");

        // After prune, caches are cleared so fetching alice again should hit store
        store.get(bytes("alice"));
        assertTrue(backend.valueLookups > 0, "value cache should have been cleared by prune");

        backend.resetCounters();
        store.getProof(bytes("alice"), 3);
        assertTrue(backend.nodeLookups > 0, "node cache should have been invalidated for pruned versions");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
