package com.bloxbean.cardano.vds.jmt.store;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmtAccessCoordinatorTest {

    @Test
    void secondWriterFailsFast() throws Exception {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (JmtAccessLease ignored = coordinator.tryAcquireUpdate("writer-a", 1)) {
            Future<JmtConcurrentMutationException> future = executor.submit(() ->
                    assertThrows(JmtConcurrentMutationException.class,
                            () -> coordinator.tryAcquireUpdate("writer-b", 1)));

            JmtConcurrentMutationException conflict = future.get(5, TimeUnit.SECONDS);
            assertEquals(JmtAccessMode.UPDATE, conflict.requestedMode());
            assertEquals("writer-b", conflict.requestedOperation());
            assertTrue(conflict.getMessage().contains("writer-a"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void proofReadCanOverlapCopyOnWriteUpdate() throws Exception {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (JmtAccessLease ignored = coordinator.tryAcquireUpdate("put", 2)) {
            Future<Boolean> future = executor.submit(() -> {
                try (JmtAccessLease read = coordinator.tryAcquireRead("getProof", 1)) {
                    return read.mode() == JmtAccessMode.READ;
                }
            });
            assertTrue(future.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void maintenanceExcludesReadersAndWriters() throws Exception {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (JmtAccessLease ignored = coordinator.tryAcquireMaintenance("truncateAfter", 10)) {
            Future<JmtConcurrentMutationException> read = executor.submit(() ->
                    assertThrows(JmtConcurrentMutationException.class,
                            () -> coordinator.tryAcquireRead("getProof", 5)));
            Future<JmtConcurrentMutationException> update = executor.submit(() ->
                    assertThrows(JmtConcurrentMutationException.class,
                            () -> coordinator.tryAcquireUpdate("put", 11)));

            assertEquals(JmtAccessMode.READ, read.get(5, TimeUnit.SECONDS).requestedMode());
            assertEquals(JmtAccessMode.UPDATE, update.get(5, TimeUnit.SECONDS).requestedMode());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void activeReaderRejectsMaintenance() throws Exception {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (JmtAccessLease ignored = coordinator.tryAcquireRead("integrity-check")) {
            Future<JmtConcurrentMutationException> future = executor.submit(() ->
                    assertThrows(JmtConcurrentMutationException.class,
                            () -> coordinator.tryAcquireMaintenance("pruneUpTo", 3)));
            assertEquals(JmtAccessMode.MAINTENANCE,
                    future.get(5, TimeUnit.SECONDS).requestedMode());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void leasesAreReentrantAndBalancedForOwner() {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        try (JmtAccessLease outer = coordinator.tryAcquireUpdate("put", 1)) {
            try (JmtAccessLease inner = coordinator.tryAcquireUpdate("commit", 1)) {
                assertTrue(coordinator.isHeldByCurrentThread(JmtAccessMode.UPDATE));
            }
            assertTrue(coordinator.isHeldByCurrentThread(JmtAccessMode.UPDATE));
        }

        assertDoesNotThrow(() -> {
            try (JmtAccessLease ignored = coordinator.tryAcquireUpdate("next", 2)) {
                assertTrue(coordinator.isHeldByCurrentThread(JmtAccessMode.UPDATE));
            }
        });
    }

    @Test
    void sameThreadReadToUpdateUpgradeFailsFast() {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        try (JmtAccessLease ignored = coordinator.tryAcquireRead("getProof", 1)) {
            JmtConcurrentMutationException conflict = assertThrows(
                    JmtConcurrentMutationException.class,
                    () -> coordinator.tryAcquireUpdate("put", 2));
            assertEquals(JmtAccessMode.UPDATE, conflict.requestedMode());
            assertTrue(conflict.getMessage().contains("getProof"));
        }
    }

    @Test
    void sameThreadUpdateToReadNestingFailsFast() {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        try (JmtAccessLease ignored = coordinator.tryAcquireUpdate("put", 2)) {
            JmtConcurrentMutationException conflict = assertThrows(
                    JmtConcurrentMutationException.class,
                    () -> coordinator.tryAcquireRead("getProof", 1));
            assertEquals(JmtAccessMode.READ, conflict.requestedMode());
            assertTrue(conflict.getMessage().contains("put"));
        }
    }

    @Test
    void leaseCannotBeClosedByAnotherThread() throws Exception {
        JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        JmtAccessLease lease = coordinator.tryAcquireUpdate("put", 1);
        try {
            Future<IllegalStateException> future = executor.submit(() ->
                    assertThrows(IllegalStateException.class, lease::close));
            assertTrue(future.get(5, TimeUnit.SECONDS).getMessage().contains("acquiring thread"));
            assertTrue(coordinator.isHeldByCurrentThread(JmtAccessMode.UPDATE));
        } finally {
            lease.close();
            executor.shutdownNow();
        }
    }
}
