package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issues commits from multiple threads with monotonically increasing versions.
 * Caches are disabled to avoid concurrency on cache structures (single-writer policy).
 */
class JmtConcurrentCommitsMonotonicTest {

    @Test
    void multi_thread_commits_with_monotonic_versions_succeed() throws Exception {
        HashFunction hash = Blake2b256::digest;
        CommitmentScheme commitments = new MpfCommitmentScheme(hash);
        JmtStore backend = new InMemoryJmtStore();
        JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                .enableNodeCache(false)
                .enableValueCache(false)
                .build();
        JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, commitments, hash,
                JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

        int threads = 4;
        int commitsPerThread = 500;
        int totalCommits = threads * commitsPerThread;
        AtomicLong version = new AtomicLong(tree.latestVersion().orElse(0L));
        AtomicLong keySeq = new AtomicLong(0);
        AtomicInteger failures = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);
        Object commitLock = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < commitsPerThread; i++) {
                        long ks = keySeq.incrementAndGet();
                        Map<byte[], byte[]> updates = new LinkedHashMap<>();
                        updates.put(("k-" + ks).getBytes(StandardCharsets.UTF_8),
                                ("val-" + ks).getBytes(StandardCharsets.UTF_8));
                        synchronized (commitLock) {
                            long v = tree.latestVersion().orElse(0L) + 1;
                            version.set(v);
                            tree.commit(v, updates);
                        }
                    }
                } catch (Throwable t1) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(0, failures.get(), "Commit workers encountered failures");
        assertEquals(totalCommits, tree.latestVersion().orElse(-1L), "Latest version should match total commits");

        // Spot check a few keys
        for (long i = keySeq.get(); i > keySeq.get() - 5 && i > 0; i--) {
            byte[] val = tree.get(("k-" + i).getBytes(StandardCharsets.UTF_8));
            assertArrayEquals(("val-" + i).getBytes(StandardCharsets.UTF_8), val);
        }
    }
}
