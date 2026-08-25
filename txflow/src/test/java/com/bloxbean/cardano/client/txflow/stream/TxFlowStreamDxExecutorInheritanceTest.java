package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.compile.CompiledTxFlow;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TxFlowStreamDxExecutorInheritanceTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void omittedExecutorInheritsGatewayDispatcherAndExplicitExecutorWins() {
        StubEngineGateway inheritedGateway = new StubEngineGateway();
        CountingExecutor inherited = new CountingExecutor();
        inheritedGateway.executionExecutor = inherited;
        try (TxFlowStream stream = builder(inheritedGateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("inherited"));
            inheritedGateway.lastHandle().completeConfirmed(STEP_ID, "tx-inherited");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertTrue(inherited.count.get() > 0);
        }

        StubEngineGateway explicitGateway = new StubEngineGateway();
        CountingExecutor gatewayExecutor = new CountingExecutor();
        CountingExecutor explicit = new CountingExecutor();
        explicitGateway.executionExecutor = gatewayExecutor;
        try (TxFlowStream stream = builder(explicitGateway).executor(explicit).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("explicit"));
            explicitGateway.lastHandle().completeConfirmed(STEP_ID, "tx-explicit");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertTrue(explicit.count.get() > 0);
            assertEquals(0, gatewayExecutor.count.get());
        }
    }

    @Test
    void missingGatewayDispatcherFailsWithTeachingMessage() {
        StubEngineGateway gateway = new StubEngineGateway();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> builder(gateway).build());

        assertTrue(failure.getMessage().contains("executor(Executor)"));
        assertTrue(failure.getMessage().contains("FlowEngine"));
    }

    @Test
    void streamCloseDoesNotShutDownInheritedExecutor() {
        StubEngineGateway gateway = new StubEngineGateway();
        DirectExecutorService inherited = new DirectExecutorService();
        gateway.executionExecutor = inherited;
        TxFlowStream stream = builder(gateway).build();
        stream.start();
        stream.close();

        assertFalse(inherited.isShutdown());
        inherited.shutdown();
    }

    @Test
    void streamAndEngineCanShareOneThreadWithoutDispatchDeadlock() throws Exception {
        ExecutorService shared = Executors.newSingleThreadExecutor();
        FlowEngine engine = FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class),
                        mock(TransactionProcessor.class),
                        mock(ChainDataSupplier.class))
                .executor(shared)
                .compiler(failingExecutionCompiler())
                .build();

        try (TxFlowStream stream = TxFlowStream.builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("shared"));

            assertEquals(TxStreamItemStatus.FAILED,
                    receipt.completion().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS).getStatus(),
                    "stream dispatch must return before engine completion scheduled on the same pool");
        } finally {
            shared.shutdownNow();
        }
    }

    @Test
    void inheritedScheduledExecutorDoesNotBecomeStreamMaintenanceImplicitly() {
        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.executionExecutor = scheduled;
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> builder(gateway)
                            .window(WindowPolicy.countOrTime(2, Duration.ofSeconds(1)))
                            .build());
            assertTrue(failure.getMessage().contains("maintenanceExecutor"));
        } finally {
            scheduled.shutdownNow();
        }
    }

    private TxFlowStream.Builder builder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private static TxWorkItem planItem(String itemId) {
        TxPlan plan = TxPlan.from(new Tx()
                .payToAddress(RECEIVER, Amount.ada(1.5))
                .from(SENDER));
        return TxWorkItem.fromTxPlan(itemId, plan);
    }

    private static TxFlowCompiler failingExecutionCompiler() {
        TxFlow executable = TxFlow.builder("shared-executor-probe")
                .addStep(FlowStep.builder(STEP_ID)
                        .withTxContext(ignored -> {
                            throw new IllegalStateException("expected engine task failure");
                        })
                        .build())
                .build();
        TxFlowCompiler compiler = mock(TxFlowCompiler.class);
        FlowCompilationResult compilation = mock(FlowCompilationResult.class);
        CompiledTxFlow compiled = mock(CompiledTxFlow.class);
        when(compiler.compile(any())).thenReturn(compilation);
        when(compilation.hasErrors()).thenReturn(false);
        when(compilation.requireCompiledFlow()).thenReturn(compiled);
        when(compiled.getExecutionPlan()).thenReturn(executable);
        when(compiled.getFingerprint()).thenReturn("shared-executor-fingerprint");
        when(compiled.getSpendingResources()).thenReturn(Set.of());
        when(compiled.getExplicitConsumers()).thenReturn(Map.of());
        return compiler;
    }

    private static final class CountingExecutor implements Executor {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            count.incrementAndGet();
            command.run();
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new IllegalStateException("executor is shut down");
            }
            command.run();
        }
    }
}
