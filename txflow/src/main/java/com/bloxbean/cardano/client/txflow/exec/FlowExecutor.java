package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.exec.registry.FlowRegistry;
import com.bloxbean.cardano.client.txflow.exec.store.*;
import com.bloxbean.cardano.client.txflow.result.*;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Executor for transaction flows.
 * <p>
 * FlowExecutor orchestrates the execution of a {@link TxFlow} by:
 * <ul>
 *     <li>Resolving UTXO dependencies between steps</li>
 *     <li>Building and submitting transactions for each step</li>
 *     <li>Waiting for transaction confirmations</li>
 *     <li>Notifying listeners of progress</li>
 * </ul>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * FlowExecutor executor = FlowExecutor.create(backendService)
 *     .withSignerRegistry(signerRegistry)
 *     .withListener(new LoggingFlowListener());
 *
 * FlowHandle handle = executor.execute(flow);
 * FlowResult result = handle.await();
 * }</pre>
 */
@Slf4j
public class FlowExecutor implements AutoCloseable {
    private static final Executor DEFAULT_EXECUTOR = createDefaultExecutor();

    private static Executor createDefaultExecutor() {
        // Try to use virtual threads if available (Java 21+)
        try {
            java.lang.reflect.Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            Executor vtExecutor = (Executor) method.invoke(null);
            log.info("Using virtual thread executor (Java 21+)");
            return vtExecutor;
        } catch (Exception e) {
            // Fall back to cached thread pool (Java 11-20)
            log.debug("Virtual threads not available, using cached thread pool");
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "txflow-executor");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private final UtxoSupplier baseUtxoSupplier;
    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final TransactionProcessor transactionProcessor;
    private final ChainDataSupplier chainDataSupplier;
    private static final Duration DEFAULT_CONFIRMATION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(2);

    private volatile SignerRegistry signerRegistry;
    private volatile FlowListener listener = FlowListener.NOOP;
    private volatile Executor executor;
    private volatile Consumer<Transaction> txInspector;
    private volatile ChainingMode chainingMode = ChainingMode.SEQUENTIAL;
    private volatile RetryPolicy defaultRetryPolicy;
    private volatile ConfirmationConfig confirmationConfig;
    private volatile ConfirmationTracker confirmationTracker;
    private volatile RollbackStrategy rollbackStrategy = RollbackStrategy.FAIL_IMMEDIATELY;
    private volatile FlowRegistry flowRegistry;
    private volatile FlowStateStore flowStateStore;
    private final Set<String> activeFlowIds = ConcurrentHashMap.newKeySet();
    private final Set<FlowHandle> activeHandles = ConcurrentHashMap.newKeySet();

    /**
     * Create a FlowExecutor with the given supplier interfaces.
     * <p>
     * This is the primary constructor that accepts supplier interfaces for loose coupling.
     *
     * @param utxoSupplier the UTXO supplier for querying UTXOs
     * @param protocolParamsSupplier the protocol params supplier for protocol parameters
     * @param transactionProcessor the transaction processor for submitting transactions
     * @param chainDataSupplier the chain data supplier for chain queries
     */
    private FlowExecutor(UtxoSupplier utxoSupplier,
                         ProtocolParamsSupplier protocolParamsSupplier,
                         TransactionProcessor transactionProcessor,
                         ChainDataSupplier chainDataSupplier) {
        this.baseUtxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.transactionProcessor = transactionProcessor;
        this.chainDataSupplier = chainDataSupplier;
    }

    /**
     * Create a FlowExecutor from a BackendService.
     * <p>
     * This is a convenience constructor that wraps the BackendService with default supplier implementations.
     *
     * @param backendService the backend service
     */
    private FlowExecutor(BackendService backendService) {
        this(new DefaultUtxoSupplier(backendService.getUtxoService()),
             new DefaultProtocolParamsSupplier(backendService.getEpochService()),
             new DefaultTransactionProcessor(backendService.getTransactionService()),
             new DefaultChainDataSupplier(backendService));
    }

    /**
     * Create a new FlowExecutor for the given backend service.
     *
     * @param backendService the backend service
     * @return a new FlowExecutor
     */
    public static FlowExecutor create(BackendService backendService) {
        return new FlowExecutor(backendService);
    }

    /**
     * Create a new FlowExecutor with the given supplier interfaces.
     * <p>
     * This factory method enables loose coupling with custom implementations,
     * allowing integration with any data provider without implementing full BackendService.
     *
     * @param utxoSupplier the UTXO supplier for querying UTXOs
     * @param protocolParamsSupplier the protocol params supplier for protocol parameters
     * @param transactionProcessor the transaction processor for submitting transactions
     * @param chainDataSupplier the chain data supplier for chain queries
     * @return a new FlowExecutor
     */
    public static FlowExecutor create(UtxoSupplier utxoSupplier,
                                      ProtocolParamsSupplier protocolParamsSupplier,
                                      TransactionProcessor transactionProcessor,
                                      ChainDataSupplier chainDataSupplier) {
        return new FlowExecutor(utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier);
    }

    /**
     * Set the signer registry for resolving signer references (used with TxPlan/YAML workflows).
     *
     * @param registry the signer registry
     * @return this executor
     */
    public FlowExecutor withSignerRegistry(SignerRegistry registry) {
        this.signerRegistry = registry;
        return this;
    }

    /**
     * Set a listener for flow execution events.
     * <p>
     * The listener is automatically wrapped in a protective adapter that catches
     * and logs exceptions thrown by listener callbacks, preventing buggy listeners
     * from crashing the flow execution.
     *
     * @param listener the listener
     * @return this executor
     */
    public FlowExecutor withListener(FlowListener listener) {
        if (listener == null || listener == FlowListener.NOOP) {
            this.listener = FlowListener.NOOP;
        } else if (listener instanceof CompositeFlowListener) {
            this.listener = listener;
        } else {
            this.listener = new CompositeFlowListener(new FlowListener[]{listener});
        }
        return this;
    }

    /**
     * Set a custom executor for async flow execution.
     * <p>
     * By default, FlowExecutor uses virtual threads on Java 21+ (detected at runtime)
     * or a cached thread pool on Java 11-20. Use this method to provide a custom executor
     * when you need specific thread management behavior.
     * <p>
     * On Java 21+, use {@code Executors.newVirtualThreadPerTaskExecutor()} for optimal
     * scalability with thousands of concurrent flows. Virtual threads are lightweight
     * and handle blocking I/O efficiently without pinning carrier threads.
     *
     * @param executor the executor for running async flow tasks
     * @return this executor
     */
    public FlowExecutor withExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Set a transaction inspector for debugging.
     *
     * @param inspector the transaction inspector
     * @return this executor
     */
    public FlowExecutor withTxInspector(Consumer<Transaction> inspector) {
        this.txInspector = inspector;
        return this;
    }

    /**
     * Set the chaining mode for transaction execution.
     * <ul>
     *     <li>{@link ChainingMode#SEQUENTIAL} (default) - Wait for each transaction
     *         to be confirmed before proceeding to the next step. Transactions are
     *         guaranteed to be in separate blocks.</li>
     *     <li>{@link ChainingMode#PIPELINED} - Submit all transactions without waiting
     *         for confirmations between steps. Transactions can potentially land in the
     *         same block, providing faster execution.</li>
     * </ul>
     *
     * @param mode the chaining mode
     * @return this executor
     */
    public FlowExecutor withChainingMode(ChainingMode mode) {
        this.chainingMode = mode != null ? mode : ChainingMode.SEQUENTIAL;
        return this;
    }

    /**
     * Set a default retry policy for all steps.
     * <p>
     * This policy will be used for any step that doesn't have its own step-level retry policy.
     * If not set, no retries will be performed by default.
     *
     * @param retryPolicy the default retry policy
     * @return this executor
     */
    public FlowExecutor withDefaultRetryPolicy(RetryPolicy retryPolicy) {
        this.defaultRetryPolicy = retryPolicy;
        return this;
    }

    /**
     * Set the confirmation tracking configuration.
     * <p>
     * When set, enables advanced confirmation tracking with:
     * <ul>
     *     <li>Configurable confirmation depth thresholds</li>
     *     <li>Rollback detection</li>
     *     <li>Enhanced listener callbacks for confirmation progress</li>
     * </ul>
     * <p>
     * If not set, the executor uses simple confirmation checking (transaction exists = confirmed).
     *
     * @param config the confirmation configuration
     * @return this executor
     */
    public FlowExecutor withConfirmationConfig(ConfirmationConfig config) {
        this.confirmationConfig = config;
        if (config != null) {
            this.confirmationTracker = new ConfirmationTracker(chainDataSupplier, config);
        }
        return this;
    }

    /**
     * Set the rollback handling strategy.
     * <p>
     * Determines how the executor responds when a transaction rollback is detected:
     * <ul>
     *     <li>{@link RollbackStrategy#FAIL_IMMEDIATELY} (default) - Fail the flow immediately</li>
     *     <li>{@link RollbackStrategy#NOTIFY_ONLY} - Notify via listener but continue waiting</li>
     *     <li>{@link RollbackStrategy#REBUILD_FROM_FAILED} - Automatically rebuild and resubmit the failed step</li>
     *     <li>{@link RollbackStrategy#REBUILD_ENTIRE_FLOW} - Restart the entire flow from step 1</li>
     * </ul>
     * <p>
     * For REBUILD_FROM_FAILED and REBUILD_ENTIRE_FLOW strategies, the maximum number of
     * rebuild attempts is controlled by the {@code maxRollbackRetries} setting in {@link ConfirmationConfig}.
     * <p>
     * Note: This only takes effect when {@link #withConfirmationConfig(ConfirmationConfig)}
     * has been set, as rollback detection requires confirmation tracking.
     *
     * @param strategy the rollback strategy
     * @return this executor
     */
    public FlowExecutor withRollbackStrategy(RollbackStrategy strategy) {
        this.rollbackStrategy = strategy != null ? strategy : RollbackStrategy.FAIL_IMMEDIATELY;
        return this;
    }

    /**
     * Set a flow registry for automatic flow tracking.
     * <p>
     * When set, flows executed via {@link #execute(TxFlow)} will be automatically
     * registered in the registry. This enables centralized monitoring of all
     * running flows.
     * <p>
     * Note: Synchronous execution via {@link #executeSync(TxFlow)} does not
     * register flows since no FlowHandle is returned.
     *
     * @param registry the flow registry
     * @return this executor
     * @see FlowRegistry
     */
    public FlowExecutor withRegistry(FlowRegistry registry) {
        this.flowRegistry = registry;
        return this;
    }

    /**
     * Set a state store for persisting flow execution state.
     * <p>
     * When set, the executor will persist flow state on key transitions:
     * <ul>
     *     <li>Flow started - initial state saved</li>
     *     <li>Transaction submitted - SUBMITTED state</li>
     *     <li>Transaction confirmed - CONFIRMED state</li>
     *     <li>Transaction rolled back - ROLLED_BACK state</li>
     *     <li>Flow completed - final state</li>
     * </ul>
     *
     * @param stateStore the state store implementation
     * @return this executor
     * @see FlowStateStore
     */
    public FlowExecutor withStateStore(FlowStateStore stateStore) {
        this.flowStateStore = stateStore;
        return this;
    }

    // ==================== Execution Hooks ====================

    /**
     * Internal interface to unify sync and async execution paths.
     * Eliminates duplication between executeX and executeWithHandleX methods.
     */
    private interface ExecutionHooks {
        void onFlowStarting(TxFlow flow);
        void onStepStarting(FlowStep step);
        void onStepCompleted(FlowStep step, FlowStepResult result);
        void onTransactionSubmitted(TxFlow flow, FlowStep step, String txHash);
        void onTransactionConfirmed(TxFlow flow, FlowStep step, String txHash, ConfirmationResult confirmResult);
        void onFlowFailed(TxFlow flow, FlowStatus status);
        void onFlowCompleted(TxFlow flow);
        void onFlowRestarting();
        void onRollbackDetected(TxFlow flow, FlowStep step, String txHash, long prevBlockHeight, String msg);
        boolean isCancelled();
    }

    /**
     * Create hooks for synchronous (no FlowHandle) execution.
     * Only calls persistence methods, no handle tracking.
     */
    private ExecutionHooks syncHooks(TxFlow flow) {
        return new ExecutionHooks() {
            @Override public void onFlowStarting(TxFlow f) { persistFlowStarted(f); }
            @Override public void onStepStarting(FlowStep step) { /* no handle to update */ }
            @Override public void onStepCompleted(FlowStep step, FlowStepResult result) { /* no handle to update */ }
            @Override public void onTransactionSubmitted(TxFlow f, FlowStep step, String txHash) {
                persistTransactionSubmitted(f, step, txHash);
            }
            @Override public void onTransactionConfirmed(TxFlow f, FlowStep step, String txHash, ConfirmationResult confirmResult) {
                Long blockHeight = confirmResult != null ? confirmResult.getBlockHeight() : null;
                Integer confirmDepth = confirmResult != null ? confirmResult.getConfirmationDepth() : null;
                persistTransactionConfirmed(f, step, txHash, blockHeight, confirmDepth);
            }
            @Override public void onFlowFailed(TxFlow f, FlowStatus status) { persistFlowComplete(f, FlowStatus.FAILED); }
            @Override public void onFlowCompleted(TxFlow f) { persistFlowComplete(f, FlowStatus.COMPLETED); }
            @Override public void onFlowRestarting() { /* no handle to reset */ }
            @Override public void onRollbackDetected(TxFlow f, FlowStep step, String txHash, long prevBlockHeight, String msg) {
                persistTransactionRolledBack(f, step, txHash, prevBlockHeight, msg);
            }
            @Override public boolean isCancelled() { return false; }
        };
    }

    /**
     * Create hooks for async (FlowHandle-backed) execution.
     * Calls persistence methods AND updates FlowHandle tracking state.
     */
    private ExecutionHooks handleHooks(TxFlow flow, FlowHandle handle) {
        return new ExecutionHooks() {
            @Override public void onFlowStarting(TxFlow f) { persistFlowStarted(f); }
            @Override public void onStepStarting(FlowStep step) { handle.updateCurrentStep(step.getId()); }
            @Override public void onStepCompleted(FlowStep step, FlowStepResult result) { handle.incrementCompletedSteps(); }
            @Override public void onTransactionSubmitted(TxFlow f, FlowStep step, String txHash) {
                persistTransactionSubmitted(f, step, txHash);
            }
            @Override public void onTransactionConfirmed(TxFlow f, FlowStep step, String txHash, ConfirmationResult confirmResult) {
                Long blockHeight = confirmResult != null ? confirmResult.getBlockHeight() : null;
                Integer confirmDepth = confirmResult != null ? confirmResult.getConfirmationDepth() : null;
                persistTransactionConfirmed(f, step, txHash, blockHeight, confirmDepth);
            }
            @Override public void onFlowFailed(TxFlow f, FlowStatus status) {
                handle.updateStatus(FlowStatus.FAILED);
                persistFlowComplete(f, FlowStatus.FAILED);
            }
            @Override public void onFlowCompleted(TxFlow f) {
                handle.updateStatus(FlowStatus.COMPLETED);
                persistFlowComplete(f, FlowStatus.COMPLETED);
            }
            @Override public void onFlowRestarting() { handle.resetCompletedSteps(); }
            @Override public void onRollbackDetected(TxFlow f, FlowStep step, String txHash, long prevBlockHeight, String msg) {
                persistTransactionRolledBack(f, step, txHash, prevBlockHeight, msg);
            }
            @Override public boolean isCancelled() { return handle.isCancelled(); }
        };
    }

    // ==================== Public Execution Methods ====================

    /**
     * Validate executor configuration before execution.
     * <p>
     * Ensures that rollback strategies that require confirmation tracking
     * have a ConfirmationConfig set.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    private void validateConfiguration() {
        if (rollbackStrategy != RollbackStrategy.FAIL_IMMEDIATELY && confirmationTracker == null) {
            throw new IllegalStateException(
                    "Rollback strategy " + rollbackStrategy + " requires confirmation tracking. " +
                    "Call withConfirmationConfig() before execute().");
        }
    }

    /**
     * Resume a previously failed flow synchronously from the point of failure.
     * <p>
     * This method inspects the previous result to identify which steps completed
     * successfully, verifies those transactions are still on-chain, and re-executes
     * from the first unverified or failed step.
     * <p>
     * <b>How it works:</b>
     * <ul>
     *     <li>Validates flow ID matches between the flow and previous result</li>
     *     <li>Verifies each previously-successful step's tx is still confirmed on-chain</li>
     *     <li>Pre-populates execution context with verified step results</li>
     *     <li>Executes remaining steps normally using existing dependency resolution</li>
     * </ul>
     * <p>
     * <b>UTXO correctness:</b> Steps without dependencies on skipped steps fetch fresh UTXOs
     * from the blockchain (where skipped steps' consumed UTXOs are already absent). Steps
     * with dependencies resolve from context, which contains real on-chain UTXOs.
     *
     * @param flow the flow to resume (step IDs must match for skipping)
     * @param previousResult the result from a previous failed execution
     * @return the flow result
     * @throws IllegalArgumentException if previousResult is null, flow IDs mismatch, or previous result was successful
     * @throws FlowExecutionException if flow validation fails
     */
    public FlowResult resumeSync(TxFlow flow, FlowResult previousResult) {
        validateConfiguration();

        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }

        validateResumeArgs(flow, previousResult);

        Map<Integer, FlowStepResult> confirmedSteps = verifyPreviousSteps(flow, previousResult);

        if (!activeFlowIds.add(flow.getId())) {
            throw new IllegalStateException("Flow '" + flow.getId() + "' is already executing");
        }

        try {
            switch (chainingMode) {
                case PIPELINED:
                    return doExecutePipelinedWithResume(flow, syncHooks(flow), confirmedSteps);
                case BATCH:
                    return doExecuteBatchWithResume(flow, syncHooks(flow), confirmedSteps);
                case SEQUENTIAL:
                default:
                    return doExecuteSequentialWithResume(flow, syncHooks(flow), confirmedSteps);
            }
        } finally {
            activeFlowIds.remove(flow.getId());
        }
    }

