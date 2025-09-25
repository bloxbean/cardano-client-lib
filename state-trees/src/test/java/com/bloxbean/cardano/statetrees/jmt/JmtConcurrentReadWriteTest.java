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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class JmtConcurrentReadWriteTest {

    @Test
    void concurrent_gets_while_committing_should_be_safe_and_correct() throws Exception {
        HashFunction hash = Blake2b256::digest;
        CommitmentScheme commitments = new MpfCommitmentScheme(hash);
        JmtStore backend = new InMemoryJmtStore();
        JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                .enableNodeCache(true).nodeCacheSize(1024)
                .enableValueCache(true).valueCacheSize(1024)
                .build();
        JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, commitments, hash,
                JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

        byte[] key = "hotKey".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicLong writerVersion = new AtomicLong(0L);
        CountDownLatch start = new CountDownLatch(1);

        var readerPool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            readerPool.submit(() -> {
                try {
                    start.await();
                    while (writerVersion.get() < 1000L) {
                        try {
                            tree.get(key); // latest read
                        } catch (Throwable t) {
                            failed.set(true);
                            break;
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread writer = new Thread(() -> {
            try {
                start.countDown();
                long v = 0L;
                for (int i = 1; i <= 1000; i++) {
                    v = i;
                    Map<byte[], byte[]> updates = new LinkedHashMap<>();
                    updates.put(key, ("v" + i).getBytes(StandardCharsets.UTF_8));
                    tree.commit(v, updates);
                    writerVersion.set(v);
                }
            } catch (Throwable t) {
                failed.set(true);
            }
        });

        writer.start();
        writer.join();
        readerPool.shutdown();
        readerPool.awaitTermination(5, TimeUnit.SECONDS);

        assertFalse(failed.get(), "Reader/Writer threads encountered an exception");
        byte[] latest = tree.get(key);
        assertArrayEquals("v1000".getBytes(StandardCharsets.UTF_8), latest);
        assertEquals(1000L, tree.latestVersion().orElse(-1L));
    }
}

