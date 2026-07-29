package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.compile.CompiledTxFlow;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Iteration 2c review gap: the fan-out bootstrap splits the wallet EXACTLY ONCE
 * across two stream instances sharing a real {@link FlowEngine}'s durable
 * execution store — proving double-split-impossibility through the engine's own
 * idempotency claim (not the {@code StubEngineGateway}'s simulated MATCH). The
 * split submission is counted at the transaction-execution seam.
 */
class TxFlowStreamPartitionedRealEngineTest {
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final String FUNDING = "addr_test1vpqfunding";
    private static final String LANE_0 = "addr_test1vpqlane0";
    private static final String LANE_1 = "addr_test1vpqlane1";

    @Test
    void aRealEngineSplitsTheWalletExactlyOnceAcrossTwoStreamInstancesSharingItsDurableStore()
            throws Exception {
        AtomicInteger splitSubmissions = new AtomicInteger();
        // One durable execution store shared by both engine instances = a restart /
        // a second stream instance against the same durable engine truth.
        InMemoryFlowExecutionStore executionStore =
                new InMemoryFlowExecutionStore(Clock.fixed(NOW, ZoneOffset.UTC));

        // A compiler + tx-context seam that "submits" (counting) and confirms
        // synchronously — the engine reaches COMPLETED without a real backend.
        Transaction split = splitTransaction();
        String hash = TransactionUtil.getTxHash(split);
        TxFlowCompiler compiler = countingSplitCompiler(split, hash, splitSubmissions);
        ChainDataSupplier chain = mock(ChainDataSupplier.class);
        when(chain.getTransactionInfo(hash)).thenReturn(Optional.of(
                TransactionInfo.builder().txHash(hash).blockHeight(10L).build()));

        FlowEngine engine = FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class), mock(TransactionProcessor.class), chain)
                .executor(Runnable::run).maintenanceExecutor(Runnable::run)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC)).store(executionStore)
                .compiler(compiler).build();

        // Instance 1 runs the split.
        try (TxFlowStream first = partitionedOn(engine).build()) {
            first.start();
            assertEquals(BootstrapReport.Outcome.RAN, first.bootstrap().outcome());
        }
        // Instance 2 over the SAME engine matches the stored split — no re-split.
        try (TxFlowStream second = partitionedOn(engine).build()) {
            second.start();
            assertEquals(BootstrapReport.Outcome.MATCHED, second.bootstrap().outcome());
        }

        assertEquals(1, splitSubmissions.get(),
                "the real engine's idempotency claim splits the funding wallet exactly once");
    }

    // ---- helpers ----

    private TxFlowStream.Builder partitionedOn(FlowEngine engine) {
        PartitionedLanes config = PartitionedLanes.fromAddress(FUNDING)
                .laneAddresses(List.of(LANE_0, LANE_1))
                .seedPerLane(Amount.ada(10))
                .build();
        return new TxFlowStream.Builder("payouts", new FlowEngineGateway(engine))
                .lanes(LanePolicy.partitioned(config))
                .executor(Runnable::run)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * A compiler whose compiled flow is a single step that "submits" the split
     * (counting) through a mock {@code TxContext} and confirms via the injected
     * chain data — reused for every compile so the actual bootstrap definition is
     * irrelevant; the engine idempotency claim is what dedupes the second run.
     */
    private TxFlowCompiler countingSplitCompiler(Transaction transaction, String hash,
                                                 AtomicInteger submissions) {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        AtomicReference<Consumer<Transaction>> inspector = new AtomicReference<>();
        when(txContext.withTxInspector(any())).thenAnswer(invocation -> {
            inspector.set(invocation.getArgument(0));
            return txContext;
        });
        when(txContext.completeAndWait(any(), any(), any())).thenAnswer(invocation -> {
            submissions.incrementAndGet();
            inspector.get().accept(transaction);
            Result<String> accepted = Result.success("submitted");
            accepted.withValue(hash);
            return TxResult.fromResult(accepted);
        });
        TxFlow executable = TxFlow.builder("bootstrap-split")
                .addStep(FlowStep.builder(StreamIdentities.GENERATED_STEP_ID)
                        .withTxContext(ignored -> txContext).build())
                .build();

        TxFlowCompiler compiler = mock(TxFlowCompiler.class);
        FlowCompilationResult compilation = mock(FlowCompilationResult.class);
        CompiledTxFlow compiled = mock(CompiledTxFlow.class);
        when(compiler.compile(any())).thenReturn(compilation);
        when(compilation.hasErrors()).thenReturn(false);
        when(compilation.requireCompiledFlow()).thenReturn(compiled);
        when(compiled.getExecutionPlan()).thenReturn(executable);
        when(compiled.getFingerprint()).thenReturn("bootstrap-fingerprint");
        when(compiled.getSpendingResources()).thenReturn(Set.of());
        when(compiled.getExplicitConsumers()).thenReturn(Map.of());
        return compiler;
    }

    private Transaction splitTransaction() {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput("00".repeat(32), 0)))
                        .fee(BigInteger.ZERO)
                        .build())
                .witnessSet(TransactionWitnessSet.builder().build())
                .isValid(true)
                .build();
    }
}
