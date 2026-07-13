package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
}
