package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessLease;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessMode;
import com.bloxbean.cardano.vds.jmt.store.JmtConcurrentMutationException;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtWriteConflictException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmtConcurrencyTest {

    private static final HashFunction HASH = Blake2b256::digest;

    @Test
    void twoTreesSharingStoreCannotWriteConcurrently() throws Exception {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree competingTree = new JellyfishMerkleTree(store, HASH);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (JmtAccessLease ignored = store.accessCoordinator().tryAcquireUpdate("writer-a", 0)) {
            Future<JmtConcurrentMutationException> future = executor.submit(() ->
                    assertThrows(JmtConcurrentMutationException.class,
                            () -> competingTree.put(0, Map.of(bytes("key"), bytes("value")))));
            assertTrue(future.get(5, TimeUnit.SECONDS).getMessage().contains("writer-a"));
        } finally {
            executor.shutdownNow();
            store.close();
        }
    }

    @Test
    void proofGenerationCanOverlapUpdateLease() throws Exception {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        byte[] key = bytes("key");
        tree.put(0, Map.of(key, bytes("value")));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (JmtAccessLease ignored = store.accessCoordinator().tryAcquireUpdate("writer", 1)) {
            Future<JmtProof> future = executor.submit(() -> tree.getProof(key, 0).orElseThrow());
            assertEquals(JmtProof.ProofType.INCLUSION,
                    future.get(5, TimeUnit.SECONDS).type());
        } finally {
            executor.shutdownNow();
            store.close();
        }
    }

    @Test
    void maintenanceFailsWhileProofReadLeaseIsActive() throws Exception {
        InMemoryJmtStore store = new InMemoryJmtStore();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (JmtAccessLease ignored = store.accessCoordinator().tryAcquireRead("proof", 0)) {
            Future<JmtConcurrentMutationException> future = executor.submit(() ->
                    assertThrows(JmtConcurrentMutationException.class,
                            () -> store.truncateAfter(0)));
            assertTrue(future.get(5, TimeUnit.SECONDS).getMessage().contains("proof"));
        } finally {
            executor.shutdownNow();
            store.close();
        }
    }

    @Test
    void commitRejectsUnexpectedBaseStateWithoutMutation() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        byte[] root0 = Blake2b256.digest(bytes("root-0"));
        try (JmtStore.CommitBatch batch = store.beginCommit(0, JmtStore.CommitConfig.defaults())) {
            batch.setRootHash(root0);
            batch.commit();
        }

        JmtStore.CommitConfig staleExpectation =
                JmtStore.CommitConfig.expectingLatest(java.util.Optional.empty());
        byte[] root1 = Blake2b256.digest(bytes("root-1"));
        try (JmtStore.CommitBatch batch = store.beginCommit(1, staleExpectation)) {
            batch.setRootHash(root1);
            assertThrows(JmtWriteConflictException.class, batch::commit);
        }

        assertArrayEquals(root0, store.latestRoot().orElseThrow().rootHash());
        assertTrue(store.rootHash(1).isEmpty());
        store.close();
    }

    @Test
    void commitBatchRejectsCrossThreadUseWithoutLeakingLease() throws Exception {
        InMemoryJmtStore store = new InMemoryJmtStore();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        JmtStore.CommitBatch batch = store.beginCommit(0, JmtStore.CommitConfig.defaults());
        try {
            Future<IllegalStateException> future = executor.submit(() ->
                    assertThrows(IllegalStateException.class, batch::close));
            assertTrue(future.get(5, TimeUnit.SECONDS).getMessage().contains("creating thread"));
        } finally {
            batch.close();
            executor.shutdownNow();
        }

        try (JmtAccessLease ignored = store.accessCoordinator().tryAcquireUpdate("next", 1)) {
            assertTrue(store.accessCoordinator().isHeldByCurrentThread(
                    JmtAccessMode.UPDATE));
        }
        store.close();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