    /**
     * Resume a previously failed flow asynchronously from the point of failure.
     * <p>
     * Verification of previous steps is performed eagerly (before launching async)
     * for fail-fast behavior. The actual re-execution runs on the configured executor.
     *
     * @param flow the flow to resume
     * @param previousResult the result from a previous failed execution
     * @return a handle for monitoring the resumed execution
     * @throws IllegalArgumentException if previousResult is null, flow IDs mismatch, or previous result was successful
     * @throws FlowExecutionException if flow validation fails
     * @throws IllegalStateException if the flow is already executing
     */
    public FlowHandle resume(TxFlow flow, FlowResult previousResult) {
        validateConfiguration();

        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }

        validateResumeArgs(flow, previousResult);

        // Verify previous steps BEFORE launching async (fail-fast)
        Map<Integer, FlowStepResult> confirmedSteps = verifyPreviousSteps(flow, previousResult);

        if (!activeFlowIds.add(flow.getId())) {
            throw new IllegalStateException("Flow '" + flow.getId() + "' is already executing");
        }

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);
        activeHandles.add(handle);

        if (flowRegistry != null) {
            flowRegistry.register(flow.getId(), handle);
        }

        Runnable task = () -> {
            try {
                handle.updateStatus(FlowStatus.IN_PROGRESS);
                FlowResult result;
                switch (chainingMode) {
                    case PIPELINED:
                        result = doExecutePipelinedWithResume(flow, handleHooks(flow, handle), confirmedSteps);
                        break;
                    case BATCH:
                        result = doExecuteBatchWithResume(flow, handleHooks(flow, handle), confirmedSteps);
                        break;
                    case SEQUENTIAL:
                    default:
                        result = doExecuteSequentialWithResume(flow, handleHooks(flow, handle), confirmedSteps);
                        break;
                }
                activeFlowIds.remove(flow.getId());
                activeHandles.remove(handle);
                future.complete(result);
            } catch (Exception e) {
                activeFlowIds.remove(flow.getId());
                activeHandles.remove(handle);
                handle.updateStatus(FlowStatus.FAILED);
                future.completeExceptionally(e);
            }
        };

        Executor effectiveExecutor = executor != null ? executor : DEFAULT_EXECUTOR;
        effectiveExecutor.execute(task);

        return handle;
    }

    /**
     * Execute a flow synchronously.
     *
     * @param flow the flow to execute
     * @return the flow result
     */
    public FlowResult executeSync(TxFlow flow) {
        validateConfiguration();

        // Validate the flow
        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }

        if (!activeFlowIds.add(flow.getId())) {
            throw new IllegalStateException("Flow '" + flow.getId() + "' is already executing");
        }

        try {
            switch (chainingMode) {
                case PIPELINED:
                    return executePipelined(flow);
                case BATCH:
                    return executeBatch(flow);
                case SEQUENTIAL:
                default:
                    return executeSequential(flow);
            }
        } finally {
            activeFlowIds.remove(flow.getId());
        }
    }

    /**
     * Thin wrapper: synchronous SEQUENTIAL execution.
     */
    private FlowResult executeSequential(TxFlow flow) {
        return doExecuteSequential(flow, syncHooks(flow));
    }

    /**
     * Execute flow in SEQUENTIAL mode - wait for each step to confirm before next.
     * <p>
     * This method supports rollback recovery strategies:
     * <ul>
     *     <li>REBUILD_FROM_FAILED: Rebuilds and resubmits only the failed step</li>
     *     <li>REBUILD_ENTIRE_FLOW: Restarts the entire flow from step 1</li>
     * </ul>
     */
    private FlowResult doExecuteSequential(TxFlow flow, ExecutionHooks hooks) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<String, Integer> stepRollbackAttempts = new ConcurrentHashMap<>();
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
                hooks.onFlowStarting(flow);
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();

            try {
                for (int i = 0; i < totalSteps; i++) {
                    // Check for cancellation
                    if (hooks.isCancelled()) {
                        FlowResult cancelledResult = resultBuilder.withStatus(FlowStatus.CANCELLED)
                                .completedAt(Instant.now()).build();
                        listener.onFlowFailed(flow, cancelledResult);
                        return cancelledResult;
                    }

                    FlowStep step = steps.get(i);
                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRollbackHandling(
                            step, context, flow.getVariables(), false,
                            stepRollbackAttempts, maxRollbackRetries, steps, cancelCheck);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        String txHash = stepResult.getTransactionHash();
                        flowTxHashes.add(txHash);
                        hooks.onTransactionSubmitted(flow, step, txHash);
                        hooks.onStepCompleted(step, stepResult);
                        listener.onStepCompleted(step, stepResult);

                        // Get confirmation details - tx is already confirmed from completeAndWait()
                        Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step, cancelCheck);
                        hooks.onTransactionConfirmed(flow, step, txHash, confirmResult.orElse(null));
                    } else {
                        listener.onStepFailed(step, stepResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                // Cleanup tracked transactions to prevent memory leak
                if (confirmationTracker != null) {
                    for (String hash : flowTxHashes) {
                        confirmationTracker.stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                if (e.isRequiresFlowRestart()) {
                    flowRestartAttempts++;
                    if (flowRestartAttempts > maxRollbackRetries) {
                        log.error("Flow restart limit ({}) reached after rollback at step '{}'",
                                maxRollbackRetries, e.getStep().getId());
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.failure(
                                new FlowExecutionException("Flow restart limit reached after rollback", e));
                        listener.onFlowFailed(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }

                    log.info("Restarting flow (attempt {}/{}) due to rollback at step '{}'",
                            flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                    listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                            "Rollback detected at step '" + e.getStep().getId() + "'");
                    waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck);
                    stepRollbackAttempts.clear();
                    hooks.onFlowRestarting();
                    continue;
                } else {
                    log.error("Step rebuild failed for step '{}'", e.getStep().getId());
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(e);
                    listener.onFlowFailed(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }
            } catch (Exception e) {
                log.error("Flow execution failed", e);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("Flow execution failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Get the maximum rollback retries from configuration.
     */
    private int getMaxRollbackRetries() {
        return confirmationConfig != null ? confirmationConfig.getMaxRollbackRetries() : 3;
    }

    /**
     * Get the confirmation timeout from configuration or use default.
     */
    private Duration getConfirmationTimeout() {
        return confirmationConfig != null ? confirmationConfig.getTimeout() : DEFAULT_CONFIRMATION_TIMEOUT;
    }

    /**
     * Get the check interval from configuration or use default.
     */
    private Duration getCheckInterval() {
        return confirmationConfig != null ? confirmationConfig.getCheckInterval() : DEFAULT_CHECK_INTERVAL;
    }

    /**
     * Execute a step with rollback handling.
     * <p>
     * This method wraps executeStepWithRetry and handles RollbackException for
     * REBUILD_FROM_FAILED strategy by rebuilding and resubmitting the step.
     * <p>
     * If the step has downstream dependents (other steps depend on its outputs),
     * REBUILD_FROM_FAILED is auto-escalated to a flow restart, since downstream
     * steps would need to be rebuilt too.
     *
     * @throws RollbackException when using REBUILD_ENTIRE_FLOW strategy or when
     *         rebuild attempts are exhausted for REBUILD_FROM_FAILED
     */
    private FlowStepResult executeStepWithRollbackHandling(FlowStep step, FlowExecutionContext context,
                                                            java.util.Map<String, Object> variables,
                                                            boolean pipelined,
                                                            Map<String, Integer> stepRollbackAttempts,
                                                            int maxRollbackRetries,
                                                            List<FlowStep> allSteps,
                                                            BooleanSupplier cancelCheck) {
        while (true) {
            if (cancelCheck.getAsBoolean()) {
                return FlowStepResult.failure(step.getId(), new RuntimeException("Flow cancelled"));
            }

            try {
                return executeStepWithRetry(step, context, variables, pipelined, cancelCheck);
            } catch (RollbackException e) {
                if (e.isRequiresFlowRestart()) {
                    // REBUILD_ENTIRE_FLOW: propagate exception to flow level
                    throw e;
                }

                // Auto-escalate REBUILD_FROM_FAILED to flow restart if step has downstream dependents
                if (hasDownstreamDependents(step, allSteps)) {
                    log.info("Step '{}' has downstream dependents, escalating to flow restart", step.getId());
                    throw RollbackException.forFlowRestart(e.getTxHash(), step, e.getPreviousBlockHeight());
                }

                // REBUILD_FROM_FAILED: try to rebuild this step
                int currentAttempts = stepRollbackAttempts.getOrDefault(step.getId(), 0) + 1;
                stepRollbackAttempts.put(step.getId(), currentAttempts);

                if (currentAttempts > maxRollbackRetries) {
                    log.error("Step '{}' rebuild limit ({}) reached", step.getId(), maxRollbackRetries);
                    throw new RollbackException(
                            "Step rebuild limit reached for step '" + step.getId() + "'",
                            e.getTxHash(), step, e.getPreviousBlockHeight(), false);
                }

                log.info("Rebuilding step '{}' (attempt {}/{}) after rollback",
                        step.getId(), currentAttempts, maxRollbackRetries);
                listener.onStepRebuilding(step, currentAttempts, maxRollbackRetries,
                        "Transaction " + e.getTxHash() + " rolled back");

                waitForBackendReadyAfterRollback(List.of(e.getTxHash()), cancelCheck); // Wait for backend to sync after rollback

                // Clear the step result so it can be rebuilt
                context.clearStepResult(step.getId());

                // Continue loop to retry the step
            }
        }
    }

    /**
     * Check if a step has downstream dependents (other steps that depend on its outputs).
     */
    private boolean hasDownstreamDependents(FlowStep step, List<FlowStep> allSteps) {
        boolean foundSelf = false;
        for (FlowStep s : allSteps) {
            if (s.getId().equals(step.getId())) {
                foundSelf = true;
                continue;
            }
            if (foundSelf && s.getDependencyStepIds().contains(step.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Thin wrapper: synchronous PIPELINED execution.
     */
    private FlowResult executePipelined(TxFlow flow) {
        return doExecutePipelined(flow, syncHooks(flow));
    }

    /**
     * Execute flow in PIPELINED mode - submit all transactions, then wait for confirmations.
     * <p>
     * This enables true transaction chaining where multiple transactions can land in the same block.
     * <p>
     * On restart after rollback, steps whose transactions are still confirmed on-chain
     * are skipped to avoid unnecessary rebuilding.
     */
    private FlowResult doExecutePipelined(TxFlow flow, ExecutionHooks hooks) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>();
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
                hooks.onFlowStarting(flow);
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();
            List<String> submittedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();

            // Determine which steps to skip (still confirmed from previous attempt)
            Set<Integer> skippedStepIndices = new HashSet<>();
            if (!previousConfirmedSteps.isEmpty()) {
                skippedStepIndices = verifyAndPrepareSkippedSteps(
                        previousConfirmedSteps, steps, context, resultBuilder);
            }

            try {
                // Phase 1: Build and submit all transactions without waiting
                log.info("PIPELINED mode: Submitting {} transactions (skipping {} confirmed)",
                        totalSteps, skippedStepIndices.size());
                for (int i = 0; i < totalSteps; i++) {
                    // Check for cancellation
                    if (hooks.isCancelled()) {
                        FlowResult cancelledResult = resultBuilder.withStatus(FlowStatus.CANCELLED)
                                .completedAt(Instant.now()).build();
                        listener.onFlowFailed(flow, cancelledResult);
                        return cancelledResult;
                    }

                    FlowStep step = steps.get(i);
                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    if (skippedStepIndices.contains(i)) {
                        // Reuse previous result
                        FlowStepResult prevResult = previousConfirmedSteps.get(i);
                        stepResults.add(prevResult);
                        submittedTxHashes.add(prevResult.getTransactionHash());
                        flowTxHashes.add(prevResult.getTransactionHash());
                        resultBuilder.addStepResult(prevResult);
                        log.info("Step '{}' skipped (still confirmed): {}", step.getId(), prevResult.getTransactionHash());
                        continue;
                    }

                    FlowStepResult stepResult = executeStepWithRetry(step, context, flow.getVariables(), true, cancelCheck);
                    stepResults.add(stepResult);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        submittedTxHashes.add(stepResult.getTransactionHash());
                        flowTxHashes.add(stepResult.getTransactionHash());
                        listener.onTransactionSubmitted(step, stepResult.getTransactionHash());
                        hooks.onTransactionSubmitted(flow, step, stepResult.getTransactionHash());
                        log.debug("Step '{}' submitted: {}", step.getId(), stepResult.getTransactionHash());
                    } else {
                        listener.onStepFailed(step, stepResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                // Phase 2: Wait for all transactions to be confirmed
                log.info("PIPELINED mode: Waiting for {} transactions to confirm", submittedTxHashes.size());
                for (int i = 0; i < submittedTxHashes.size(); i++) {
                    String txHash = submittedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        // Already confirmed
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        continue;
                    }

                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step, cancelCheck);
                    if (confirmResult.isPresent()) {
                        ConfirmationResult result = confirmResult.get();
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        hooks.onTransactionConfirmed(flow, step, txHash, result);
                        log.debug("Step '{}' confirmed: {} at block {}", step.getId(), txHash,
                                result.getBlockHeight());
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new ConfirmationTimeoutException(txHash));
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailedResult;
                    }
                }

                // Cleanup tracked transactions to prevent memory leak
                if (confirmationTracker != null) {
                    for (String hash : flowTxHashes) {
                        confirmationTracker.stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    log.error("Flow restart limit ({}) reached after rollback at step '{}' in PIPELINED mode",
                            maxRollbackRetries, e.getStep().getId());
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in PIPELINED mode", e));
                    listener.onFlowFailed(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }

                log.info("Restarting PIPELINED flow (attempt {}/{}) due to rollback at step '{}'",
                        flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                        "Rollback detected at step '" + e.getStep().getId() + "' in PIPELINED mode");

                // Find steps that are still confirmed before clearing tracker
                previousConfirmedSteps = findStillConfirmedSteps(steps, submittedTxHashes, stepResults);

                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck);
                hooks.onFlowRestarting();
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("PIPELINED flow execution failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Find steps whose transactions are still confirmed on-chain after a rollback.
     * Used by PIPELINED mode to skip already-confirmed steps on restart.
     */
    private Map<Integer, FlowStepResult> findStillConfirmedSteps(
            List<FlowStep> steps, List<String> txHashes, List<FlowStepResult> results) {
        Map<Integer, FlowStepResult> confirmed = new HashMap<>();
        for (int i = 0; i < Math.min(txHashes.size(), results.size()); i++) {
            String txHash = txHashes.get(i);
            if (txHash == null) continue;
            try {
                Optional<TransactionInfo> txInfo = chainDataSupplier.getTransactionInfo(txHash);
                if (txInfo.isPresent() && txInfo.get().getBlockHeight() != null) {
                    confirmed.put(i, results.get(i));
                    log.debug("Step '{}' tx {} still confirmed at block {}",
                            steps.get(i).getId(), txHash, txInfo.get().getBlockHeight());
                }
            } catch (Exception e) {
                // Assume NOT confirmed (safer to rebuild)
                log.debug("Could not verify step '{}' tx {}, will rebuild", steps.get(i).getId(), txHash);
            }
        }
        return confirmed;
    }

    /**
     * Verify previously confirmed steps are still on-chain and pre-populate context.
     */
    private Set<Integer> verifyAndPrepareSkippedSteps(
            Map<Integer, FlowStepResult> previousConfirmedSteps,
            List<FlowStep> steps, FlowExecutionContext context,
            FlowResult.Builder resultBuilder) {
        Set<Integer> verified = new HashSet<>();
        for (Map.Entry<Integer, FlowStepResult> entry : previousConfirmedSteps.entrySet()) {
            int idx = entry.getKey();
            FlowStepResult prevResult = entry.getValue();
            String txHash = prevResult.getTransactionHash();
            try {
                Optional<TransactionInfo> txInfo = chainDataSupplier.getTransactionInfo(txHash);
                if (txInfo.isPresent() && txInfo.get().getBlockHeight() != null) {
                    // Still confirmed — pre-populate context
                    context.recordStepResult(steps.get(idx).getId(), prevResult);
                    verified.add(idx);
                } else {
                    log.info("Previously confirmed step '{}' tx {} no longer on-chain, will rebuild",
                            steps.get(idx).getId(), txHash);
                }
            } catch (Exception e) {
                log.debug("Could not re-verify step '{}' tx {}, will rebuild", steps.get(idx).getId(), txHash);
            }
        }
        return verified;
    }

    /**
     * Wait for a transaction to be confirmed on-chain.
     *
     * @param txHash the transaction hash to wait for
     * @return Optional containing ConfirmationResult if confirmed, empty if timeout or failure
     */
    private Optional<ConfirmationResult> waitForConfirmation(String txHash) {
        return waitForConfirmation(txHash, null, () -> false);
    }

    /**
     * Wait for a transaction to be confirmed on-chain with enhanced tracking.
     * <p>
     * If confirmation tracking is configured, this method:
     * <ul>
     *     <li>Tracks confirmation depth and status progression</li>
     *     <li>Detects rollbacks and handles them according to the rollback strategy</li>
     *     <li>Fires listener callbacks for confirmation progress</li>
     * </ul>
     *
     * @param txHash the transaction hash to wait for
     * @param step the flow step (for listener callbacks, can be null)
     * @return Optional containing ConfirmationResult if confirmed, empty if timeout or rollback
     */
    private Optional<ConfirmationResult> waitForConfirmation(String txHash, FlowStep step,
                                                               BooleanSupplier cancelCheck) {
        // Use enhanced confirmation tracking if configured
        if (confirmationTracker != null) {
            return waitForConfirmationWithTracking(txHash, step, cancelCheck);
        }

        // Fall back to simple confirmation checking
        long startTime = System.currentTimeMillis();
        long timeoutMs = getConfirmationTimeout().toMillis();
        long intervalMs = getCheckInterval().toMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (cancelCheck.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return Optional.empty();
            }
            try {
                Optional<TransactionInfo> txInfo = chainDataSupplier.getTransactionInfo(txHash);
                if (txInfo.isPresent()) {
                    // Build a minimal ConfirmationResult for simple polling mode
                    // Block height is available from the transaction response
                    return Optional.of(ConfirmationResult.builder()
                            .txHash(txHash)
                            .status(ConfirmationStatus.CONFIRMED)
                            .blockHeight(txInfo.get().getBlockHeight())
                            .blockHash(txInfo.get().getBlockHash())
                            .confirmationDepth(0)  // Not tracked in simple mode
                            .build());
                }
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception e) {
                log.debug("Waiting for tx confirmation: {}", txHash);
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Wait for confirmation using the enhanced ConfirmationTracker.
     * <p>
     * This method handles rollback strategies:
     * <ul>
     *     <li>FAIL_IMMEDIATELY: Returns empty on rollback</li>
     *     <li>NOTIFY_ONLY: Continues waiting after notifying listeners</li>
     *     <li>REBUILD_FROM_FAILED: Throws RollbackException for step rebuild</li>
     *     <li>REBUILD_ENTIRE_FLOW: Throws RollbackException for flow restart</li>
     * </ul>
     *
     * @param txHash the transaction hash to wait for
     * @param step the flow step (for listener callbacks, can be null)
     * @return Optional containing ConfirmationResult if confirmed, empty if timeout or rollback
     * @throws RollbackException when using REBUILD_FROM_FAILED or REBUILD_ENTIRE_FLOW strategies
     */
    private Optional<ConfirmationResult> waitForConfirmationWithTracking(String txHash, FlowStep step,
                                                                          BooleanSupplier cancelCheck) {
        ConfirmationStatus targetStatus = ConfirmationStatus.CONFIRMED;

        // Track the last known status for detecting transitions
        final ConfirmationStatus[] lastStatus = {null};
        final Long[] firstBlockHeight = {null};
        int notifyOnlyRepolls = 0;

        while (true) {
            if (cancelCheck.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return Optional.empty();
            }
            ConfirmationResult result = confirmationTracker.waitForConfirmation(txHash, targetStatus,
                    (hash, confirmResult) -> {
                        if (step == null) return;

                        ConfirmationStatus currentStatus = confirmResult.getStatus();
                        int depth = confirmResult.getConfirmationDepth();

                        // Fire callbacks on status transitions
                        if (lastStatus[0] != currentStatus) {
                            // Transition: SUBMITTED -> IN_BLOCK
                            if (currentStatus == ConfirmationStatus.IN_BLOCK && lastStatus[0] == ConfirmationStatus.SUBMITTED) {
                                firstBlockHeight[0] = confirmResult.getBlockHeight();
                                if (confirmResult.getBlockHeight() != null) {
                                    listener.onTransactionInBlock(step, txHash, confirmResult.getBlockHeight());
                                }
                            }
                            lastStatus[0] = currentStatus;
                        }

                        // Always fire depth changed callback when depth changes
                        listener.onConfirmationDepthChanged(step, txHash, depth, currentStatus);
                    },
                    cancelCheck);

            // Handle rollback
            if (result.isRolledBack()) {
                long prevHeight = firstBlockHeight[0] != null ? firstBlockHeight[0] : 0;

                if (step != null) {
                    listener.onTransactionRolledBack(step, txHash, prevHeight);
                }

                switch (rollbackStrategy) {
                    case FAIL_IMMEDIATELY:
                        log.warn("Transaction {} rolled back, failing flow (FAIL_IMMEDIATELY strategy)", txHash);
                        return Optional.empty();

                    case NOTIFY_ONLY:
                        notifyOnlyRepolls++;
                        if (notifyOnlyRepolls > getMaxRollbackRetries()) {
                            log.warn("NOTIFY_ONLY re-poll limit ({}) reached for tx {}", getMaxRollbackRetries(), txHash);
                            return Optional.empty();
                        }
                        // Clear stale tracker state and re-enter polling
                        // The tx may be re-included from mempool after new blocks are mined
                        log.warn("Transaction {} rolled back, re-entering confirmation polling (NOTIFY_ONLY strategy, attempt {}/{})",
                                txHash, notifyOnlyRepolls, getMaxRollbackRetries());
                        confirmationTracker.stopTracking(txHash);
                        firstBlockHeight[0] = null;
                        lastStatus[0] = null;
                        continue;  // re-enter the while loop to poll again

                    case REBUILD_FROM_FAILED:
                        log.info("Transaction {} rolled back, will rebuild step (REBUILD_FROM_FAILED strategy)", txHash);
                        if (step != null) {
                            throw RollbackException.forStepRebuild(txHash, step, prevHeight);
                        }
                        return Optional.empty();

                    case REBUILD_ENTIRE_FLOW:
                        log.info("Transaction {} rolled back, will restart flow (REBUILD_ENTIRE_FLOW strategy)", txHash);
                        if (step != null) {
                            throw RollbackException.forFlowRestart(txHash, step, prevHeight);
                        }
                        return Optional.empty();
                }
            }

            // Check if target status was reached
            if (result.hasReached(targetStatus)) {
                return Optional.of(result);
            }

            // Timeout or other failure — exit
            if (result.getError() != null) {
                log.warn("Confirmation wait failed for tx {}: {}", txHash, result.getError().getMessage());
            }
            return Optional.empty();
        }
    }

    /**
     * Wait for the backend to be ready after a rollback.
     * Retries querying the backend until it responds successfully.
     * This handles both test scenarios (node restart) and production scenarios (network issues).
     *
     * @param maxAttempts maximum number of retry attempts
     * @param retryDelayMs delay between retries in milliseconds
     */
    private void waitForBackendReady(int maxAttempts, long retryDelayMs, BooleanSupplier cancelCheck) {
        log.debug("Waiting for backend to be ready (max {} attempts)...", maxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (cancelCheck.getAsBoolean()) {
                return;
            }
            try {
                // Try to query the backend - if successful, it's ready
                long tipHeight = chainDataSupplier.getChainTipHeight();
                log.debug("Backend is ready (attempt {}, block height: {})", attempt, tipHeight);
                return;
            } catch (Exception e) {
                log.debug("Backend not ready yet (attempt {}): {}", attempt, e.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        log.warn("Backend may not be fully ready after {} attempts", maxAttempts);
    }

    /**
     * Wait for backend to be ready after a rollback.
     * <p>
     * Clears only this flow's tracked transactions (scoped to flowTxHashes)
     * to avoid interfering with other concurrent flows.
     * <p>
     * Additional wait behavior is controlled by the {@link ConfirmationConfig}:
     * <ul>
     *     <li>If {@code waitForBackendAfterRollback} is false (production default):
     *         Only clears tracker, no wait.</li>
     *     <li>If {@code waitForBackendAfterRollback} is true (devnet/test):
     *         Waits for backend ready and optional UTXO sync delay.</li>
     * </ul>
     */
    private void waitForBackendReadyAfterRollback(List<String> flowTxHashes, BooleanSupplier cancelCheck) {
        // Clear only this flow's tracked transactions (scoped, not global)
        if (confirmationTracker != null) {
            log.debug("Clearing confirmation tracker state for {} flow transactions after rollback", flowTxHashes.size());
            for (String txHash : flowTxHashes) {
                confirmationTracker.stopTracking(txHash);
            }
        }

        // Skip wait logic if not configured (production default)
        if (confirmationConfig == null || !confirmationConfig.isWaitForBackendAfterRollback()) {
            log.debug("Skipping backend wait (not configured for post-rollback wait)");
            return;
        }

        // Wait for backend ready (for test scenarios like Yaci DevKit)
        long retryDelayMs = confirmationConfig.getCheckInterval().toMillis();
        int maxAttempts = confirmationConfig.getPostRollbackWaitAttempts();
        waitForBackendReady(maxAttempts, retryDelayMs, cancelCheck);

        // Optional additional delay for UTXO indexer sync
        if (cancelCheck.getAsBoolean()) {
            return;
        }
        Duration utxoSyncDelay = confirmationConfig.getPostRollbackUtxoSyncDelay();
        if (utxoSyncDelay != null && !utxoSyncDelay.isZero()) {
            try {
                log.debug("Waiting {}ms for UTXO indexer to sync", utxoSyncDelay.toMillis());
                Thread.sleep(utxoSyncDelay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Execute a flow asynchronously.
     * <p>
     * If a {@link FlowRegistry} is configured via {@link #withRegistry(FlowRegistry)},
     * the flow will be automatically registered for tracking.
     *
     * @param flow the flow to execute
     * @return a handle for monitoring the execution
     */
    public FlowHandle execute(TxFlow flow) {
        validateConfiguration();

        if (!activeFlowIds.add(flow.getId())) {
            throw new IllegalStateException("Flow '" + flow.getId() + "' is already executing");
        }

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);
        activeHandles.add(handle);

        // Auto-register with flow registry if configured
        if (flowRegistry != null) {
            flowRegistry.register(flow.getId(), handle);
        }

        Runnable task = () -> {
            try {
                handle.updateStatus(FlowStatus.IN_PROGRESS);
                FlowResult result = executeWithHandle(flow, handle);
                // Clean up BEFORE completing the future so callers unblocked by
                // await() can immediately re-execute the same flow ID.
                activeFlowIds.remove(flow.getId());
                activeHandles.remove(handle);
                future.complete(result);
            } catch (Exception e) {
                activeFlowIds.remove(flow.getId());
                activeHandles.remove(handle);
                handle.updateStatus(FlowStatus.FAILED);
                future.completeExceptionally(e);
            }
        };

        Executor effectiveExecutor = executor != null ? executor : DEFAULT_EXECUTOR;
        effectiveExecutor.execute(task);

        return handle;
    }

    private FlowResult executeWithHandle(TxFlow flow, FlowHandle handle) {
        // Validate the flow
        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }

        switch (chainingMode) {
            case PIPELINED:
                return executeWithHandlePipelined(flow, handle);
            case BATCH:
                return executeWithHandleBatch(flow, handle);
            case SEQUENTIAL:
            default:
                return executeWithHandleSequential(flow, handle);
        }
    }

    /**
     * Thin wrapper: async SEQUENTIAL execution with FlowHandle.
     */
    private FlowResult executeWithHandleSequential(TxFlow flow, FlowHandle handle) {
        return doExecuteSequential(flow, handleHooks(flow, handle));
    }

    /**
     * Thin wrapper: async PIPELINED execution with FlowHandle.
     */
    private FlowResult executeWithHandlePipelined(TxFlow flow, FlowHandle handle) {
        return doExecutePipelined(flow, handleHooks(flow, handle));
    }

    /**
     * Execute a step with retry logic.
     * <p>
     * This method wraps the actual step execution and handles retries according to the
     * configured retry policy. It uses the step-level policy if available, otherwise
     * falls back to the default policy.
     *
     * @param step the step to execute
     * @param context the execution context
     * @param variables the flow variables
     * @param pipelined true if executing in pipelined mode
     * @return the step result
     */
    private FlowStepResult executeStepWithRetry(FlowStep step, FlowExecutionContext context,
                                                 java.util.Map<String, Object> variables, boolean pipelined,
                                                 BooleanSupplier cancelCheck) {
        // Determine retry policy (step-level overrides default)
        RetryPolicy policy = step.hasRetryPolicy() ? step.getRetryPolicy() : defaultRetryPolicy;
        int maxAttempts = (policy != null) ? policy.getMaxAttempts() : 1;

        Throwable lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                FlowStepResult result = pipelined
                        ? executeStepPipelined(step, context, variables)
                        : executeStepSequential(step, context, variables, cancelCheck);

                if (result.isSuccessful()) {
                    return result;  // Success!
                }

                // Check if error is retryable
                lastError = result.getError();
                if (policy == null || !policy.isRetryable(lastError) || attempt >= maxAttempts) {
                    if (attempt > 1) {
                        listener.onStepRetryExhausted(step, attempt, lastError);
                    }
                    return result;  // Not retryable or max attempts reached
                }

                // Notify retry
                listener.onStepRetry(step, attempt, maxAttempts, lastError);
                log.info("Retrying step '{}' (attempt {}/{}): {}",
                        step.getId(), attempt + 1, maxAttempts,
                        lastError != null ? lastError.getMessage() : "unknown error");

                // Check for cancellation before retry wait
                if (cancelCheck.getAsBoolean()) {
                    return FlowStepResult.failure(step.getId(), new RuntimeException("Flow cancelled"));
                }

                // Wait before retry
                Duration delay = policy.calculateDelay(attempt);
                Thread.sleep(delay.toMillis());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FlowStepResult.failure(step.getId(), e);
            }
        }

        // Should not reach here, but safety return
        return FlowStepResult.failure(step.getId(), lastError);
    }

    /**
     * Execute a single step in SEQUENTIAL mode (waits for confirmation).
     */
    private FlowStepResult executeStepSequential(FlowStep step, FlowExecutionContext context,
                                                  java.util.Map<String, Object> variables,
                                                  BooleanSupplier cancelCheck) {
        String stepId = step.getId();
        log.debug("Executing step '{}'", stepId);

        try {
            // Create appropriate UtxoSupplier based on dependencies
            UtxoSupplier utxoSupplier = createUtxoSupplier(step, context);

            // Create QuickTxBuilder with the appropriate UTXO supplier
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

            // Get TxContext from step (either from TxPlan or TxContext factory)
            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                TxPlan plan = step.getTxPlan();
                // Merge flow variables into plan
                for (var entry : variables.entrySet()) {
                    if (!plan.getVariables().containsKey(entry.getKey())) {
                        plan.addVariable(entry.getKey(), entry.getValue());
                    }
                }
                txContext = quickTxBuilder.compose(plan, signerRegistry);
            } else if (step.hasTxContextFactory()) {
                // Call user's factory with our builder (which has chain-aware UTXO supplier)
                txContext = step.getTxContextFactory().apply(quickTxBuilder);
            } else {
                throw new FlowExecutionException("Step '" + stepId + "' has neither TxPlan nor TxContext factory");
            }

            // Apply tx inspector if set
            if (txInspector != null) {
                txContext.withTxInspector(txInspector);
            }

            // Store the built transaction for capturing outputs/inputs
            Transaction[] builtTx = new Transaction[1];
            txContext.withTxInspector(tx -> {
                builtTx[0] = tx;
                if (txInspector != null) {
                    txInspector.accept(tx);
                }
            });

            // Execute and wait for confirmation
            TxResult result = txContext.completeAndWait(getConfirmationTimeout(), getCheckInterval(),
                    msg -> log.debug("[{}] {}", stepId, msg));

            if (result.isSuccessful()) {
                String txHash = result.getValue();
                listener.onTransactionSubmitted(step, txHash);

                // Capture outputs and spent inputs
                List<Utxo> outputUtxos = captureOutputUtxos(builtTx[0], txHash);
                List<TransactionInput> spentInputs = captureSpentInputs(builtTx[0]);

                // If confirmation tracking is configured, wait for deeper confirmation
                // This enables rollback detection in SEQUENTIAL mode
                if (confirmationTracker != null) {
                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step, cancelCheck);
                    if (confirmResult.isEmpty()) {
                        // waitForConfirmation handles RollbackException for REBUILD strategies
                        // For FAIL_IMMEDIATELY/NOTIFY_ONLY, it returns empty
                        FlowStepResult failedResult = FlowStepResult.failure(stepId,
                                new ConfirmationTimeoutException(txHash));
                        context.recordStepResult(stepId, failedResult);
                        return failedResult;
                    }
                }

                listener.onTransactionConfirmed(step, txHash);

                FlowStepResult stepResult = FlowStepResult.success(stepId, txHash, outputUtxos, spentInputs);
                context.recordStepResult(stepId, stepResult);
                return stepResult;
            } else {
                FlowStepResult stepResult = FlowStepResult.failure(stepId,
                        new RuntimeException("Transaction failed: " + result.getResponse()));
                context.recordStepResult(stepId, stepResult);
                return stepResult;
            }

        } catch (RollbackException e) {
            throw e;  // Let RollbackException propagate for rollback handling
        } catch (Exception e) {
            log.error("Step '{}' failed", stepId, e);
            FlowStepResult stepResult = FlowStepResult.failure(stepId, e);
            context.recordStepResult(stepId, stepResult);
            return stepResult;
        }
    }

    /**
     * Execute a single step in PIPELINED mode (submits without waiting for confirmation).
     * <p>
     * Outputs are captured from the built transaction before submission, enabling
     * subsequent steps to use them as inputs even before on-chain confirmation.
     */
    private FlowStepResult executeStepPipelined(FlowStep step, FlowExecutionContext context,
                                                 java.util.Map<String, Object> variables) {
        String stepId = step.getId();
        log.debug("Executing step '{}' (pipelined)", stepId);

        try {
            // Create appropriate UtxoSupplier based on dependencies
            UtxoSupplier utxoSupplier = createUtxoSupplier(step, context);

            // Create QuickTxBuilder with the appropriate UTXO supplier
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

            // Get TxContext from step (either from TxPlan or TxContext factory)
            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                TxPlan plan = step.getTxPlan();
                // Merge flow variables into plan
                for (var entry : variables.entrySet()) {
                    if (!plan.getVariables().containsKey(entry.getKey())) {
                        plan.addVariable(entry.getKey(), entry.getValue());
                    }
                }
                txContext = quickTxBuilder.compose(plan, signerRegistry);
            } else if (step.hasTxContextFactory()) {
                // Call user's factory with our builder (which has chain-aware UTXO supplier)
                txContext = step.getTxContextFactory().apply(quickTxBuilder);
            } else {
                throw new FlowExecutionException("Step '" + stepId + "' has neither TxPlan nor TxContext factory");
            }

            // Store the built transaction for capturing outputs/inputs
            Transaction[] builtTx = new Transaction[1];
            txContext.withTxInspector(tx -> {
                builtTx[0] = tx;
                if (txInspector != null) {
                    txInspector.accept(tx);
                }
            });

            // Submit transaction without waiting for confirmation (PIPELINED mode)
            TxResult result = txContext.complete();

            if (result.isSuccessful()) {
                String txHash = result.getValue();

                // Capture outputs and spent inputs BEFORE confirmation
                // This enables subsequent steps to use these outputs as inputs
                List<Utxo> outputUtxos = captureOutputUtxos(builtTx[0], txHash);
                List<TransactionInput> spentInputs = captureSpentInputs(builtTx[0]);

                log.debug("Step '{}' submitted (pipelined): {} with {} outputs",
                        stepId, txHash, outputUtxos.size());

                FlowStepResult stepResult = FlowStepResult.success(stepId, txHash, outputUtxos, spentInputs);
                context.recordStepResult(stepId, stepResult);
                return stepResult;
            } else {
                FlowStepResult stepResult = FlowStepResult.failure(stepId,
                        new RuntimeException("Transaction submission failed: " + result.getResponse()));
                context.recordStepResult(stepId, stepResult);
                return stepResult;
            }

        } catch (RollbackException e) {
            throw e;  // Let RollbackException propagate for rollback handling
        } catch (Exception e) {
            log.error("Step '{}' failed (pipelined)", stepId, e);
            FlowStepResult stepResult = FlowStepResult.failure(stepId, e);
            context.recordStepResult(stepId, stepResult);
            return stepResult;
        }
    }

    /**
     * Create appropriate UTXO supplier based on step dependencies.
     */
    private UtxoSupplier createUtxoSupplier(FlowStep step, FlowExecutionContext context) {
        if (!step.hasDependencies() && context.getCompletedStepCount() == 0) {
            // First step with no deps — no filtering needed
            return baseUtxoSupplier;
        }

        // Always use FlowUtxoSupplier to filter out spent UTXOs from previous steps,
        // even when no explicit dependencies are declared.
        // Prevents double-spend when steps use the same address in PIPELINED/BATCH.
        return new FlowUtxoSupplier(baseUtxoSupplier, context, step.getDependencies());
    }

    /**
     * Capture output UTXOs from a built transaction.
     */
    private List<Utxo> captureOutputUtxos(Transaction transaction, String txHash) {
        List<Utxo> outputUtxos = new ArrayList<>();

        if (transaction == null || transaction.getBody() == null) {
            log.warn("No built transaction available for capturing outputs");
            return outputUtxos;
        }

        try {
            List<TransactionOutput> outputs = transaction.getBody().getOutputs();
            if (outputs == null || outputs.isEmpty()) {
                return outputUtxos;
            }

            for (int i = 0; i < outputs.size(); i++) {
                TransactionOutput output = outputs.get(i);

                Utxo utxo = new Utxo();
                utxo.setTxHash(txHash);
                utxo.setOutputIndex(i);
                utxo.setAddress(output.getAddress());

                // Convert Value to List<Amount>
                if (output.getValue() != null) {
                    List<Amount> amounts = new ArrayList<>();

                    // Add lovelace amount
                    if (output.getValue().getCoin() != null) {
                        amounts.add(Amount.lovelace(output.getValue().getCoin()));
                    }

                    // Add multi-asset amounts
                    List<MultiAsset> multiAssets = output.getValue().getMultiAssets();
                    if (multiAssets != null) {
                        for (MultiAsset multiAsset : multiAssets) {
                            String policyId = multiAsset.getPolicyId();
                            for (Asset asset : multiAsset.getAssets()) {
                                String assetName = asset.getNameAsHex();
                                if (assetName.startsWith("0x") || assetName.startsWith("0X")) {
                                    assetName = assetName.substring(2);
                                }
                                String unit = policyId + assetName;
                                amounts.add(new Amount(unit, asset.getValue()));
                            }
                        }
                    }

                    utxo.setAmount(amounts);
                }

                // Handle datum hash if present (convert byte[] to hex string)
                if (output.getDatumHash() != null) {
                    utxo.setDataHash(HexUtil.encodeHexString(output.getDatumHash()));
                }

                // Handle inline datum (convert PlutusData to hex string)
                if (output.getInlineDatum() != null) {
                    try {
                        utxo.setInlineDatum(output.getInlineDatum().serializeToHex());
                    } catch (Exception e) {
                        log.warn("Failed to serialize inline datum", e);
                    }
                }

                // Handle reference script (convert byte[] to hex string)
                if (output.getScriptRef() != null) {
                    utxo.setReferenceScriptHash(HexUtil.encodeHexString(output.getScriptRef()));
                }

                outputUtxos.add(utxo);
            }
        } catch (Exception e) {
            log.error("Failed to capture output UTXOs", e);
        }

        return outputUtxos;
    }

    /**
     * Capture spent inputs from a built transaction.
     */
    private List<TransactionInput> captureSpentInputs(Transaction transaction) {
        if (transaction == null || transaction.getBody() == null) {
            return new ArrayList<>();
        }

        List<TransactionInput> inputs = transaction.getBody().getInputs();
        return inputs != null ? new ArrayList<>(inputs) : new ArrayList<>();
    }

    /**
     * Thin wrapper: synchronous BATCH execution.
     */
    private FlowResult executeBatch(TxFlow flow) {
        return doExecuteBatch(flow, syncHooks(flow));
    }

    /**
     * Thin wrapper: async BATCH execution with FlowHandle.
     */
    private FlowResult executeWithHandleBatch(TxFlow flow, FlowHandle handle) {
        return doExecuteBatch(flow, handleHooks(flow, handle));
    }

    /**
     * Execute flow in BATCH mode: build all transactions first, then submit all at once.
     * <p>
     * This mode provides the highest likelihood of all transactions landing in the same
     * block, as all submissions happen within milliseconds of each other.
     * <p>
     * Phase 1: Build and sign ALL transactions (computing hashes client-side)
     * Phase 2: Submit ALL transactions in rapid succession
     * Phase 3: Wait for all confirmations
     * <p>
     * Supports rollback retry loop: on rollback, the entire flow is rebuilt and resubmitted.
     */
    private FlowResult doExecuteBatch(TxFlow flow, ExecutionHooks hooks) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>();
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
                hooks.onFlowStarting(flow);
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();

            // Determine which steps to skip (still confirmed from previous attempt)
            Set<Integer> skippedStepIndices = new HashSet<>();
            if (!previousConfirmedSteps.isEmpty()) {
                skippedStepIndices = verifyAndPrepareSkippedSteps(
                        previousConfirmedSteps, steps, context, resultBuilder);
            }

            List<Transaction> builtTransactions = new ArrayList<>();
            List<String> precomputedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();

            try {
                // ============ PHASE 1: BUILD ALL TRANSACTIONS ============
                log.info("BATCH mode: Building {} transactions (skipping {} confirmed)",
                        totalSteps, skippedStepIndices.size());
                for (int i = 0; i < totalSteps; i++) {
                    // Check for cancellation
                    if (hooks.isCancelled()) {
                        FlowResult cancelledResult = resultBuilder.withStatus(FlowStatus.CANCELLED)
                                .completedAt(Instant.now()).build();
                        listener.onFlowFailed(flow, cancelledResult);
                        return cancelledResult;
                    }

                    FlowStep step = steps.get(i);
                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    if (skippedStepIndices.contains(i)) {
                        // Reuse previous result — skip build for still-confirmed step
                        FlowStepResult prevResult = previousConfirmedSteps.get(i);
                        builtTransactions.add(null); // placeholder to maintain index alignment
                        precomputedTxHashes.add(prevResult.getTransactionHash());
                        stepResults.add(prevResult);
                        resultBuilder.addStepResult(prevResult);
                        flowTxHashes.add(prevResult.getTransactionHash());
                        log.info("Step '{}' skipped (still confirmed): {}", step.getId(), prevResult.getTransactionHash());
                        continue;
                    }

                    BuildResult buildResult = buildStepOnly(step, context, flow.getVariables());

                    if (buildResult.isSuccessful()) {
                        Transaction tx = buildResult.getTransaction();
                        String txHash = TransactionUtil.getTxHash(tx);

                        builtTransactions.add(tx);
                        precomputedTxHashes.add(txHash);

                        List<Utxo> outputUtxos = captureOutputUtxos(tx, txHash);
                        List<TransactionInput> spentInputs = captureSpentInputs(tx);

                        FlowStepResult stepResult = FlowStepResult.success(step.getId(), txHash, outputUtxos, spentInputs);
                        stepResults.add(stepResult);
                        resultBuilder.addStepResult(stepResult);
                        context.recordStepResult(step.getId(), stepResult);

                        log.debug("Step '{}' built: {} with {} outputs", step.getId(), txHash, outputUtxos.size());
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(), buildResult.getError());
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailed);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailed;
                    }
                }

                // ============ PHASE 2: SUBMIT ALL TRANSACTIONS ============
                log.info("BATCH mode: Submitting {} transactions (skipping {} confirmed)",
                        builtTransactions.size(), skippedStepIndices.size());
                for (int i = 0; i < builtTransactions.size(); i++) {
                    if (skippedStepIndices.contains(i)) {
                        // Already confirmed on-chain — skip submission
                        FlowStep step = steps.get(i);
                        log.debug("Step '{}' submit skipped (still confirmed): {}", step.getId(), precomputedTxHashes.get(i));
                        continue;
                    }

                    Transaction tx = builtTransactions.get(i);
                    FlowStep step = steps.get(i);
                    String expectedHash = precomputedTxHashes.get(i);

                    TxResult result = submitTransaction(tx);

                    if (result.isSuccessful()) {
                        String actualHash = result.getValue();
                        if (!actualHash.equals(expectedHash)) {
                            throw new FlowExecutionException(
                                    "BATCH mode hash mismatch for step '" + step.getId() +
                                    "': expected " + expectedHash + ", actual " + actualHash +
                                    ". Downstream transactions would reference invalid UTXO inputs.");
                        }
                        flowTxHashes.add(actualHash);
                        listener.onTransactionSubmitted(step, actualHash);
                        hooks.onTransactionSubmitted(flow, step, actualHash);
                        log.debug("Step '{}' submitted: {}", step.getId(), actualHash);
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new RuntimeException("Transaction submission failed: " + result.getResponse()));
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailed);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailed;
                    }
                }

                // ============ PHASE 3: WAIT FOR CONFIRMATIONS ============
                log.info("BATCH mode: Waiting for {} confirmations (skipping {} confirmed)",
                        precomputedTxHashes.size(), skippedStepIndices.size());
                for (int i = 0; i < precomputedTxHashes.size(); i++) {
                    String txHash = precomputedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        // Already confirmed — just fire callbacks
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        continue;
                    }

                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step, cancelCheck);
                    if (confirmResult.isPresent()) {
                        ConfirmationResult cr = confirmResult.get();
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        hooks.onTransactionConfirmed(flow, step, txHash, cr);
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new ConfirmationTimeoutException(txHash));
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailed);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailed;
                    }
                }

                // Cleanup tracked transactions to prevent memory leak
                if (confirmationTracker != null) {
                    for (String hash : flowTxHashes) {
                        confirmationTracker.stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    log.error("Flow restart limit ({}) reached after rollback at step '{}' in BATCH mode",
                            maxRollbackRetries, e.getStep().getId());
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in BATCH mode", e));
                    listener.onFlowFailed(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }

                log.info("Restarting BATCH flow (attempt {}/{}) due to rollback at step '{}'",
                        flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                        "Rollback detected at step '" + e.getStep().getId() + "' in BATCH mode");

                // Find steps that are still confirmed before clearing tracker
                previousConfirmedSteps = findStillConfirmedSteps(steps, precomputedTxHashes, stepResults);

                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck);
                hooks.onFlowRestarting();
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.completedAt(Instant.now());
                FlowResult failedResult = resultBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("BATCH flow execution failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Build a step's transaction without submitting.
     * Returns the signed Transaction ready for later submission.
     */
    private BuildResult buildStepOnly(FlowStep step, FlowExecutionContext context,
                                       java.util.Map<String, Object> variables) {
        String stepId = step.getId();
        log.debug("Building step '{}' (batch mode)", stepId);

        try {
            UtxoSupplier utxoSupplier = createUtxoSupplier(step, context);
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                TxPlan plan = step.getTxPlan();
                for (var entry : variables.entrySet()) {
                    if (!plan.getVariables().containsKey(entry.getKey())) {
                        plan.addVariable(entry.getKey(), entry.getValue());
                    }
                }
                txContext = quickTxBuilder.compose(plan, signerRegistry);
            } else if (step.hasTxContextFactory()) {
                txContext = step.getTxContextFactory().apply(quickTxBuilder);
            } else {
                throw new FlowExecutionException("Step '" + stepId + "' has neither TxPlan nor TxContext factory");
            }

            // Apply tx inspector if set
            if (txInspector != null) {
                txContext.withTxInspector(txInspector);
            }

            // Build and sign WITHOUT submitting
            Transaction transaction = txContext.buildAndSign();
            return BuildResult.success(transaction);

        } catch (Exception e) {
            log.error("Step '{}' build failed", stepId, e);
            return BuildResult.failure(e);
        }
    }

    /**
     * Submit a pre-built transaction to the network.
     *
     * @param transaction the signed transaction to submit
     * @return the submission result
     */
    private TxResult submitTransaction(Transaction transaction) {
        try {
            byte[] serializedTx = transaction.serialize();
            var result = transactionProcessor.submitTransaction(serializedTx);
            return TxResult.fromResult(result);
        } catch (Exception e) {
            throw new FlowExecutionException("Transaction submission failed", e);
        }
    }

    /**
     * Result of building a transaction.
     */
    @Getter
    @AllArgsConstructor
    private static class BuildResult {
        private final boolean successful;
        private final Transaction transaction;
        private final Throwable error;

        public static BuildResult success(Transaction tx) {
            return new BuildResult(true, tx, null);
        }

        public static BuildResult failure(Throwable error) {
            return new BuildResult(false, null, error);
        }
    }

    // ==================== State Persistence Helpers ====================

    /**
     * Save initial flow state when execution begins.
     */
    private void persistFlowStarted(TxFlow flow) {
        if (flowStateStore == null) return;

        try {
            FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                    .flowId(flow.getId())
                    .status(FlowStatus.IN_PROGRESS)
                    .startedAt(Instant.now())
                    .description(flow.getDescription())
                    .totalSteps(flow.getSteps().size())
                    .completedSteps(0)
                    .variables(new HashMap<>(flow.getVariables()))
                    .build();

            // Add step placeholders
            for (FlowStep step : flow.getSteps()) {
                snapshot.addStep(StepStateSnapshot.pending(step.getId()));
            }

            flowStateStore.saveFlowState(snapshot);
            log.debug("Persisted flow started: {}", flow.getId());
        } catch (Exception e) {
            log.warn("Failed to persist flow started state: {}", e.getMessage());
        }
    }

    /**
     * Persist transaction submitted state.
     */
    private void persistTransactionSubmitted(TxFlow flow, FlowStep step, String txHash) {
        if (flowStateStore == null) return;

        try {
            TransactionStateDetails details = TransactionStateDetails.submitted(Instant.now());
            flowStateStore.updateTransactionState(flow.getId(), step.getId(), txHash, details);
            log.debug("Persisted transaction submitted: {} -> {}", step.getId(), txHash);
        } catch (Exception e) {
            log.warn("Failed to persist transaction submitted state: {}", e.getMessage());
        }
    }

    /**
     * Persist transaction confirmed state.
     *
     * @param flow the flow
     * @param step the step
     * @param txHash the transaction hash
     * @param blockHeight block height where the transaction was confirmed (may be null)
     * @param confirmationDepth confirmation depth when confirmed (may be null)
     */
    private void persistTransactionConfirmed(TxFlow flow, FlowStep step, String txHash,
                                             Long blockHeight, Integer confirmationDepth) {
        if (flowStateStore == null) return;

        try {
            TransactionStateDetails details = TransactionStateDetails.builder()
                    .state(TransactionState.CONFIRMED)
                    .blockHeight(blockHeight)
                    .confirmationDepth(confirmationDepth)
                    .timestamp(Instant.now())
                    .build();
            flowStateStore.updateTransactionState(flow.getId(), step.getId(), txHash, details);
            log.debug("Persisted transaction confirmed: {} -> {} (block: {}, depth: {})",
                    step.getId(), txHash, blockHeight, confirmationDepth);
        } catch (Exception e) {
            log.warn("Failed to persist transaction confirmed state: {}", e.getMessage());
        }
    }

    /**
     * Persist transaction rolled back state.
     *
     * @param flow the flow
     * @param step the step
     * @param txHash the transaction hash
     * @param previousBlockHeight block height before rollback (may be null)
     * @param errorMessage description of the rollback cause
     */
    private void persistTransactionRolledBack(TxFlow flow, FlowStep step, String txHash,
                                              Long previousBlockHeight, String errorMessage) {
        if (flowStateStore == null) return;

        try {
            TransactionStateDetails details = TransactionStateDetails.rolledBack(
                    previousBlockHeight, errorMessage, Instant.now());
            flowStateStore.updateTransactionState(flow.getId(), step.getId(), txHash, details);
            log.debug("Persisted transaction rolled back: {} -> {} (previous block: {})",
                    step.getId(), txHash, previousBlockHeight);
        } catch (Exception e) {
            log.warn("Failed to persist transaction rolled back state: {}", e.getMessage());
        }
    }

    /**
     * Persist flow completion state.
     */
    private void persistFlowComplete(TxFlow flow, FlowStatus status) {
        if (flowStateStore == null) return;

        try {
            flowStateStore.markFlowComplete(flow.getId(), status);
            log.debug("Persisted flow complete: {} -> {}", flow.getId(), status);
        } catch (Exception e) {
            log.warn("Failed to persist flow complete state: {}", e.getMessage());
        }
    }

    // ==================== Resume/Retry Helpers ====================

    /**
     * Validate arguments for resume methods.
     */
    private void validateResumeArgs(TxFlow flow, FlowResult previousResult) {
        if (previousResult == null) {
            throw new IllegalArgumentException("previousResult cannot be null");
        }
        if (!flow.getId().equals(previousResult.getFlowId())) {
            throw new IllegalArgumentException("Flow ID mismatch: flow='" + flow.getId()
                    + "', previousResult='" + previousResult.getFlowId() + "'");
        }
        if (previousResult.isSuccessful()) {
            throw new IllegalArgumentException("Previous execution was successful, nothing to resume");
        }
    }

    /**
     * Verify which steps from a previous execution are still confirmed on-chain.
     * <p>
     * Returns a contiguous prefix of verified steps. On the first unverified or
     * failed step, verification stops — no gaps are allowed to prevent dependency issues.
     *
     * @param flow the flow definition
     * @param previousResult the previous execution result
     * @return map of step index to verified step result (contiguous prefix only)
     */
    private Map<Integer, FlowStepResult> verifyPreviousSteps(TxFlow flow, FlowResult previousResult) {
        Map<Integer, FlowStepResult> confirmedSteps = new HashMap<>();
        List<FlowStep> steps = flow.getSteps();

        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            Optional<FlowStepResult> prevStepResult = previousResult.getStepResult(step.getId());

            if (prevStepResult.isPresent() && prevStepResult.get().isSuccessful()) {
                String txHash = prevStepResult.get().getTransactionHash();
                try {
                    Optional<TransactionInfo> txInfo = chainDataSupplier.getTransactionInfo(txHash);
                    if (txInfo.isPresent() && txInfo.get().getBlockHeight() != null) {
                        confirmedSteps.put(i, prevStepResult.get());
                        log.info("Resume: step '{}' tx {} confirmed at block {}, will skip",
                                step.getId(), txHash, txInfo.get().getBlockHeight());
                    } else {
                        log.info("Resume: step '{}' tx {} no longer on-chain, will re-execute",
                                step.getId(), txHash);
                        break;
                    }
                } catch (Exception e) {
                    log.info("Resume: could not verify step '{}' tx {}, will re-execute",
                            step.getId(), txHash);
                    break;
                }
            } else {
                break; // Stop at first non-successful step
            }
        }
        return confirmedSteps;
    }

    /**
     * Execute SEQUENTIAL mode with resume support — skips verified steps from a previous run.
     */
    private FlowResult doExecuteSequentialWithResume(TxFlow flow, ExecutionHooks hooks,
                                                      Map<Integer, FlowStepResult> confirmedSteps) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<String, Integer> stepRollbackAttempts = new ConcurrentHashMap<>();
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            // Determine which steps to skip
            Set<Integer> skippedStepIndices = new HashSet<>();
            if (!confirmedSteps.isEmpty()) {
                skippedStepIndices = verifyAndPrepareSkippedSteps(
                        confirmedSteps, flow.getSteps(), context, resultBuilder);
            }

            if (flowRestartAttempts == 0) {
                log.info("Resuming flow '{}': skipping {} confirmed steps, executing from step {}",
                        flow.getId(), skippedStepIndices.size(),
                        skippedStepIndices.size() < flow.getSteps().size()
                                ? flow.getSteps().get(skippedStepIndices.size()).getId() : "none");
            }

            flowTxHashes.clear();
            // Add tx hashes from skipped steps
            for (int idx : skippedStepIndices) {
                flowTxHashes.add(confirmedSteps.get(idx).getTransactionHash());
            }
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();

            try {
                for (int i = 0; i < totalSteps; i++) {
                    if (hooks.isCancelled()) {
                        FlowResult cancelledResult = resultBuilder.withStatus(FlowStatus.CANCELLED)
                                .completedAt(Instant.now()).build();
                        listener.onFlowFailed(flow, cancelledResult);
                        return cancelledResult;
                    }

                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        FlowStepResult prevResult = confirmedSteps.get(i);
                        resultBuilder.addStepResult(prevResult);
                        hooks.onStepCompleted(step, prevResult);
                        listener.onStepCompleted(step, prevResult);
                        log.info("Step '{}' skipped (still confirmed): {}", step.getId(), prevResult.getTransactionHash());
                        continue;
                    }

                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRollbackHandling(
                            step, context, flow.getVariables(), false,
                            stepRollbackAttempts, maxRollbackRetries, steps, cancelCheck);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        String txHash = stepResult.getTransactionHash();
                        flowTxHashes.add(txHash);
                        hooks.onTransactionSubmitted(flow, step, txHash);
                        hooks.onStepCompleted(step, stepResult);
                        listener.onStepCompleted(step, stepResult);

                        Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step, cancelCheck);
                        hooks.onTransactionConfirmed(flow, step, txHash, confirmResult.orElse(null));
                    } else {
                        listener.onStepFailed(step, stepResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                if (confirmationTracker != null) {
                    for (String hash : flowTxHashes) {
                        confirmationTracker.stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                if (e.isRequiresFlowRestart()) {
                    flowRestartAttempts++;
                    if (flowRestartAttempts > maxRollbackRetries) {
                        log.error("Flow restart limit ({}) reached after rollback at step '{}'",
                                maxRollbackRetries, e.getStep().getId());
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.failure(
                                new FlowExecutionException("Flow restart limit reached after rollback", e));
                        listener.onFlowFailed(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }

                    log.info("Restarting resumed flow (attempt {}/{}) due to rollback at step '{}'",
                            flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                    listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                            "Rollback detected at step '" + e.getStep().getId() + "'");
                    waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck);
                    // On restart, clear confirmed steps — verifyAndPrepareSkippedSteps will re-check
                    confirmedSteps = new HashMap<>(confirmedSteps);
                    stepRollbackAttempts.clear();
                    hooks.onFlowRestarting();
                    continue;
                } else {
                    log.error("Step rebuild failed for step '{}'", e.getStep().getId());
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(e);
                    listener.onFlowFailed(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }
            } catch (Exception e) {
                log.error("Flow execution failed", e);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("Resumed flow execution failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Execute PIPELINED mode with resume support — seeds confirmed steps from a previous run.
     */
    private FlowResult doExecutePipelinedWithResume(TxFlow flow, ExecutionHooks hooks,
                                                     Map<Integer, FlowStepResult> confirmedSteps) {
        // The existing doExecutePipelined already supports previousConfirmedSteps via its
        // rollback retry loop. We just need to seed it by injecting our verified steps
        // into the first iteration. We achieve this by temporarily modifying the internal
        // flow — but since doExecutePipelined manages previousConfirmedSteps as a local var,
        // we create a thin wrapper that delegates.
        //
        // Actually, doExecutePipelined initializes previousConfirmedSteps as empty HashMap
        // and only populates it on rollback. To seed it for resume, we need a variant.
        // The simplest approach: duplicate the initial setup and call into the existing method
        // with pre-seeded state. But to avoid duplication, we use a different approach:
        // we call the existing doExecutePipelined logic but with confirmedSteps pre-seeded.
        //
        // Since doExecutePipelined has its own retry loop with previousConfirmedSteps,
        // the cleanest way is to refactor slightly. For now, we use the same pattern
        // as doExecuteSequentialWithResume — it's a full method but shares all helpers.

        // For PIPELINED resume, we reuse the exact same logic as doExecutePipelined
        // but start with previousConfirmedSteps pre-seeded.
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>(confirmedSteps);
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                log.info("Resuming flow '{}' in PIPELINED mode: {} confirmed steps to skip",
                        flow.getId(), previousConfirmedSteps.size());
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();
            List<String> submittedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();

            Set<Integer> skippedStepIndices = new HashSet<>();
            if (!previousConfirmedSteps.isEmpty()) {
                skippedStepIndices = verifyAndPrepareSkippedSteps(
                        previousConfirmedSteps, steps, context, resultBuilder);
            }

            try {
                log.info("PIPELINED resume: Submitting {} transactions (skipping {} confirmed)",
                        totalSteps, skippedStepIndices.size());
                for (int i = 0; i < totalSteps; i++) {
                    if (hooks.isCancelled()) {
                        FlowResult cancelledResult = resultBuilder.withStatus(FlowStatus.CANCELLED)
                                .completedAt(Instant.now()).build();
                        listener.onFlowFailed(flow, cancelledResult);
                        return cancelledResult;
                    }

                    FlowStep step = steps.get(i);
                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    if (skippedStepIndices.contains(i)) {
                        FlowStepResult prevResult = previousConfirmedSteps.get(i);
                        stepResults.add(prevResult);
                        submittedTxHashes.add(prevResult.getTransactionHash());
                        flowTxHashes.add(prevResult.getTransactionHash());
                        resultBuilder.addStepResult(prevResult);
                        log.info("Step '{}' skipped (still confirmed): {}", step.getId(), prevResult.getTransactionHash());
                        continue;
                    }

                    FlowStepResult stepResult = executeStepWithRetry(step, context, flow.getVariables(), true, cancelCheck);
                    stepResults.add(stepResult);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        submittedTxHashes.add(stepResult.getTransactionHash());
                        flowTxHashes.add(stepResult.getTransactionHash());
                        listener.onTransactionSubmitted(step, stepResult.getTransactionHash());
                        hooks.onTransactionSubmitted(flow, step, stepResult.getTransactionHash());
                    } else {
                        listener.onStepFailed(step, stepResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                log.info("PIPELINED resume: Waiting for {} transactions to confirm", submittedTxHashes.size());
                for (int i = 0; i < submittedTxHashes.size(); i++) {
                    String txHash = submittedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        continue;
                    }

                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step, cancelCheck);
                    if (confirmResult.isPresent()) {
                        ConfirmationResult result = confirmResult.get();
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        hooks.onTransactionConfirmed(flow, step, txHash, result);
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new ConfirmationTimeoutException(txHash));
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailedResult;
                    }
                }

                if (confirmationTracker != null) {
                    for (String hash : flowTxHashes) {
                        confirmationTracker.stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in PIPELINED resume", e));
                    listener.onFlowFailed(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }

                previousConfirmedSteps = findStillConfirmedSteps(steps, submittedTxHashes, stepResults);
                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck);
                hooks.onFlowRestarting();
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("PIPELINED resume failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Execute BATCH mode with resume support — seeds confirmed steps from a previous run.
     */
    private FlowResult doExecuteBatchWithResume(TxFlow flow, ExecutionHooks hooks,
                                                 Map<Integer, FlowStepResult> confirmedSteps) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>(confirmedSteps);
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                log.info("Resuming flow '{}' in BATCH mode: {} confirmed steps to skip",
                        flow.getId(), previousConfirmedSteps.size());
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();

            Set<Integer> skippedStepIndices = new HashSet<>();
            if (!previousConfirmedSteps.isEmpty()) {
                skippedStepIndices = verifyAndPrepareSkippedSteps(
                        previousConfirmedSteps, steps, context, resultBuilder);
            }

            List<Transaction> builtTransactions = new ArrayList<>();
            List<String> precomputedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();

            try {
                // PHASE 1: BUILD
                log.info("BATCH resume: Building {} transactions (skipping {} confirmed)",
                        totalSteps, skippedStepIndices.size());
                for (int i = 0; i < totalSteps; i++) {
                    if (hooks.isCancelled()) {
                        FlowResult cancelledResult = resultBuilder.withStatus(FlowStatus.CANCELLED)
                                .completedAt(Instant.now()).build();
                        listener.onFlowFailed(flow, cancelledResult);
                        return cancelledResult;
                    }

                    FlowStep step = steps.get(i);
                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    if (skippedStepIndices.contains(i)) {
                        FlowStepResult prevResult = previousConfirmedSteps.get(i);
                        builtTransactions.add(null);
                        precomputedTxHashes.add(prevResult.getTransactionHash());
                        stepResults.add(prevResult);
                        resultBuilder.addStepResult(prevResult);
                        flowTxHashes.add(prevResult.getTransactionHash());
                        log.info("Step '{}' skipped (still confirmed): {}", step.getId(), prevResult.getTransactionHash());
                        continue;
                    }

                    BuildResult buildResult = buildStepOnly(step, context, flow.getVariables());

                    if (buildResult.isSuccessful()) {
                        Transaction tx = buildResult.getTransaction();
                        String txHash = TransactionUtil.getTxHash(tx);

                        builtTransactions.add(tx);
                        precomputedTxHashes.add(txHash);

                        List<Utxo> outputUtxos = captureOutputUtxos(tx, txHash);
                        List<TransactionInput> spentInputs = captureSpentInputs(tx);

                        FlowStepResult stepResult = FlowStepResult.success(step.getId(), txHash, outputUtxos, spentInputs);
                        stepResults.add(stepResult);
                        resultBuilder.addStepResult(stepResult);
                        context.recordStepResult(step.getId(), stepResult);
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(), buildResult.getError());
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailed);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailed;
                    }
                }

                // PHASE 2: SUBMIT
                log.info("BATCH resume: Submitting {} transactions (skipping {} confirmed)",
                        builtTransactions.size(), skippedStepIndices.size());
                for (int i = 0; i < builtTransactions.size(); i++) {
                    if (skippedStepIndices.contains(i)) {
                        continue;
                    }

                    Transaction tx = builtTransactions.get(i);
                    FlowStep step = steps.get(i);
                    String expectedHash = precomputedTxHashes.get(i);

                    TxResult result = submitTransaction(tx);

                    if (result.isSuccessful()) {
                        String actualHash = result.getValue();
                        if (!actualHash.equals(expectedHash)) {
                            throw new FlowExecutionException(
                                    "BATCH mode hash mismatch for step '" + step.getId() +
                                    "': expected " + expectedHash + ", actual " + actualHash);
                        }
                        flowTxHashes.add(actualHash);
                        listener.onTransactionSubmitted(step, actualHash);
                        hooks.onTransactionSubmitted(flow, step, actualHash);
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new RuntimeException("Transaction submission failed: " + result.getResponse()));
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailed);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailed;
                    }
                }

                // PHASE 3: CONFIRM
                log.info("BATCH resume: Waiting for {} confirmations (skipping {} confirmed)",
                        precomputedTxHashes.size(), skippedStepIndices.size());
                for (int i = 0; i < precomputedTxHashes.size(); i++) {
                    String txHash = precomputedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        continue;
                    }

                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step, cancelCheck);
                    if (confirmResult.isPresent()) {
                        ConfirmationResult cr = confirmResult.get();
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        hooks.onTransactionConfirmed(flow, step, txHash, cr);
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new ConfirmationTimeoutException(txHash));
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailed);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return flowFailed;
                    }
                }

                if (confirmationTracker != null) {
                    for (String hash : flowTxHashes) {
                        confirmationTracker.stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in BATCH resume", e));
                    listener.onFlowFailed(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }

                previousConfirmedSteps = findStillConfirmedSteps(steps, precomputedTxHashes, stepResults);
                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck);
                hooks.onFlowRestarting();
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.completedAt(Instant.now());
                FlowResult failedResult = resultBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("BATCH resume failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Close this executor and release associated resources.
     * <p>
     * Cancels all running flows, clears active flow tracking and confirmation tracker state.
     */
    @Override
    public void close() {
        for (FlowHandle handle : activeHandles) {
            handle.cancel();
        }
        activeHandles.clear();
        activeFlowIds.clear();
        if (confirmationTracker != null) {
            confirmationTracker.clearTracking();
        }
    }
}
