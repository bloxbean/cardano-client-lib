package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.client.quicktx.signing.SignerScopes;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.stream.TxFlowStream;
import com.bloxbean.cardano.hdwallet.Wallet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowRuntimeTest {

    @Test
    void builderCreatesOneOrdinaryEngineAndStartedOrdinaryStreams() {
        try (FlowRuntime runtime = FlowRuntime.builder(backend()).name("payments").build()) {
            FlowEngine engine = runtime.engine();

            try (TxFlowStream stream = runtime.open("payouts")) {
                assertSame(engine.executionExecutor(), runtime.engine().executionExecutor());
                assertNotNull(stream);
            }
        }
    }

    @Test
    void taskExecutorMatchesTheRunningJavaCapabilityAndUsesDeterministicNames()
            throws Exception {
        ExecutorService taskExecutor = FlowRuntime.RuntimeExecutors
                .taskExecutor("probe", 3);
        ScheduledExecutorService maintenance = FlowRuntime.RuntimeExecutors
                .maintenanceExecutor("probe", 2);
        try {
            Thread taskThread = taskExecutor.submit(Thread::currentThread).get(1, TimeUnit.SECONDS);
            Thread maintenanceThread = maintenance.submit(Thread::currentThread)
                    .get(1, TimeUnit.SECONDS);

            assertTrue(taskThread.getName().startsWith("txflow-runtime-probe-task-"));
            assertTrue(maintenanceThread.getName()
                    .startsWith("txflow-runtime-probe-maintenance-"));
            assertFalse(maintenanceThread.isDaemon());
            assertFalse(isVirtual(maintenanceThread));
            if (Runtime.version().feature() >= 21) {
                assertTrue(isVirtual(taskThread));
            } else {
                assertFalse(taskThread.isDaemon());
                assertFalse(isVirtual(taskThread));
                assertEquals(3, ((ThreadPoolExecutor) taskExecutor).getCorePoolSize());
            }
        } finally {
            maintenance.shutdownNow();
            taskExecutor.shutdownNow();
        }
    }

    @Test
    void java17DefaultPoolSizeIsBoundedByTheDocumentedFormula() {
        if (Runtime.version().feature() >= 21) {
            return;
        }
        try (FlowRuntime runtime = FlowRuntime.builder(backend()).build()) {
            ThreadPoolExecutor executor =
                    (ThreadPoolExecutor) runtime.engine().executionExecutor();
            assertEquals(Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors())),
                    executor.getCorePoolSize());
        }
    }

    @Test
    void builderSurfaceValidatesNamesCountsSchemesAndDuplicateSignerRefs() {
        FlowRuntime.Builder builder = FlowRuntime.builder(backend());
        assertThrows(IllegalArgumentException.class, () -> builder.name("bad name"));
        assertThrows(IllegalArgumentException.class, () -> builder.taskParallelism(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maintenanceThreads(-1));
        assertThrows(IllegalArgumentException.class,
                () -> builder.account("wallet://sender", mock(Account.class)));
        assertThrows(IllegalArgumentException.class,
                () -> builder.wallet("account://sender", mock(Wallet.class)));

        FlowRuntime.Builder duplicate = FlowRuntime.builder(backend())
                .account("account://sender", mock(Account.class));
        assertThrows(IllegalArgumentException.class,
                () -> duplicate.account("account://sender", mock(Account.class)));
    }

    @Test
    void accountAndWalletRegistrationsPopulateTheUnderlyingEngineRegistry() throws Exception {
        Account account = new Account();
        Wallet wallet = mock(Wallet.class);
        try (FlowRuntime runtime = FlowRuntime.builder(backend())
                .account("account://sender", account)
                .wallet("wallet://treasury", wallet)
                .build()) {
            Field registryField = FlowEngine.class.getDeclaredField("signerRegistry");
            registryField.setAccessible(true);
            SignerRegistry registry = (SignerRegistry) registryField.get(runtime.engine());

            var accountBinding = registry.resolve("account://sender").orElseThrow();
            assertEquals(account.baseAddress(), accountBinding.preferredAddress().orElseThrow());
            assertNotNull(accountBinding.signerFor(SignerScopes.PAYMENT));
            assertSame(wallet, registry.resolve("wallet://treasury").orElseThrow()
                    .asWallet().orElseThrow());
        }
    }

    @Test
    void failedOpenIsNeverTrackedAndCloseStillShutsOwnedExecutors() {
        ExecutorService tasks = mock(ExecutorService.class);
        ScheduledExecutorService maintenance = mock(ScheduledExecutorService.class);
        IllegalStateException startupFailure = new IllegalStateException("startup failed");
        FlowRuntime runtime = FlowRuntime.forTesting(engine(), tasks, maintenance,
                (streamId, engine, scheduler) -> {
                    throw startupFailure;
                });

        assertSame(startupFailure, assertThrows(IllegalStateException.class,
                () -> runtime.open("payouts")));
        runtime.close();

        verify(maintenance).shutdown();
        verify(tasks).shutdown();
    }

    @Test
    void closeVisitsStreamsInReverseOrderBeforeBothExecutors() {
        ExecutorService tasks = mock(ExecutorService.class);
        ScheduledExecutorService maintenance = mock(ScheduledExecutorService.class);
        TxFlowStream first = mock(TxFlowStream.class);
        TxFlowStream second = mock(TxFlowStream.class);
        List<String> order = new ArrayList<>();
        AtomicReference<TxFlowStream> next = new AtomicReference<>(first);
        FlowRuntime runtime = FlowRuntime.forTesting(engine(), tasks, maintenance,
                (streamId, engine, scheduler) -> next.getAndSet(second));
        doAnswer(ignored -> order.add("first")).when(first).close();
        doAnswer(ignored -> order.add("second")).when(second).close();
        doAnswer(ignored -> order.add("maintenance")).when(maintenance).shutdown();
        doAnswer(ignored -> order.add("tasks")).when(tasks).shutdown();

        runtime.open("first");
        runtime.open("second");
        runtime.close();
        runtime.close();

        assertEquals(List.of("second", "first", "maintenance", "tasks"), order);
    }

    @Test
    void closePreservesTheFirstFailureAndSuppressesEveryLaterFailure() {
        ExecutorService tasks = mock(ExecutorService.class);
        ScheduledExecutorService maintenance = mock(ScheduledExecutorService.class);
        TxFlowStream first = mock(TxFlowStream.class);
        TxFlowStream second = mock(TxFlowStream.class);
        IllegalStateException secondFailure = new IllegalStateException("second");
        IllegalArgumentException firstFailure = new IllegalArgumentException("first");
        IllegalStateException maintenanceFailure = new IllegalStateException("maintenance");
        IllegalStateException taskFailure = new IllegalStateException("task");
        AtomicReference<TxFlowStream> next = new AtomicReference<>(first);
        FlowRuntime runtime = FlowRuntime.forTesting(engine(), tasks, maintenance,
                (streamId, engine, scheduler) -> next.getAndSet(second));
        doThrow(firstFailure).when(first).close();
        doThrow(secondFailure).when(second).close();
        doThrow(maintenanceFailure).when(maintenance).shutdown();
        doThrow(taskFailure).when(tasks).shutdown();
        runtime.open("first");
        runtime.open("second");

        RuntimeException thrown = assertThrows(RuntimeException.class, runtime::close);

        assertSame(secondFailure, thrown, "reverse order makes the second stream primary");
        assertEquals(List.of(firstFailure, maintenanceFailure, taskFailure),
                List.of(thrown.getSuppressed()));
    }

    @Test
    void concurrentOpenAndCloseCannotLeakAnUntrackedLiveStream() throws Exception {
        ExecutorService tasks = mock(ExecutorService.class);
        ScheduledExecutorService maintenance = mock(ScheduledExecutorService.class);
        TxFlowStream stream = mock(TxFlowStream.class);
        CountDownLatch openerEntered = new CountDownLatch(1);
        CountDownLatch releaseOpen = new CountDownLatch(1);
        FlowRuntime runtime = FlowRuntime.forTesting(engine(), tasks, maintenance,
                (streamId, engine, scheduler) -> {
                    openerEntered.countDown();
                    try {
                        assertTrue(releaseOpen.await(1, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    return stream;
                });
        AtomicReference<TxFlowStream> opened = new AtomicReference<>();
        Thread openThread = new Thread(() -> opened.set(runtime.open("payouts")));
        Thread closeThread = new Thread(runtime::close);

        openThread.start();
        assertTrue(openerEntered.await(1, TimeUnit.SECONDS));
        closeThread.start();
        releaseOpen.countDown();
        openThread.join(1_000);
        closeThread.join(1_000);

        assertSame(stream, opened.get());
        verify(stream).close();
        verify(maintenance).shutdown();
        verify(tasks).shutdown();
        assertThrows(IllegalStateException.class, () -> runtime.open("too-late"));
    }

    @Test
    void concurrentCloseWaitsForTheSingleCleanupPass() throws Exception {
        ExecutorService tasks = mock(ExecutorService.class);
        ScheduledExecutorService maintenance = mock(ScheduledExecutorService.class);
        TxFlowStream stream = mock(TxFlowStream.class);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        doAnswer(ignored -> {
            closeEntered.countDown();
            assertTrue(releaseClose.await(1, TimeUnit.SECONDS));
            return null;
        }).when(stream).close();
        FlowRuntime runtime = FlowRuntime.forTesting(engine(), tasks, maintenance,
                (streamId, engine, scheduler) -> stream);
        runtime.open("payouts");
        Thread first = new Thread(runtime::close);
        Thread second = new Thread(runtime::close);

        first.start();
        assertTrue(closeEntered.await(1, TimeUnit.SECONDS));
        second.start();
        assertTrue(second.isAlive(), "the concurrent caller waits for cleanup completion");
        releaseClose.countDown();
        first.join(1_000);
        second.join(1_000);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        verify(stream).close();
        verify(maintenance).shutdown();
        verify(tasks).shutdown();
    }

    @Test
    void closingANestedStreamDoesNotShutRuntimePools() {
        ExecutorService tasks = mock(ExecutorService.class);
        ScheduledExecutorService maintenance = mock(ScheduledExecutorService.class);
        TxFlowStream stream = mock(TxFlowStream.class);
        FlowRuntime runtime = FlowRuntime.forTesting(engine(), tasks, maintenance,
                (streamId, engine, scheduler) -> stream);

        runtime.open("payouts").close();

        verify(tasks, never()).shutdown();
        verify(maintenance, never()).shutdown();
        runtime.close();
    }

    private static boolean isVirtual(Thread thread) throws Exception {
        if (Runtime.version().feature() < 21) {
            return false;
        }
        Method method = Thread.class.getMethod("isVirtual");
        return (boolean) method.invoke(thread);
    }

    private static FlowEngine engine() {
        return FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class),
                        mock(TransactionProcessor.class),
                        mock(ChainDataSupplier.class))
                .executor(Runnable::run)
                .build();
    }

    private static BackendService backend() {
        BackendService backend = mock(BackendService.class);
        when(backend.getUtxoService()).thenReturn(mock(UtxoService.class));
        when(backend.getEpochService()).thenReturn(mock(EpochService.class));
        when(backend.getTransactionService()).thenReturn(mock(TransactionService.class));
        when(backend.getBlockService()).thenReturn(mock(BlockService.class));
        return backend;
    }
}
