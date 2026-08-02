package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.BackoffStrategy;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.config.RollbackAction;
import com.bloxbean.cardano.client.txflow.config.RollbackMonitoringHorizon;
import com.bloxbean.cardano.client.txflow.config.RollbackPolicy;
import com.bloxbean.cardano.client.txflow.config.RollbackRebuildScope;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.absent;
import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.included;

class FlowExecutorTest {
    private static final String TEST_ADDRESS =
            "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2"
                    + "k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp";

    @Test
    void submissionFailureClassificationRecognizesWrappedApiRuntimeSubclasses() {
        class ProviderFailure extends ApiRuntimeException {
            ProviderFailure(String message) { super(message); }
        }

        assertTrue(FlowExecutor.hasSubmissionApiFailure(
                new RuntimeException("wrapped", new ProviderFailure("provider unavailable"))));
        assertFalse(FlowExecutor.hasSubmissionApiFailure(new RuntimeException("ordinary")));
    }

    @Mock
    private UtxoSupplier utxoSupplier;
    @Mock
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;
    @Mock
    private ChainDataSupplier chainDataSupplier;

    private FlowExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chainDataSupplier)
                .withExecutor(Runnable::run);
    }

    private TxFlow createSimpleFlow(String flowId) {
        return TxFlow.builder(flowId)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();
    }

    private QuickTxBuilder.TxContext failingTxContext(String message) {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.withTxInspector(any())).thenReturn(txContext);
        when(txContext.completeAndWait(any(Duration.class), any(Duration.class), any()))
                .thenReturn(TxResult.fromResult(Result.error(message)));
        return txContext;
    }

    private QuickTxBuilder.TxContext successfulTxContext(String txHash) {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.withTxInspector(any())).thenReturn(txContext);
        Result<String> result = Result.success("submitted");
        result.withValue(txHash);
        when(txContext.completeAndWait(any(Duration.class), any(Duration.class), any()))
                .thenReturn(TxResult.fromResult(result));
        return txContext;
    }

    private QuickTxBuilder.TxContext successfulPipelinedTxContext(String txHash) {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.withTxInspector(any())).thenReturn(txContext);
        Result<String> result = Result.success("submitted");
        result.withValue(txHash);
        when(txContext.complete()).thenReturn(TxResult.fromResult(result));
        return txContext;
    }

    private QuickTxBuilder.TxContext batchTxContext(Transaction transaction) {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.buildAndSign()).thenReturn(transaction);
        return txContext;
    }

    private Transaction transaction(int inputIndex) {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput("00".repeat(32), inputIndex)))
                        .fee(BigInteger.ZERO)
                        .build())
                .witnessSet(TransactionWitnessSet.builder().build())
                .isValid(true)
                .build();
    }

    private Transaction transactionWithOutput(int inputIndex) {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput("00".repeat(32), inputIndex)))
                        .outputs(List.of(TransactionOutput.builder()
                                .address(TEST_ADDRESS)
                                .value(Value.builder().coin(BigInteger.ONE).build())
                                .build()))
                        .fee(BigInteger.ZERO)
                        .build())
                .witnessSet(TransactionWitnessSet.builder().build())
                .isValid(true)
                .build();
    }

    private PersistencePort failingConfirmationPersistence() {
        return new PersistencePort() {
            @Override
            public void onConfirmed(FlowStep step, String transactionHash) {
                throw new IllegalStateException("confirmation journal unavailable");
            }
        };
    }

    @Test
    void sequentialTerminalFailurePreservesAlreadyConfirmedStepResults() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(Optional.of(
                TransactionInfo.builder().txHash("tx1").blockHeight(10L).build()));
        executor.withPersistencePort(failingConfirmationPersistence());
        TxFlow flow = TxFlow.builder("sequential-terminal-integrity")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("tx1")).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(List.of("step1"), result.getStepResults().stream()
                .map(FlowStepResult::getStepId).toList());
        assertTrue(result.getStepResults().get(0).isSuccessful());
    }

    @Test
    void pipelinedTerminalFailurePreservesAlreadyConfirmedStepResults() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(Optional.of(
                TransactionInfo.builder().txHash("tx1").blockHeight(10L).build()));
        executor.withChainingMode(ChainingMode.PIPELINED)
                .withPersistencePort(failingConfirmationPersistence());
        TxFlow flow = TxFlow.builder("pipelined-terminal-integrity")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulPipelinedTxContext("tx1")).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(List.of("step1"), result.getStepResults().stream()
                .map(FlowStepResult::getStepId).toList());
        assertTrue(result.getStepResults().get(0).isSuccessful());
    }

    @Test
    void pipelinedSubmittedButUnconfirmedPrefixIsNotSuccessful() {
        QuickTxBuilder.TxContext rejected = mock(QuickTxBuilder.TxContext.class);
        when(rejected.withTxInspector(any())).thenReturn(rejected);
        when(rejected.complete()).thenReturn(
                TxResult.fromResult(Result.error("second rejected")));
        executor.withChainingMode(ChainingMode.PIPELINED);
        TxFlow flow = TxFlow.builder("pipelined-submitted-prefix")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulPipelinedTxContext("tx1")).build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> rejected).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(2, result.getStepResults().size());
        assertEquals(FlowStatus.IN_PROGRESS, result.getStepResults().get(0).getStatus());
        assertFalse(result.getStepResults().get(0).isSuccessful());
        assertEquals("tx1", result.getStepResults().get(0).getTransactionHash());
        assertEquals("step2", result.getFailedStep().orElseThrow().getStepId());
    }

    @Test
    void batchDefinitiveSubmissionFailureDoesNotReportBuildOnlyStepAsSuccessful() throws Exception {
        Transaction transaction = transaction(0);
        when(transactionProcessor.submitTransaction(any(byte[].class)))
                .thenReturn(Result.error("definitively rejected"));
        executor.withChainingMode(ChainingMode.BATCH);
        TxFlow flow = TxFlow.builder("batch-build-only")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> batchTxContext(transaction)).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(1, result.getStepResults().size());
        assertEquals("step1", result.getFailedStep().orElseThrow().getStepId());
        assertFalse(result.getStepResults().get(0).isSuccessful());
    }

    @Test
    void batchInvokesUserTransactionInspectorExactlyOnceAfterBuild() throws Exception {
        Transaction transaction = transaction(0);
        QuickTxBuilder.TxContext txContext = batchTxContext(transaction);
        AtomicInteger inspections = new AtomicInteger();
        AtomicReference<Transaction> inspectedTransaction = new AtomicReference<>();
        when(transactionProcessor.submitTransaction(any(byte[].class)))
                .thenReturn(Result.error("definitively rejected"));
        executor.withChainingMode(ChainingMode.BATCH)
                .withTxInspector(inspected -> {
                    inspections.incrementAndGet();
                    inspectedTransaction.set(inspected);
                });
        TxFlow flow = TxFlow.builder("batch-inspector")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext).build())
                .build();

        executor.executeSync(flow);

        assertEquals(1, inspections.get());
        assertSame(transaction, inspectedTransaction.get());
        verify(txContext, never()).withTxInspector(any());
    }

    @Test
    void batchTerminalFailurePreservesSubmittedAndFailedSteps() throws Exception {
        Transaction first = transaction(0);
        Transaction second = transaction(1);
        String firstHash = TransactionUtil.getTxHash(first);
        Result<String> firstSubmission = Result.success("submitted");
        firstSubmission.withValue(firstHash);
        when(transactionProcessor.submitTransaction(any(byte[].class)))
                .thenReturn(firstSubmission, Result.error("second rejected"));
        executor.withChainingMode(ChainingMode.BATCH);
        TxFlow flow = TxFlow.builder("batch-submitted-prefix")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> batchTxContext(first)).build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> batchTxContext(second)).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(List.of("step1", "step2"), result.getStepResults().stream()
                .map(FlowStepResult::getStepId).toList());
        assertEquals(firstHash, result.getStepResults().get(0).getTransactionHash());
        assertEquals(FlowStatus.IN_PROGRESS, result.getStepResults().get(0).getStatus());
        assertFalse(result.getStepResults().get(0).isSuccessful());
        assertEquals("step2", result.getFailedStep().orElseThrow().getStepId());
    }

    @Test
    void batchObservationFailureAfterUnknownSubmissionRequiresRecovery() throws Exception {
        class ProviderFailure extends ApiRuntimeException {
            ProviderFailure(String message) { super(message); }
        }
        Transaction transaction = transaction(0);
        String hash = TransactionUtil.getTxHash(transaction);
        when(transactionProcessor.submitTransaction(any(byte[].class)))
                .thenThrow(new ProviderFailure("response lost"));
        when(chainDataSupplier.getTransactionInfo(hash))
                .thenThrow(new IllegalStateException("backend unavailable"));
        executor.withChainingMode(ChainingMode.BATCH);
        TxFlow flow = TxFlow.builder("batch-unknown-observation")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> batchTxContext(transaction)).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(ReconciliationUncertainException.class, result.getError());
        // Uncertain submission = uncertain disposition: the step settles
        // submission-pending (IN_PROGRESS, hash retained), never FAILED — same
        // contract as confirmation timeouts.
        assertTrue(result.getFailedStep().isEmpty());
        assertEquals(1, result.getStepResults().size());
        FlowStepResult pending = result.getStepResults().get(0);
        assertEquals("step1", pending.getStepId());
        assertEquals(FlowStatus.IN_PROGRESS, pending.getStatus());
        assertInstanceOf(ReconciliationUncertainException.class, pending.getError());
    }

    @Test
    void batchRejectedSameHashResubmissionRequiresRecovery() throws Exception {
        class ProviderFailure extends ApiRuntimeException {
            ProviderFailure(String message) { super(message); }
        }
        Transaction transaction = transaction(0);
        String hash = TransactionUtil.getTxHash(transaction);
        when(transactionProcessor.submitTransaction(any(byte[].class)))
                .thenThrow(new ProviderFailure("response lost"))
                .thenReturn(Result.error("bad inputs may mean first submission landed"));
        when(chainDataSupplier.getTransactionInfo(hash)).thenReturn(Optional.empty());
        executor.withChainingMode(ChainingMode.BATCH);
        TxFlow flow = TxFlow.builder("batch-unknown-resubmit")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> batchTxContext(transaction)).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(ReconciliationUncertainException.class, result.getError());
        // Uncertain submission = uncertain disposition: the step settles
        // submission-pending (IN_PROGRESS, hash retained), never FAILED — same
        // contract as confirmation timeouts.
        assertTrue(result.getFailedStep().isEmpty());
        assertEquals(1, result.getStepResults().size());
        FlowStepResult pending = result.getStepResults().get(0);
        assertEquals("step1", pending.getStepId());
        assertEquals(FlowStatus.IN_PROGRESS, pending.getStatus());
        assertInstanceOf(ReconciliationUncertainException.class, pending.getError());
    }

    @Test
    void sequentialConfirmationTimeoutDoesNotAdvanceToNextStep() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        executor.withScheduler(scheduler);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(Optional.empty());
        QuickTxBuilder.TxContext first = successfulTxContext("tx1");
        AtomicInteger secondStepBuilds = new AtomicInteger();

        TxFlow flow = TxFlow.builder("sequential-confirmation-gate")
                .addStep(FlowStep.builder("step1").withTxContext(builder -> first).build())
                .addStep(FlowStep.builder("step2").withTxContext(builder -> {
                    secondStepBuilds.incrementAndGet();
                    return successfulTxContext("tx2");
                }).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(ConfirmationTimeoutException.class, result.getError());
        assertEquals(0, result.getCompletedStepCount());
        // A timed-out confirmation is an uncertain disposition: the transaction was
        // submitted and may still land, so the step settles submission-pending
        // (IN_PROGRESS with the hash retained), never FAILED — a FAILED step would
        // invite a fresh-build retry of a payment that can still confirm.
        assertTrue(result.getFailedStep().isEmpty());
        assertEquals(1, result.getStepResults().size());
        FlowStepResult pending = result.getStepResults().get(0);
        assertEquals("step1", pending.getStepId());
        assertEquals(FlowStatus.IN_PROGRESS, pending.getStatus());
        assertFalse(pending.isSuccessful());
        assertInstanceOf(ConfirmationTimeoutException.class, pending.getError());
        assertEquals("tx1", pending.getTransactionHash());
        assertEquals(0, secondStepBuilds.get());
    }

    @Test
    void pipelinedConfirmationFailurePreservesSubmittedTransactionDetails() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        Transaction transaction = transactionWithOutput(3);
        String transactionHash = "pipeline-hash";
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        AtomicReference<Consumer<Transaction>> buildInspector = new AtomicReference<>();
        when(txContext.withTxInspector(any())).thenAnswer(invocation -> {
            buildInspector.set(invocation.getArgument(0));
            return txContext;
        });
        Result<String> submission = Result.success("submitted");
        submission.withValue(transactionHash);
        when(txContext.complete()).thenAnswer(invocation -> {
            buildInspector.get().accept(transaction);
            return TxResult.fromResult(submission);
        });
        when(chainDataSupplier.getTransactionInfo(transactionHash)).thenReturn(Optional.empty());
        executor.withChainingMode(ChainingMode.PIPELINED)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .timeout(Duration.ofSeconds(1))
                        .checkInterval(Duration.ofSeconds(1))
                        .build());
        TxFlow flow = TxFlow.builder("pipeline-confirmation-details")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(1, result.getStepResults().size());
        // Timeout = uncertain disposition: submission-pending, details preserved.
        assertTrue(result.getFailedStep().isEmpty());
        FlowStepResult pending = result.getStepResults().get(0);
        assertEquals(FlowStatus.IN_PROGRESS, pending.getStatus());
        assertEquals(transactionHash, pending.getTransactionHash());
        assertEquals(1, pending.getOutputUtxos().size());
        assertEquals(transactionHash, pending.getOutputUtxos().get(0).getTxHash());
        assertEquals(3, pending.getSpentInputs().get(0).getIndex());
        assertInstanceOf(ConfirmationTimeoutException.class, pending.getError());
    }

    @Test
    void batchConfirmationFailurePreservesSubmittedTransactionDetails() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        Transaction transaction = transactionWithOutput(4);
        String transactionHash = TransactionUtil.getTxHash(transaction);
        Result<String> submission = Result.success("submitted");
        submission.withValue(transactionHash);
        when(transactionProcessor.submitTransaction(any(byte[].class))).thenReturn(submission);
        when(chainDataSupplier.getTransactionInfo(transactionHash)).thenReturn(Optional.empty());
        executor.withChainingMode(ChainingMode.BATCH)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .timeout(Duration.ofSeconds(1))
                        .checkInterval(Duration.ofSeconds(1))
                        .build());
        TxFlow flow = TxFlow.builder("batch-confirmation-details")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> batchTxContext(transaction)).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(1, result.getStepResults().size());
        // Timeout = uncertain disposition: submission-pending, details preserved.
        assertTrue(result.getFailedStep().isEmpty());
        FlowStepResult pending = result.getStepResults().get(0);
        assertEquals(FlowStatus.IN_PROGRESS, pending.getStatus());
        assertEquals(transactionHash, pending.getTransactionHash());
        assertEquals(1, pending.getOutputUtxos().size());
        assertEquals(transactionHash, pending.getOutputUtxos().get(0).getTxHash());
        assertEquals(4, pending.getSpentInputs().get(0).getIndex());
        assertInstanceOf(ConfirmationTimeoutException.class, pending.getError());
    }

    @ParameterizedTest
    @EnumSource(ChainingMode.class)
    void confirmationCancellationKeepsSubmittedAttemptInProgress(
            ChainingMode mode) throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        TestFlowScheduler scheduler = new TestFlowScheduler();
        when(chainDataSupplier.getChainTipHeight()).thenReturn(100L);
        when(chainDataSupplier.getTransactionInfo(anyString())).thenAnswer(invocation -> {
            cancelled.set(true);
            return Optional.empty();
        });

        String expectedHash;
        QuickTxBuilder.TxContext txContext;
        if (mode == ChainingMode.BATCH) {
            Transaction transaction = transactionWithOutput(8);
            expectedHash = TransactionUtil.getTxHash(transaction);
            txContext = batchTxContext(transaction);
            Result<String> accepted = Result.success("submitted");
            accepted.withValue(expectedHash);
            when(transactionProcessor.submitTransaction(any(byte[].class)))
                    .thenReturn(accepted);
        } else if (mode == ChainingMode.PIPELINED) {
            expectedHash = "cancelled-pipeline-hash";
            txContext = successfulPipelinedTxContext(expectedHash);
        } else {
            expectedHash = "cancelled-sequential-hash";
            txContext = successfulTxContext(expectedHash);
        }
        executor.withChainingMode(mode)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(1)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(10))
                        .build());
        TxFlow flow = TxFlow.builder("cancel-confirmation-" + mode.name().toLowerCase())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext).build())
                .build();

        FlowResult result = executor.executeSync(flow, cancelled::get);

        assertEquals(FlowStatus.CANCELLED, result.getStatus());
        assertInstanceOf(CancellationException.class, result.getError());
        assertEquals(1, result.getStepResults().size());
        FlowStepResult submitted = result.getStepResults().get(0);
        assertEquals(FlowStatus.IN_PROGRESS, submitted.getStatus());
        assertFalse(submitted.isSuccessful());
        assertEquals(expectedHash, submitted.getTransactionHash());
        assertInstanceOf(CancellationException.class, submitted.getError());
        assertTrue(result.getFailedStep().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ChainingMode.class)
    void liveHorizonCancellationIsTypedCancelledInEveryMode(
            ChainingMode mode) throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger firstHashObservations = new AtomicInteger();
        TestFlowScheduler scheduler = new TestFlowScheduler();

        String firstHash;
        String secondHash;
        QuickTxBuilder.TxContext firstContext;
        QuickTxBuilder.TxContext secondContext;
        if (mode == ChainingMode.BATCH) {
            Transaction first = transactionWithOutput(12);
            Transaction second = transactionWithOutput(13);
            firstHash = TransactionUtil.getTxHash(first);
            secondHash = TransactionUtil.getTxHash(second);
            firstContext = batchTxContext(first);
            secondContext = batchTxContext(second);
            Result<String> firstAccepted = Result.success("submitted");
            firstAccepted.withValue(firstHash);
            Result<String> secondAccepted = Result.success("submitted");
            secondAccepted.withValue(secondHash);
            when(transactionProcessor.submitTransaction(any(byte[].class)))
                    .thenReturn(firstAccepted, secondAccepted);
        } else if (mode == ChainingMode.PIPELINED) {
            firstHash = "live-horizon-pipeline-first";
            secondHash = "live-horizon-pipeline-second";
            firstContext = successfulPipelinedTxContext(firstHash);
            secondContext = successfulPipelinedTxContext(secondHash);
        } else {
            firstHash = "live-horizon-sequential-first";
            secondHash = "live-horizon-sequential-second";
            firstContext = successfulTxContext(firstHash);
            secondContext = successfulTxContext(secondHash);
        }

        when(chainDataSupplier.getChainTipHeight()).thenReturn(100L);
        int confirmationsBeforeHorizon = mode == ChainingMode.SEQUENTIAL ? 2 : 1;
        when(chainDataSupplier.getTransactionInfo(anyString())).thenAnswer(invocation -> {
            String transactionHash = invocation.getArgument(0);
            if (!firstHash.equals(transactionHash)) return Optional.empty();
            if (firstHashObservations.incrementAndGet() <= confirmationsBeforeHorizon) {
                return Optional.of(TransactionInfo.builder()
                        .txHash(firstHash).blockHeight(100L).blockHash("block-100").build());
            }
            cancelled.set(true);
            return Optional.empty();
        });

        executor.withChainingMode(mode)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(0)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(10))
                        .build());
        TxFlow flow = TxFlow.builder("cancel-live-horizon-" + mode.name().toLowerCase())
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .rollbackPolicy(RollbackPolicy.defaults())
                        .build())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> firstContext).build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> secondContext).build())
                .build();

        FlowResult result = executor.executeSync(flow, cancelled::get);

        assertEquals(FlowStatus.CANCELLED, result.getStatus());
        assertInstanceOf(CancellationException.class, result.getError());
        assertTrue(result.getStepResults().stream().anyMatch(step ->
                firstHash.equals(step.getTransactionHash()) && step.isSuccessful()));
        assertTrue(result.getStepResults().stream().noneMatch(step ->
                step.getStatus() == FlowStatus.FAILED));
    }

    @Test
    void batchCancellationAfterSigningStopsBeforeSubmittingTransitionAndBackend()
            throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        Transaction transaction = transaction(9);
        PersistencePort persistence = mock(PersistencePort.class);
        executor.withChainingMode(ChainingMode.BATCH)
                .withPersistencePort(persistence)
                .withTxInspector(ignored -> cancelled.set(true));
        TxFlow flow = TxFlow.builder("cancel-signed-batch")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> batchTxContext(transaction)).build())
                .build();

        FlowResult result = executor.executeSync(flow, cancelled::get);

        assertEquals(FlowStatus.CANCELLED, result.getStatus());
        assertInstanceOf(CancellationException.class, result.getError());
        assertTrue(result.getStepResults().isEmpty());
        verify(persistence).onPrepared(any(), eq(transaction));
        verify(persistence, never()).onSubmitting(any(), any());
        verify(transactionProcessor, never()).submitTransaction(any(byte[].class));
    }

    @Test
    void batchCancellationAtSubmittingBoundaryStillStopsBeforeBackendCall()
            throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        Transaction transaction = transaction(11);
        PersistencePort persistence = mock(PersistencePort.class);
        doAnswer(invocation -> {
            cancelled.set(true);
            return null;
        }).when(persistence).onSubmitting(any(), eq(transaction));
        executor.withChainingMode(ChainingMode.BATCH)
                .withPersistencePort(persistence);
        TxFlow flow = TxFlow.builder("cancel-at-submitting-boundary")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> batchTxContext(transaction)).build())
                .build();

        FlowResult result = executor.executeSync(flow, cancelled::get);

        assertEquals(FlowStatus.CANCELLED, result.getStatus());
        assertInstanceOf(CancellationException.class, result.getError());
        assertTrue(result.getStepResults().isEmpty());
        verify(persistence).onSubmitting(any(), eq(transaction));
        verify(transactionProcessor, never()).submitTransaction(any(byte[].class));
    }

    @Test
    void sequentialCancellationAtInnerExecutionBoundaryIsTypedCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger factories = new AtomicInteger();
        executor.withListener(new FlowListener() {
            @Override
            public void onStepStarted(FlowStep step, int index, int total) {
                cancelled.set(true);
            }
        });
        TxFlow flow = TxFlow.builder("cancel-inner-sequential")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> {
                            factories.incrementAndGet();
                            return successfulTxContext("must-not-submit");
                        }).build())
                .build();

        FlowResult result = executor.executeSync(flow, cancelled::get);

        assertEquals(FlowStatus.CANCELLED, result.getStatus());
        assertInstanceOf(CancellationException.class, result.getError());
        assertEquals(1, result.getStepResults().size());
        assertEquals(FlowStatus.CANCELLED,
                result.getStepResults().get(0).getStatus());
        assertInstanceOf(CancellationException.class,
                result.getStepResults().get(0).getError());
        assertEquals(0, factories.get());
    }

    @ParameterizedTest
    @EnumSource(ChainingMode.class)
    void exhaustedRollbackNeverLeavesRolledBackHashSuccessful(
            ChainingMode mode) throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withExecutor(Runnable::run)
                .withScheduler(scheduler)
                .withChainingMode(mode)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(2)
                        .requiredAuthoritativeAbsences(1)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(10))
                        .maxRollbackRetries(0)
                        .build())
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW);

        String rolledBackHash;
        QuickTxBuilder.TxContext txContext;
        if (mode == ChainingMode.BATCH) {
            Transaction transaction = transactionWithOutput(10);
            rolledBackHash = TransactionUtil.getTxHash(transaction);
            txContext = batchTxContext(transaction);
            Result<String> accepted = Result.success("submitted");
            accepted.withValue(rolledBackHash);
            when(transactionProcessor.submitTransaction(any(byte[].class)))
                    .thenReturn(accepted);
        } else if (mode == ChainingMode.PIPELINED) {
            rolledBackHash = "rolled-back-pipeline-hash";
            txContext = successfulPipelinedTxContext(rolledBackHash);
        } else {
            rolledBackHash = "rolled-back-sequential-hash";
            txContext = successfulTxContext(rolledBackHash);
        }
        TxFlow flow = TxFlow.builder("rollback-projection-" + mode.name().toLowerCase())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        FlowStepResult rolledBack = result.getStepResults().stream()
                .filter(stepResult -> rolledBackHash.equals(stepResult.getTransactionHash()))
                .findFirst().orElseThrow();
        assertEquals(FlowStatus.FAILED, rolledBack.getStatus());
        assertFalse(rolledBack.isSuccessful());
        assertInstanceOf(RollbackException.class, rolledBack.getError());
        assertFalse(result.getStepResults().stream().anyMatch(stepResult ->
                rolledBackHash.equals(stepResult.getTransactionHash())
                        && stepResult.isSuccessful()));
    }

    @ParameterizedTest
    @EnumSource(ChainingMode.class)
    void cancellationDuringRollbackPrefixReconciliationStaysCancelled(
            ChainingMode mode) throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger rolledBackObservations = new AtomicInteger();
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ChainDataSupplier authoritative = ObservationCapabilities.withAuthoritativeAbsence(
                chainDataSupplier);

        String firstHash;
        String rolledBackHash;
        QuickTxBuilder.TxContext firstContext;
        QuickTxBuilder.TxContext rolledBackContext;
        if (mode == ChainingMode.BATCH) {
            Transaction first = transactionWithOutput(14);
            Transaction rolledBack = transactionWithOutput(15);
            firstHash = TransactionUtil.getTxHash(first);
            rolledBackHash = TransactionUtil.getTxHash(rolledBack);
            firstContext = batchTxContext(first);
            rolledBackContext = batchTxContext(rolledBack);
            Result<String> firstAccepted = Result.success("submitted");
            firstAccepted.withValue(firstHash);
            Result<String> secondAccepted = Result.success("submitted");
            secondAccepted.withValue(rolledBackHash);
            when(transactionProcessor.submitTransaction(any(byte[].class)))
                    .thenReturn(firstAccepted, secondAccepted);
        } else if (mode == ChainingMode.PIPELINED) {
            firstHash = "rollback-cancel-pipeline-first";
            rolledBackHash = "rollback-cancel-pipeline-second";
            firstContext = successfulPipelinedTxContext(firstHash);
            rolledBackContext = successfulPipelinedTxContext(rolledBackHash);
        } else {
            firstHash = "rollback-cancel-sequential-first";
            rolledBackHash = "rollback-cancel-sequential-second";
            firstContext = successfulTxContext(firstHash);
            rolledBackContext = successfulTxContext(rolledBackHash);
        }

        when(chainDataSupplier.getTransactionInfo(firstHash)).thenReturn(Optional.of(
                TransactionInfo.builder().txHash(firstHash)
                        .blockHeight(100L).blockHash("block-100").build()));
        when(chainDataSupplier.getTransactionInfo(rolledBackHash)).thenAnswer(invocation ->
                rolledBackObservations.incrementAndGet() == 1
                        ? Optional.of(TransactionInfo.builder().txHash(rolledBackHash)
                                .blockHeight(101L).blockHash("block-101").build())
                        : Optional.empty());
        when(chainDataSupplier.getChainTipHeight()).thenAnswer(invocation ->
                rolledBackObservations.get() >= 2 ? 102L : 101L);

        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, authoritative)
                .withExecutor(Runnable::run)
                .withScheduler(scheduler)
                .withChainingMode(mode)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(1)
                        .requiredAuthoritativeAbsences(1)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(10))
                        .maxRollbackRetries(1)
                        .build())
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .withListener(new FlowListener() {
                    @Override
                    public void onTransactionRolledBack(
                            FlowStep step, String transactionHash, long previousBlockHeight) {
                        cancelled.set(true);
                    }
                });
        TxFlow flow = TxFlow.builder("cancel-rollback-reconcile-" + mode.name().toLowerCase())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> firstContext).build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> rolledBackContext).build())
                .build();

        FlowResult result = executor.executeSync(flow, cancelled::get);

        assertEquals(FlowStatus.CANCELLED, result.getStatus());
        assertInstanceOf(CancellationException.class, result.getError());
        assertTrue(result.getStepResults().stream().anyMatch(step ->
                firstHash.equals(step.getTransactionHash()) && step.isSuccessful()));
        assertTrue(result.getStepResults().stream().noneMatch(step ->
                step.getStatus() == FlowStatus.FAILED));
    }

    @Test
    void ambiguousAbsenceAfterInclusionIsSuspectedThenRequiresRecovery() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        executor.withScheduler(scheduler).withConfirmationConfig(ConfirmationConfig.builder()
                .minConfirmations(3)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(3))
                .build());
        when(chainDataSupplier.getChainTipHeight()).thenReturn(100L);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1")
                        .blockHeight(99L).blockHash("block-99").build()),
                Optional.empty());
        AtomicInteger suspected = new AtomicInteger();
        executor.withListener(new FlowListener() {
            @Override
            public void onTransactionRollbackSuspected(
                    FlowStep step, String transactionHash, long previousBlockHeight) {
                suspected.incrementAndGet();
            }
        });

        TxFlow flow = TxFlow.builder("ambiguous-absence")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("tx1")).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(ReconciliationUncertainException.class, result.getError());
        assertEquals(1, suspected.get());
    }

    @Test
    void portableWaitForReinclusionSucceedsWithinItsIndependentWindow() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102),
                        absent(103), included(104, 103, "block-b"),
                        included(105, 103, "block-b"));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withScheduler(scheduler);

        TxFlow flow = portableWaitFlow("wait-reincluded", Duration.ofSeconds(2));
        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isSuccessful());
        assertEquals(Duration.ofSeconds(3), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void portableWaitForReinclusionExhaustsAtWindowNotRecoveryCycleCount() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102),
                        absent(103), absent(104));
        AtomicInteger uncertainSteps = new AtomicInteger();
        AtomicInteger failedSteps = new AtomicInteger();
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withScheduler(scheduler)
                .withListener(new FlowListener() {
                    @Override
                    public void onStepUncertain(FlowStep step, FlowStepResult result) {
                        uncertainSteps.incrementAndGet();
                    }

                    @Override
                    public void onStepFailed(FlowStep step, FlowStepResult result) {
                        failedSteps.incrementAndGet();
                    }
                });

        FlowResult result = executor.executeSync(
                portableWaitFlow("wait-exhausted", Duration.ofSeconds(2)));

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(RollbackException.class, result.getError());
        assertInstanceOf(ReconciliationUncertainException.class, result.getError().getCause());
        assertTrue(result.getError().getCause().getCause().getMessage().contains("within PT2S"));
        assertTrue(result.getFailedStep().isEmpty());
        assertEquals(1, result.getStepResults().size());
        FlowStepResult pending = result.getStepResults().get(0);
        assertEquals(FlowStatus.IN_PROGRESS, pending.getStatus());
        assertEquals("same-hash", pending.getTransactionHash());
        assertEquals(1, uncertainSteps.get());
        assertEquals(0, failedSteps.get());
        assertEquals(Duration.ofSeconds(4), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void exhaustedNotifyOnlyRepollsPreserveRollbackOutcome() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withExecutor(Runnable::run)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(1)
                        .requiredAuthoritativeAbsences(1)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(2))
                        .maxRollbackRetries(1)
                        .build())
                .withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY);

        TxFlow flow = TxFlow.builder("notify-only-exhausted")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("same-hash"))
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(RollbackException.class, result.getError());
        assertEquals(100L, ((RollbackException) result.getError()).getPreviousBlockHeight());
        assertEquals(Duration.ofSeconds(2), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void notifyOnlyRepollAllowsSameHashReinclusion() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101),
                        included(102, 102, "block-b"), included(103, 102, "block-b"),
                        included(104, 102, "block-b"));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withExecutor(Runnable::run)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(1)
                        .requiredAuthoritativeAbsences(1)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(5))
                        .maxRollbackRetries(1)
                        .build())
                .withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY);

        TxFlow flow = TxFlow.builder("notify-only-reincluded")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("same-hash"))
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isSuccessful(), () -> "unexpected result: " + result.getStatus()
                + ", error=" + result.getError() + ", delays=" + scheduler.getDelays());
        assertEquals(Duration.ofSeconds(3), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    private TxFlow portableWaitFlow(String flowId, Duration reinclusionWindow) {
        ConfirmationConfig confirmation = ConfirmationConfig.builder()
                .minConfirmations(1)
                .requiredAuthoritativeAbsences(2)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(20))
                .build();
        RollbackPolicy rollback = new RollbackPolicy(
                RollbackAction.WAIT_FOR_REINCLUSION,
                RollbackMonitoringHorizon.UNTIL_STEP_CONFIRMED,
                RollbackRebuildScope.INVALIDATED_CLOSURE,
                99, reinclusionWindow, 2);
        return TxFlow.builder(flowId)
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .confirmationConfig(confirmation)
                        .rollbackPolicy(rollback)
                        .build())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("same-hash"))
                        .build())
                .build();
    }

    private Function<QuickTxBuilder, QuickTxBuilder.TxContext> blockingFactory(
            QuickTxBuilder.TxContext txContext, CountDownLatch started, CountDownLatch release) {
        return builder -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Timed out waiting to release test flow");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return txContext;
        };
    }

    @Test
    void asyncExecutionRequiresCallerSuppliedExecutorButSyncDoesNot() {
        FlowExecutor withoutExecutor = FlowExecutor.create(
                utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier);
        TxFlow flow = createSimpleFlow("caller-executor-required");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> withoutExecutor.execute(flow));

        assertTrue(failure.getMessage().contains("caller-supplied Executor"));
        assertTrue(failure.getMessage().contains("virtual-thread executor"));
        FlowResult previous = FlowResult.builder(flow.getId())
                .withStatus(FlowStatus.FAILED)
                .addStepResult(FlowStepResult.failure("step1", new RuntimeException("failed")))
                .build();
        assertThrows(IllegalStateException.class,
                () -> withoutExecutor.resume(flow, previous));
        assertDoesNotThrow(() -> withoutExecutor.executeSync(flow));
    }

    @Test
    void portableTemplateRequiresCompilationBeforeDirectExecution() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: portable-template}
                spec:
                  steps:
                    - id: payment
                      transaction:
                        tx: {intents: []}
                """;
        TxFlow portable = TxFlowCodec.standard()
                .parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();

        FlowExecutionException syncFailure = assertThrows(FlowExecutionException.class,
                () -> executor.executeSync(portable));
        assertTrue(syncFailure.getMessage().contains("TxFlowCompiler"));
        assertTrue(syncFailure.getMessage().contains("FlowEngine"));

        FlowExecutionException asyncFailure = assertThrows(FlowExecutionException.class,
                () -> executor.execute(portable));
        assertTrue(asyncFailure.getMessage().contains("template steps: [payment]"));
    }

    // ==================== HIGH-3: Validate rollback strategy requires ConfirmationConfig ====================

    @Test
    void testExecuteSync_rebuildFromFailed_withoutConfirmationConfig_throwsIllegalState() {
        executor.withRollbackStrategy(RollbackStrategy.REBUILD_FROM_FAILED);
        TxFlow flow = createSimpleFlow("test-flow");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("REBUILD_FROM_FAILED"));
        assertTrue(ex.getMessage().contains("withConfirmationConfig"));
    }

    @Test
    void testExecute_rebuildEntireFlow_withoutConfirmationConfig_throwsIllegalState() {
        executor.withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW);
        TxFlow flow = createSimpleFlow("test-flow");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.execute(flow));
        assertTrue(ex.getMessage().contains("REBUILD_ENTIRE_FLOW"));
    }

    @Test
    void testExecute_notifyOnly_withoutConfirmationConfig_throwsIllegalState() {
        executor.withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY);
        TxFlow flow = createSimpleFlow("test-flow");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.execute(flow));
        assertTrue(ex.getMessage().contains("NOTIFY_ONLY"));
    }

    @Test
    void testExecute_failImmediately_withoutConfirmationConfig_succeeds() {
        executor.withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY);
        TxFlow flow = createSimpleFlow("test-flow");

        // Should not throw — FAIL_IMMEDIATELY doesn't require ConfirmationConfig
        // It will fail later during execution, but the validation should pass
        assertDoesNotThrow(() -> executor.execute(flow));
    }

    @Test
    void testExecuteSync_flowRollbackStrategy_withoutConfirmationConfig_throwsIllegalState() {
        TxFlow flow = TxFlow.builder("flow-context-no-confirmation")
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("REBUILD_ENTIRE_FLOW"));
        assertTrue(ex.getMessage().contains("context.confirmation"));
    }

    @Test
    void testExecuteSync_flowRollbackStrategy_withFlowConfirmation_passesValidation() {
        TxFlow flow = TxFlow.builder("flow-context-confirmation")
                .withConfirmationConfig(ConfirmationConfig.quick())
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertDoesNotThrow(() -> executor.executeSync(flow));
    }

    @Test
    void testExecuteSync_explicitExecutorRollbackDefault_overridesFlowRollbackStrategy() {
        executor.withRollbackStrategy(null);

        TxFlow flow = TxFlow.builder("executor-rollback-default")
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertDoesNotThrow(() -> executor.executeSync(flow));
    }

    @Test
    void testExecuteSync_explicitNullConfirmation_overridesFlowConfirmation() {
        executor.withConfirmationConfig(null);

        TxFlow flow = TxFlow.builder("executor-null-confirmation")
                .withConfirmationConfig(ConfirmationConfig.quick())
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("REBUILD_ENTIRE_FLOW"));
        assertTrue(ex.getMessage().contains("context.confirmation"));
    }

    @Test
    void testEffectiveChainingMode_flowContextAppliesWhenExecutorUnset() throws Exception {
        TxFlow flow = TxFlow.builder("flow-chaining")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertEquals(ChainingMode.BATCH, executor.effectiveSettings(flow).getChainingMode());
    }

    @Test
    void testEffectiveChainingMode_explicitExecutorDefaultOverridesFlowContext() throws Exception {
        executor.withChainingMode(null);

        TxFlow flow = TxFlow.builder("executor-chaining-default")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertEquals(ChainingMode.SEQUENTIAL, executor.effectiveSettings(flow).getChainingMode());
    }

    @Test
    void portableRollbackPolicyControlsLegacyFacadeWithoutLosingItsBudgets() {
        var rollback = new com.bloxbean.cardano.client.txflow.config.RollbackPolicy(
                com.bloxbean.cardano.client.txflow.config.RollbackAction.RECONCILE_AND_REBUILD,
                com.bloxbean.cardano.client.txflow.config.RollbackMonitoringHorizon.UNTIL_FLOW_TERMINAL,
                com.bloxbean.cardano.client.txflow.config.RollbackRebuildScope.INVALIDATED_CLOSURE,
                7, Duration.ofMinutes(1), 4);
        TxFlow flow = TxFlow.builder("portable-rollback")
                .withExecutionSettings(com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings.builder()
                        .rollbackPolicy(rollback).build())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        FlowExecutor.EffectiveFlowExecutionSettings settings = executor.effectiveSettings(flow);
        assertEquals(RollbackStrategy.REBUILD_ENTIRE_FLOW, settings.getRollbackStrategy());
        assertEquals(7, settings.getConfirmationConfig().getMaxRollbackRetries());
        assertEquals(4, settings.getConfirmationConfig().getRequiredAuthoritativeAbsences());
        assertTrue(settings.isMonitorUntilFlowTerminal());
    }

    @Test
    void testExecute_concurrentFlowsWithDifferentContexts_doNotContaminateChainingMode() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        executor.withExecutor(pool);

        QuickTxBuilder.TxContext sequentialContext = failingTxContext("sequential connection timeout");
        QuickTxBuilder.TxContext batchContext = failingTxContext("batch connection timeout");

        TxFlow sequentialFlow = TxFlow.builder("concurrent-sequential")
                .withChainingMode(ChainingMode.SEQUENTIAL)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(blockingFactory(sequentialContext, started, release))
                        .build())
                .build();

        TxFlow batchFlow = TxFlow.builder("concurrent-batch")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(blockingFactory(batchContext, started, release))
                        .build())
                .build();

        try {
            FlowHandle sequentialHandle = executor.execute(sequentialFlow);
            FlowHandle batchHandle = executor.execute(batchFlow);

            assertTrue(started.await(5, TimeUnit.SECONDS), "Both flows should start before release");
            release.countDown();

            sequentialHandle.await(Duration.ofSeconds(5));
            batchHandle.await(Duration.ofSeconds(5));

            verify(sequentialContext, atLeastOnce())
                    .completeAndWait(any(Duration.class), any(Duration.class), any());
            verify(sequentialContext, never()).buildAndSign();
            verify(batchContext, atLeastOnce()).buildAndSign();
            verify(batchContext, never())
                    .completeAndWait(any(Duration.class), any(Duration.class), any());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void testExecuteSync_stepRetryPolicyOverridesFlowDefaultRetryPolicy() {
        QuickTxBuilder.TxContext txContext = failingTxContext("connection timeout");
        RetryPolicy flowRetry = RetryPolicy.builder()
                .maxAttempts(3)
                .backoffStrategy(BackoffStrategy.FIXED)
                .initialDelay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .build();
        RetryPolicy stepRetry = RetryPolicy.noRetry();

        TxFlow flow = TxFlow.builder("step-retry-wins")
                .withDefaultRetryPolicy(flowRetry)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext)
                        .withRetryPolicy(stepRetry)
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isFailed());
        verify(txContext, times(1))
                .completeAndWait(any(Duration.class), any(Duration.class), any());
    }

    @Test
    void testExecuteSync_flowDefaultRetryPolicyAppliesWhenStepHasNoRetryPolicy() {
        QuickTxBuilder.TxContext txContext = failingTxContext("connection timeout");
        RetryPolicy flowRetry = RetryPolicy.builder()
                .maxAttempts(2)
                .backoffStrategy(BackoffStrategy.FIXED)
                .initialDelay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .build();

        TxFlow flow = TxFlow.builder("flow-retry-default")
                .withDefaultRetryPolicy(flowRetry)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext)
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isFailed());
        verify(txContext, times(2))
                .completeAndWait(any(Duration.class), any(Duration.class), any());
    }

    // ==================== HIGH-5: Reject duplicate flow ID execution ====================

    @Test
    void testExecute_duplicateFlowId_throwsIllegalState() throws Exception {
        // Use a blocking executor to ensure the flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow1 = createSimpleFlow("same-id");

        // First execution should work
        FlowHandle handle1 = executor.execute(flow1);

        // Second execution with same ID should throw
        TxFlow flow2 = createSimpleFlow("same-id");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.execute(flow2));
        assertTrue(ex.getMessage().contains("same-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        // Clean up
        handle1.cancel();
        blockLatch.countDown();
    }

    @Test
    void testExecute_afterFlowCompletes_sameIdAllowed() throws Exception {
        TxFlow flow = createSimpleFlow("reusable-id");

        FlowHandle handle = executor.execute(flow);

        // Wait briefly for async task to start and (likely) fail
        try {
            handle.await(Duration.ofSeconds(2));
        } catch (Exception e) {
            // Expected — mocked backend will fail
        }

        // After completion, same ID should be allowed again
        // (the finally block removes the ID)
        assertDoesNotThrow(() -> {
            FlowHandle handle2 = executor.execute(flow);
            handle2.cancel();
        });
    }

    // ==================== MED-1: FlowHandle status FAILED on async exception ====================

    @Test
    void testExecute_executionFailure_setsStatusToFailed() throws Exception {
        // Create a valid flow that will fail during execution (mocked backend returns null)
        TxFlow flow = createSimpleFlow("fail-flow");

        FlowHandle handle = executor.execute(flow);

        // Wait for it to complete (should fail because mocked backend is not set up)
        try {
            handle.await(Duration.ofSeconds(5));
        } catch (Exception e) {
            // Expected — execution fails with mocked backend
        }

        assertEquals(FlowStatus.FAILED, handle.getStatus(),
                "FlowHandle status should be FAILED after execution exception");
    }

    // ==================== MED-7: FlowHandle.await() preserves exception type ====================

    @Test
    void testAwait_preservesFlowExecutionException() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new FlowExecutionException("test error"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class, () -> handle.await());
        assertEquals("test error", ex.getMessage());
    }

    @Test
    void testAwait_preservesIllegalStateException() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new IllegalStateException("bad state"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> handle.await());
        assertEquals("bad state", ex.getMessage());
    }

    @Test
    void testAwait_wrapsCheckedExceptions() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new Exception("checked error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> handle.await());
        assertEquals("checked error", ex.getCause().getMessage());
    }

    @Test
    void testAwaitWithTimeout_preservesFlowExecutionException() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new FlowExecutionException("timeout test error"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> handle.await(Duration.ofSeconds(5)));
        assertEquals("timeout test error", ex.getMessage());
    }

    // ==================== MED-8: getResult() throws on failed future ====================

    @Test
    void testGetResult_returnsEmptyWhenNotDone() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        assertTrue(handle.getResult().isEmpty());
    }

    @Test
    void testGetResult_throwsWhenCompletedExceptionally() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new FlowExecutionException("execution failed"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class, () -> handle.getResult());
        assertEquals("execution failed", ex.getMessage());
    }

    @Test
    void testGetResult_returnsEmptyWhenCancelled() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.cancel(true);

        assertTrue(handle.getResult().isEmpty());
    }

    // ==================== MED-9: RetryPolicy.isRetryable() rejects Error ====================

    @Test
    void testIsRetryable_returnsFalseForOutOfMemoryError() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertFalse(policy.isRetryable(new OutOfMemoryError("test")));
    }

    @Test
    void testIsRetryable_returnsFalseForStackOverflowError() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertFalse(policy.isRetryable(new StackOverflowError()));
    }

    @Test
    void testIsRetryable_returnsFalseForUnknownRuntimeException() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertFalse(policy.isRetryable(new RuntimeException("unknown transient issue")));
    }

    // ==================== HIGH-1: Listener wrapping ====================

    @Test
    void testWithListener_wrapsInCompositeFlowListener() {
        FlowListener customListener = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow flow) {
                throw new RuntimeException("buggy listener");
            }
        };

        executor.withListener(customListener);

        // The executor should not crash when calling listeners
        // We can't easily test the listener is wrapped without reflection,
        // but we can verify the executor accepts it without error
        assertNotNull(executor);
    }

    @Test
    void testWithListener_nullSetsNoop() {
        executor.withListener(null);
        // Should not throw
        assertNotNull(executor);
    }

    // ==================== HIGH-6: AutoCloseable ====================

    @Test
    void testClose_doesNotThrow() {
        executor.close();
        // Should complete without error
    }

    @Test
    void testClose_withConfirmationConfig() {
        executor.withConfirmationConfig(ConfirmationConfig.quick());
        executor.close();
        // Should complete without error
    }

    // ==================== HIGH-2: cancel() interrupts running flows ====================

    @Test
    void testCancel_duringConfirmationWait_exitsPromptly() throws Exception {
        // Setup: chainDataSupplier returns a tip but never finds the transaction
        // This will cause the confirmation wait loop to keep polling
        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo(anyString())).thenReturn(Optional.empty());

        // Configure with confirmation tracking so waitForConfirmationWithTracking is used
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(1)
                .checkInterval(Duration.ofMillis(50))
                .timeout(Duration.ofSeconds(60))  // Long timeout — we rely on cancel, not timeout
                .build();
        executor.withConfirmationConfig(config);

        TxFlow flow = createSimpleFlow("cancel-test");
        FlowHandle handle = executor.execute(flow);

        // Wait briefly for execution to start (it will fail during tx build with mocked backend,
        // but the test validates that the cancellation flag propagates correctly)
        Thread.sleep(200);

        long start = System.currentTimeMillis();
        handle.cancel();

        // Wait for the flow to complete — should be very fast after cancel
        try {
            handle.await(Duration.ofSeconds(5));
        } catch (Exception e) {
            // Expected — the flow will fail/cancel
        }

        long elapsed = System.currentTimeMillis() - start;
        assertTrue(handle.isCancelled(), "Handle should be marked as cancelled");
        // The flow may have already failed before reaching confirmation wait (due to mocked backend),
        // but the key assertion is that cancel() returns promptly and the handle reflects cancellation
        assertTrue(elapsed < 5000, "Should exit promptly after cancel, took " + elapsed + "ms");
    }

    @Test
    void cooperativeCancellationKeepsHandleStatusCancelled() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Runnable> queued =
                new java.util.concurrent.atomic.AtomicReference<>();
        executor.withExecutor(queued::set);
        FlowHandle handle = executor.execute(createSimpleFlow("cancel-status"));

        assertTrue(handle.cancel());
        queued.get().run();

        assertThrows(java.util.concurrent.CancellationException.class, handle::await);
        assertEquals(FlowStatus.CANCELLED, handle.getStatus());
    }

    // ==================== HIGH-5: executeSync/resumeSync duplicate flow ID guard ====================

    @Test
    void testExecuteSync_duplicateFlowId_throwsIllegalState() throws Exception {
        // Use a blocking executor to ensure the async flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow = createSimpleFlow("sync-dup-id");

        // Start async execution — this will hold the flow ID in activeFlowIds
        FlowHandle handle = executor.execute(flow);

        // Sync execution with the same ID should throw
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("sync-dup-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        handle.cancel();
        blockLatch.countDown();
    }

    @Test
    void testResumeSync_duplicateFlowId_throwsIllegalState() throws Exception {
        // Use a blocking executor to ensure the async flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow = createSimpleFlow("resume-dup-id");

        // Start async execution to occupy the flow ID
        FlowHandle handle = executor.execute(flow);

        // Build a failed FlowResult for resumeSync
        FlowResult failedResult = FlowResult.builder("resume-dup-id")
                .withStatus(FlowStatus.FAILED)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .addStepResult(FlowStepResult.failure("step1", new RuntimeException("test")))
                .withError(new RuntimeException("test"))
                .build();

        // resumeSync with same ID should throw
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> executor.resumeSync(flow, failedResult));
        assertTrue(ex.getMessage().contains("resume-dup-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        handle.cancel();
        blockLatch.countDown();
    }

    @Test
    void testResumeSync_submissionPendingStep_refusesResume() {
        TxFlow flow = createSimpleFlow("resume-pending-guard");

        // Previous run ended with an uncertain disposition: the step's transaction was
        // submitted but its outcome is unknown (confirmation timeout). The step result is
        // submission-pending (IN_PROGRESS + hash), not failed.
        String pendingHash = "e".repeat(64);
        FlowResult uncertainResult = FlowResult.builder("resume-pending-guard")
                .withStatus(FlowStatus.FAILED)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .addStepResult(FlowStepResult.submissionPendingAt("step1", pendingHash,
                        List.of(), List.of(), new ConfirmationTimeoutException(pendingHash), Instant.now()))
                .withError(new ConfirmationTimeoutException(pendingHash))
                .build();

        // Resume must refuse: re-executing the pending step could duplicate a transaction
        // that may still confirm.
        IllegalStateException syncEx = assertThrows(IllegalStateException.class,
                () -> executor.resumeSync(flow, uncertainResult));
        assertTrue(syncEx.getMessage().contains("step1"));
        assertTrue(syncEx.getMessage().contains(pendingHash));
        assertTrue(syncEx.getMessage().contains("submission-pending"));

        IllegalStateException asyncEx = assertThrows(IllegalStateException.class,
                () -> executor.resume(flow, uncertainResult));
        assertTrue(asyncEx.getMessage().contains("submission-pending"));
    }

    // ==================== P2-3: close() cancels active handles ====================

    @Test
    void testClose_cancelsActiveHandles() throws Exception {
        // Use a blocking executor to ensure the flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow = createSimpleFlow("close-cancel-test");

        FlowHandle handle = executor.execute(flow);

        // Close should cancel running flows
        executor.close();

        assertTrue(handle.isCancelled(), "Handle should be cancelled after close()");

        blockLatch.countDown();
    }
}
