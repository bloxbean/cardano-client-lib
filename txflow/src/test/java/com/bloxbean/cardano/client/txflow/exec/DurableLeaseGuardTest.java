package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DurableLeaseGuardTest {
    @Test
    void renewalCanRunBeforeAnyResourceLeaseIsAcquired() throws Exception {
        FlowExecutionStore store = mock(FlowExecutionStore.class);
        Instant expiry = Instant.now().plusSeconds(1);
        ExecutionLease lease = new ExecutionLease("execution", "owner", 1, expiry);
        CountDownLatch renewed = new CountDownLatch(1);
        when(store.acquireExecutionLease(any(), any(), any(), any())).thenReturn(lease);
        when(store.renewExecutionLease(any(), any(), any())).thenAnswer(invocation -> {
            renewed.countDown();
            return lease;
        });
        DurableLeaseGuard guard = new DurableLeaseGuard(store, Clock.systemUTC(),
                Duration.ofMillis(60), Runnable::run);

        guard.acquireExecution("execution", "owner");
        guard.startRenewal();

        assertTrue(renewed.await(1, TimeUnit.SECONDS));
        guard.close();
        verify(store).releaseExecutionLease(lease);
    }

    @Test
    void renewalDispatchRejectionIsRetainedAndFencesFurtherWork() throws Exception {
        FlowExecutionStore store = mock(FlowExecutionStore.class);
        ExecutionLease lease = new ExecutionLease(
                "execution", "owner", 1, Instant.now().plusSeconds(1));
        when(store.acquireExecutionLease(any(), any(), any(), any())).thenReturn(lease);
        CountDownLatch dispatchAttempted = new CountDownLatch(1);
        RejectedExecutionException rejection =
                new RejectedExecutionException("maintenance executor stopped");
        DurableLeaseGuard guard = new DurableLeaseGuard(store, Clock.systemUTC(),
                Duration.ofMillis(30), command -> {
                    dispatchAttempted.countDown();
                    throw rejection;
                });

        guard.acquireExecution("execution", "owner");
        guard.startRenewal();

        assertTrue(dispatchAttempted.await(1, TimeUnit.SECONDS));
        for (int i = 0; i < 100 && !guard.hasFailed(); i++) {
            Thread.yield();
        }
        assertTrue(guard.hasFailed());
        assertSame(rejection, assertThrows(RejectedExecutionException.class, guard::checkHealthy));
        assertSame(rejection, assertThrows(RejectedExecutionException.class, guard::fence));

        guard.close();
        guard.close();
        verify(store, times(1)).releaseExecutionLease(lease);
    }
}
