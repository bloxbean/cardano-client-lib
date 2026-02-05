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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
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
 * <h3>Example Usage:</h3>
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
public class FlowExecutor {
    private final UtxoSupplier baseUtxoSupplier;
    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final TransactionProcessor transactionProcessor;
    private final ChainDataSupplier chainDataSupplier;
    private SignerRegistry signerRegistry;
    private FlowListener listener = FlowListener.NOOP;
    private Duration confirmationTimeout = Duration.ofSeconds(60);
    private Duration checkInterval = Duration.ofSeconds(2);
    private Executor executor;
    private Consumer<Transaction> txInspector;
    private ChainingMode chainingMode = ChainingMode.SEQUENTIAL;
    private RetryPolicy defaultRetryPolicy;
    private ConfirmationConfig confirmationConfig;
    private ConfirmationTracker confirmationTracker;
    private RollbackStrategy rollbackStrategy = RollbackStrategy.FAIL_IMMEDIATELY;
    private FlowRegistry flowRegistry;
    private FlowStateStore flowStateStore;

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
     *
     * @param listener the listener
     * @return this executor
     */
    public FlowExecutor withListener(FlowListener listener) {
        this.listener = listener != null ? listener : FlowListener.NOOP;
        return this;
    }

    /**
     * Set the confirmation timeout for each step.
     *
     * @param timeout the timeout
     * @return this executor
     */
    public FlowExecutor withConfirmationTimeout(Duration timeout) {
        this.confirmationTimeout = timeout;
        return this;
    }

    /**
     * Set the interval for checking transaction confirmation.
     *
     * @param interval the check interval
     * @return this executor
     */
    public FlowExecutor withCheckInterval(Duration interval) {
        this.checkInterval = interval;
        return this;
    }

    /**
     * Set a custom executor for async operations.
     *
     * @param executor the executor
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
     * <p>
     * This enables recovery of pending flows after application restart
     * using {@link #resumeTracking(FlowStateSnapshot, RecoveryCallback)}.
     *
     * @param stateStore the state store implementation
     * @return this executor
     * @see FlowStateStore
     * @see #resumeTracking(FlowStateSnapshot, RecoveryCallback)
     */
    public FlowExecutor withStateStore(FlowStateStore stateStore) {
        this.flowStateStore = stateStore;
        return this;
    }

    /**
     * Resume tracking of a flow from a persisted state snapshot.
     * <p>
     * This method is used to recover flows after application restart.
     * It takes a snapshot loaded from the state store and resumes
     * confirmation tracking for pending transactions.
     * <p>
     * Example usage:
     * <pre>{@code
     * // On application startup
     * List<FlowStateSnapshot> pending = stateStore.loadPendingFlows();
     * for (FlowStateSnapshot snapshot : pending) {
     *     FlowHandle handle = executor.resumeTracking(snapshot, recoveryCallback);
     *     registry.register(snapshot.getFlowId(), handle);
     * }
     * }</pre>
     *
     * @param snapshot the flow state snapshot to resume
     * @param callback the recovery callback for deciding how to handle pending transactions
     * @return a FlowHandle for monitoring the resumed flow
     * @throws IllegalArgumentException if snapshot is null
     */
    public FlowHandle resumeTracking(FlowStateSnapshot snapshot, RecoveryCallback callback) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }

        RecoveryCallback effectiveCallback = callback != null ? callback : RecoveryCallback.CONTINUE_ALL;

        // Notify callback that recovery is starting
        effectiveCallback.onRecoveryStarting(snapshot);

        // Create a CompletableFuture to track the recovery
        CompletableFuture<FlowResult> future = new CompletableFuture<>();

        // Create a minimal TxFlow for the handle (used for ID and tracking only)
        TxFlow recoveryFlow = TxFlow.builder(snapshot.getFlowId())
                .withDescription("Recovery: " + snapshot.getDescription())
                .build();

        FlowHandle handle = new FlowHandle(recoveryFlow, future);
        handle.updateStatus(FlowStatus.IN_PROGRESS);

        // Start async recovery tracking
        Runnable recoveryTask = () -> {
            try {
                FlowResult result = executeRecoveryTracking(snapshot, effectiveCallback, handle);
                future.complete(result);
            } catch (Exception e) {
                log.error("Recovery tracking failed for flow {}", snapshot.getFlowId(), e);
                effectiveCallback.onRecoveryComplete(snapshot, false, e.getMessage());
                future.completeExceptionally(e);
            }
        };

        if (executor != null) {
            executor.execute(recoveryTask);
        } else {
            ForkJoinPool.commonPool().execute(recoveryTask);
        }

        return handle;
    }

    /**
     * Execute recovery tracking for a flow snapshot.
     */
    private FlowResult executeRecoveryTracking(FlowStateSnapshot snapshot,
                                                RecoveryCallback callback,
                                                FlowHandle handle) {
        String flowId = snapshot.getFlowId();
        FlowResult.Builder resultBuilder = FlowResult.builder(flowId)
                .startedAt(snapshot.getStartedAt() != null ? snapshot.getStartedAt() : Instant.now());

        List<StepStateSnapshot> pendingSteps = snapshot.getPendingSteps();

        if (pendingSteps.isEmpty()) {
            // No pending transactions, mark as complete
            log.info("No pending transactions to track for flow {}", flowId);
            handle.updateStatus(FlowStatus.COMPLETED);
            resultBuilder.completedAt(Instant.now());
            FlowResult result = resultBuilder.success();
            callback.onRecoveryComplete(snapshot, true, null);

            if (flowStateStore != null) {
                flowStateStore.markFlowComplete(flowId, FlowStatus.COMPLETED);
            }
            return result;
        }

        log.info("Resuming tracking for flow {} with {} pending transactions", flowId, pendingSteps.size());

        // Track each pending transaction
        for (StepStateSnapshot stepSnapshot : pendingSteps) {
            RecoveryCallback.RecoveryAction action = callback.onPendingTransaction(snapshot, stepSnapshot);

            switch (action) {
                case CONTINUE_TRACKING:
                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(stepSnapshot.getTransactionHash(), null);
                    if (confirmResult.isPresent()) {
                        ConfirmationResult result = confirmResult.get();
                        log.info("Transaction {} confirmed during recovery at block {}",
                                stepSnapshot.getTransactionHash(), result.getBlockHeight());
                        if (flowStateStore != null) {
                            TransactionStateDetails details = TransactionStateDetails.confirmed(
                                    result.getBlockHeight() != null ? result.getBlockHeight() : 0,
                                    result.getConfirmationDepth(),
                                    Instant.now());
                            flowStateStore.updateTransactionState(flowId, stepSnapshot.getStepId(),
                                    stepSnapshot.getTransactionHash(), details);
                        }
                        handle.incrementCompletedSteps();
                    } else {
                        log.warn("Transaction {} not confirmed during recovery", stepSnapshot.getTransactionHash());
                        handle.updateStatus(FlowStatus.FAILED);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.failure(
                                new ConfirmationTimeoutException(stepSnapshot.getTransactionHash()));
                        callback.onRecoveryComplete(snapshot, false, "Transaction confirmation timeout");

                        if (flowStateStore != null) {
                            flowStateStore.markFlowComplete(flowId, FlowStatus.FAILED);
                        }
                        return failedResult;
                    }
                    break;

                case SKIP:
                    log.info("Skipping transaction {} during recovery", stepSnapshot.getTransactionHash());
                    // Mark step as skipped but continue
                    break;

                case RESUBMIT:
                    log.warn("RESUBMIT action requested but not supported in basic recovery. " +
                            "Application should handle resubmission externally.");
                    // For RESUBMIT, the application needs to rebuild and resubmit the transaction
                    // This is beyond the scope of basic recovery tracking
                    break;

                case FAIL_FLOW:
                    log.info("Failing flow {} as requested by recovery callback", flowId);
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow failed by recovery callback"));
                    callback.onRecoveryComplete(snapshot, false, "Flow failed by recovery callback");

                    if (flowStateStore != null) {
                        flowStateStore.markFlowComplete(flowId, FlowStatus.FAILED);
                    }
                    return failedResult;
            }
        }

        // All pending transactions tracked successfully
        handle.updateStatus(FlowStatus.COMPLETED);
        resultBuilder.completedAt(Instant.now());
        FlowResult result = resultBuilder.success();
        callback.onRecoveryComplete(snapshot, true, null);

        if (flowStateStore != null) {
            flowStateStore.markFlowComplete(flowId, FlowStatus.COMPLETED);
        }

        log.info("Recovery tracking completed for flow {}", flowId);
        return result;
    }

    /**
     * Execute a flow synchronously.
     *
     * @param flow the flow to execute
     * @return the flow result
     */
    public FlowResult executeSync(TxFlow flow) {
        // Validate the flow
        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }

        switch (chainingMode) {
            case PIPELINED:
                return executePipelined(flow);
            case BATCH:
                return executeBatch(flow);
            case SEQUENTIAL:
            default:
                return executeSequential(flow);
        }
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
    private FlowResult executeSequential(TxFlow flow) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<String, Integer> stepRollbackAttempts = new ConcurrentHashMap<>();

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
                persistFlowStarted(flow);
            }

            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();

            try {
                for (int i = 0; i < totalSteps; i++) {
                    FlowStep step = steps.get(i);
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRollbackHandling(
                            step, context, flow.getVariables(), false,
                            stepRollbackAttempts, maxRollbackRetries);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        listener.onStepCompleted(step, stepResult);
                    } else {
                        listener.onStepFailed(step, stepResult);
                        // Stop on first failure
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        persistFlowComplete(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                persistFlowComplete(flow, FlowStatus.COMPLETED);
                return successResult;

            } catch (RollbackException e) {
                if (e.isRequiresFlowRestart()) {
                    flowRestartAttempts++;
                    if (flowRestartAttempts > maxRollbackRetries) {
                        log.error("Flow restart limit ({}) reached after rollback at step '{}'",
                                maxRollbackRetries, e.getStep().getId());
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.failure(
                                new FlowExecutionException("Flow restart limit reached after rollback", e));
                        listener.onFlowFailed(flow, failedResult);
                        return failedResult;
                    }

                    log.info("Restarting flow (attempt {}/{}) due to rollback at step '{}'",
                            flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                    listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                            "Rollback detected at step '" + e.getStep().getId() + "'");
                    waitForBackendReadyAfterRollback(); // Wait for backend to sync after rollback
                    stepRollbackAttempts.clear(); // Reset step-level counters on flow restart
                    continue; // Restart the flow
                } else {
                    // REBUILD_FROM_FAILED is handled within executeStepWithRollbackHandling
                    // If we get here, it means rebuild also failed
                    log.error("Step rebuild failed for step '{}'", e.getStep().getId());
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(e);
                    listener.onFlowFailed(flow, failedResult);
                    return failedResult;
                }
            } catch (Exception e) {
                log.error("Flow execution failed", e);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
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
        return failedResult;
    }

    /**
     * Get the maximum rollback retries from configuration.
     */
    private int getMaxRollbackRetries() {
        return confirmationConfig != null ? confirmationConfig.getMaxRollbackRetries() : 3;
    }

    /**
     * Execute a step with rollback handling.
     * <p>
     * This method wraps executeStepWithRetry and handles RollbackException for
     * REBUILD_FROM_FAILED strategy by rebuilding and resubmitting the step.
     *
     * @throws RollbackException when using REBUILD_ENTIRE_FLOW strategy or when
     *         rebuild attempts are exhausted for REBUILD_FROM_FAILED
     */
    private FlowStepResult executeStepWithRollbackHandling(FlowStep step, FlowExecutionContext context,
                                                            java.util.Map<String, Object> variables,
                                                            boolean pipelined,
                                                            Map<String, Integer> stepRollbackAttempts,
                                                            int maxRollbackRetries) {
        while (true) {
            try {
                return executeStepWithRetry(step, context, variables, pipelined);
            } catch (RollbackException e) {
                if (e.isRequiresFlowRestart()) {
                    // REBUILD_ENTIRE_FLOW: propagate exception to flow level
                    throw e;
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

                waitForBackendReadyAfterRollback(); // Wait for backend to sync after rollback

                // Clear the step result so it can be rebuilt
                context.clearStepResult(step.getId());

                // Continue loop to retry the step
            }
        }
    }

    /**
     * Execute flow in PIPELINED mode - submit all transactions, then wait for confirmations.
     * <p>
     * This enables true transaction chaining where multiple transactions can land in the same block.
     * <p>
     * For rollback handling in PIPELINED mode:
     * <ul>
     *     <li>REBUILD_FROM_FAILED and REBUILD_ENTIRE_FLOW both trigger a full flow restart,
     *         as all transactions are interdependent in pipelined execution</li>
     * </ul>
     */
    private FlowResult executePipelined(TxFlow flow) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
            }

            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();
            List<String> submittedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();

            try {
                // Phase 1: Build and submit all transactions without waiting
                log.info("PIPELINED mode: Submitting {} transactions", totalSteps);
                for (int i = 0; i < totalSteps; i++) {
                    FlowStep step = steps.get(i);
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRetry(step, context, flow.getVariables(), true);
                    stepResults.add(stepResult);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        submittedTxHashes.add(stepResult.getTransactionHash());
                        listener.onTransactionSubmitted(step, stepResult.getTransactionHash());
                        log.debug("Step '{}' submitted: {}", step.getId(), stepResult.getTransactionHash());
                    } else {
                        // If submission fails, stop and report failure
                        listener.onStepFailed(step, stepResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        return failedResult;
                    }
                }

                // Phase 2: Wait for all transactions to be confirmed
                log.info("PIPELINED mode: Waiting for {} transactions to confirm", submittedTxHashes.size());
                for (int i = 0; i < submittedTxHashes.size(); i++) {
                    String txHash = submittedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step);
                    if (confirmResult.isPresent()) {
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        log.debug("Step '{}' confirmed: {} at block {}", step.getId(), txHash,
                                confirmResult.get().getBlockHeight());
                    } else {
                        // Confirmation timeout or rollback (FAIL_IMMEDIATELY/NOTIFY_ONLY)
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new ConfirmationTimeoutException(txHash));
                        listener.onStepFailed(step, failedResult);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailedResult);
                        return flowFailedResult;
                    }
                }

                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                return successResult;

            } catch (RollbackException e) {
                // In PIPELINED mode, any rollback requires restarting the entire flow
                // because all transactions are submitted before any confirmations
                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    log.error("Flow restart limit ({}) reached after rollback at step '{}' in PIPELINED mode",
                            maxRollbackRetries, e.getStep().getId());
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in PIPELINED mode", e));
                    listener.onFlowFailed(flow, failedResult);
                    return failedResult;
                }

                log.info("Restarting PIPELINED flow (attempt {}/{}) due to rollback at step '{}'",
                        flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                        "Rollback detected at step '" + e.getStep().getId() + "' in PIPELINED mode");
                waitForBackendReadyAfterRollback(); // Wait for backend to sync after rollback
                continue; // Restart the flow

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
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
        return failedResult;
    }

    /**
     * Wait for a transaction to be confirmed on-chain.
     *
     * @param txHash the transaction hash to wait for
     * @return Optional containing ConfirmationResult if confirmed, empty if timeout or failure
     */
    private Optional<ConfirmationResult> waitForConfirmation(String txHash) {
        return waitForConfirmation(txHash, null);
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
    private Optional<ConfirmationResult> waitForConfirmation(String txHash, FlowStep step) {
        // Use enhanced confirmation tracking if configured
        if (confirmationTracker != null) {
            return waitForConfirmationWithTracking(txHash, step);
        }

        // Fall back to simple confirmation checking
        long startTime = System.currentTimeMillis();
        long timeoutMs = confirmationTimeout.toMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
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
                Thread.sleep(checkInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception e) {
                log.debug("Waiting for tx confirmation: {}", txHash);
                try {
                    Thread.sleep(checkInterval.toMillis());
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
    private Optional<ConfirmationResult> waitForConfirmationWithTracking(String txHash, FlowStep step) {
        ConfirmationStatus targetStatus = confirmationConfig.isRequireFinalization()
                ? ConfirmationStatus.FINALIZED
                : ConfirmationStatus.CONFIRMED;

        // Track the last known status for detecting transitions
        final ConfirmationStatus[] lastStatus = {null};
        final Long[] firstBlockHeight = {null};

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
                        // Transition: -> FINALIZED
                        if (currentStatus == ConfirmationStatus.FINALIZED) {
                            listener.onTransactionFinalized(step, txHash);
                        }
                        lastStatus[0] = currentStatus;
                    }

                    // Always fire depth changed callback when depth changes
                    listener.onConfirmationDepthChanged(step, txHash, depth, currentStatus);
                });

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
                    // Continue waiting (handled by tracker timeout)
                    log.warn("Transaction {} rolled back, notified listeners (NOTIFY_ONLY strategy)", txHash);
                    break;

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

        // Timeout or other failure
        if (result.getError() != null) {
            log.warn("Confirmation wait failed for tx {}: {}", txHash, result.getError().getMessage());
        }
        return Optional.empty();
    }

    /**
     * Wait for the backend to be ready after a rollback.
     * Retries querying the backend until it responds successfully.
     * This handles both test scenarios (node restart) and production scenarios (network issues).
     *
     * @param maxAttempts maximum number of retry attempts
     * @param retryDelayMs delay between retries in milliseconds
     */
    private void waitForBackendReady(int maxAttempts, long retryDelayMs) {
        log.debug("Waiting for backend to be ready (max {} attempts)...", maxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
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
     * This method always clears the confirmation tracker state to avoid stale tracking data.
     * <p>
     * Additional wait behavior is controlled by the {@link ConfirmationConfig}:
     * <ul>
     *     <li>If {@code waitForBackendAfterRollback} is false (production default):
     *         Only clears tracker, no wait.</li>
     *     <li>If {@code waitForBackendAfterRollback} is true (devnet/test):
     *         Waits for backend ready and optional UTXO sync delay.</li>
     * </ul>
     */
    private void waitForBackendReadyAfterRollback() {
        // Always clear confirmation tracker state to avoid stale tracking data
        if (confirmationTracker != null) {
            log.debug("Clearing confirmation tracker state after rollback");
            confirmationTracker.clearTracking();
        }

        // Skip wait logic if not configured (production default)
        if (confirmationConfig == null || !confirmationConfig.isWaitForBackendAfterRollback()) {
            log.debug("Skipping backend wait (not configured for post-rollback wait)");
            return;
        }

        // Wait for backend ready (for test scenarios like Yaci DevKit)
        long retryDelayMs = confirmationConfig.getCheckInterval().toMillis();
        int maxAttempts = confirmationConfig.getPostRollbackWaitAttempts();
        waitForBackendReady(maxAttempts, retryDelayMs);

        // Optional additional delay for UTXO indexer sync
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
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        // Auto-register with flow registry if configured
        if (flowRegistry != null) {
            flowRegistry.register(flow.getId(), handle);
        }

        Runnable task = () -> {
            try {
                handle.updateStatus(FlowStatus.IN_PROGRESS);
                FlowResult result = executeWithHandle(flow, handle);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        };

        if (executor != null) {
            executor.execute(task);
        } else {
            ForkJoinPool.commonPool().execute(task);
        }

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

    private FlowResult executeWithHandleSequential(TxFlow flow, FlowHandle handle) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;
        Map<String, Integer> stepRollbackAttempts = new ConcurrentHashMap<>();

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
                persistFlowStarted(flow);
            }

            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();

            try {
                for (int i = 0; i < totalSteps; i++) {
                    FlowStep step = steps.get(i);
                    handle.updateCurrentStep(step.getId());
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRollbackHandling(
                            step, context, flow.getVariables(), false,
                            stepRollbackAttempts, maxRollbackRetries);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        handle.incrementCompletedSteps();
                        listener.onStepCompleted(step, stepResult);
                        // Get confirmation details - tx is already confirmed from completeAndWait()
                        Optional<ConfirmationResult> confirmResult = waitForConfirmation(stepResult.getTransactionHash(), step);
                        Long blockHeight = confirmResult.map(ConfirmationResult::getBlockHeight).orElse(null);
                        Integer confirmDepth = confirmResult.map(ConfirmationResult::getConfirmationDepth).orElse(null);
                        persistTransactionConfirmed(flow, step, stepResult.getTransactionHash(), blockHeight, confirmDepth);
                    } else {
                        listener.onStepFailed(step, stepResult);
                        handle.updateStatus(FlowStatus.FAILED);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        persistFlowComplete(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                handle.updateStatus(FlowStatus.COMPLETED);
                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                persistFlowComplete(flow, FlowStatus.COMPLETED);
                return successResult;

            } catch (RollbackException e) {
                // Persist rollback state
                persistTransactionRolledBack(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                if (e.isRequiresFlowRestart()) {
                    flowRestartAttempts++;
                    if (flowRestartAttempts > maxRollbackRetries) {
                        log.error("Flow restart limit ({}) reached after rollback at step '{}'",
                                maxRollbackRetries, e.getStep().getId());
                        handle.updateStatus(FlowStatus.FAILED);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.failure(
                                new FlowExecutionException("Flow restart limit reached after rollback", e));
                        listener.onFlowFailed(flow, failedResult);
                        persistFlowComplete(flow, FlowStatus.FAILED);
                        return failedResult;
                    }

                    log.info("Restarting flow (attempt {}/{}) due to rollback at step '{}'",
                            flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                    listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                            "Rollback detected at step '" + e.getStep().getId() + "'");
                    waitForBackendReadyAfterRollback(); // Wait for backend to sync after rollback
                    stepRollbackAttempts.clear();
                    handle.resetCompletedSteps();
                    continue;
                } else {
                    log.error("Step rebuild failed for step '{}'", e.getStep().getId());
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(e);
                    listener.onFlowFailed(flow, failedResult);
                    persistFlowComplete(flow, FlowStatus.FAILED);
                    return failedResult;
                }
            } catch (Exception e) {
                log.error("Flow execution failed", e);
                handle.updateStatus(FlowStatus.FAILED);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                persistFlowComplete(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        handle.updateStatus(FlowStatus.FAILED);
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("Flow execution failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        persistFlowComplete(flow, FlowStatus.FAILED);
        return failedResult;
    }

    private FlowResult executeWithHandlePipelined(TxFlow flow, FlowHandle handle) {
        int maxRollbackRetries = getMaxRollbackRetries();
        int flowRestartAttempts = 0;

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(Instant.now());

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
                persistFlowStarted(flow);
            }

            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();
            List<String> submittedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();

            try {
                // Phase 1: Build and submit all transactions
                log.info("PIPELINED mode: Submitting {} transactions", totalSteps);
                for (int i = 0; i < totalSteps; i++) {
                    FlowStep step = steps.get(i);
                    handle.updateCurrentStep(step.getId());
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRetry(step, context, flow.getVariables(), true);
                    stepResults.add(stepResult);
                    resultBuilder.addStepResult(stepResult);

                    if (stepResult.isSuccessful()) {
                        submittedTxHashes.add(stepResult.getTransactionHash());
                        listener.onTransactionSubmitted(step, stepResult.getTransactionHash());
                        persistTransactionSubmitted(flow, step, stepResult.getTransactionHash());
                    } else {
                        listener.onStepFailed(step, stepResult);
                        handle.updateStatus(FlowStatus.FAILED);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        listener.onFlowFailed(flow, failedResult);
                        persistFlowComplete(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                // Phase 2: Wait for all confirmations
                log.info("PIPELINED mode: Waiting for {} transactions to confirm", submittedTxHashes.size());
                for (int i = 0; i < submittedTxHashes.size(); i++) {
                    String txHash = submittedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step);
                    if (confirmResult.isPresent()) {
                        ConfirmationResult result = confirmResult.get();
                        handle.incrementCompletedSteps();
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        persistTransactionConfirmed(flow, step, txHash,
                                result.getBlockHeight(), result.getConfirmationDepth());
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                new ConfirmationTimeoutException(txHash));
                        listener.onStepFailed(step, failedResult);
                        handle.updateStatus(FlowStatus.FAILED);
                        resultBuilder.completedAt(Instant.now());
                        FlowResult flowFailedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        listener.onFlowFailed(flow, flowFailedResult);
                        persistFlowComplete(flow, FlowStatus.FAILED);
                        return flowFailedResult;
                    }
                }

                handle.updateStatus(FlowStatus.COMPLETED);
                resultBuilder.completedAt(Instant.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                persistFlowComplete(flow, FlowStatus.COMPLETED);
                return successResult;

            } catch (RollbackException e) {
                // Persist rollback state
                persistTransactionRolledBack(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    log.error("Flow restart limit ({}) reached after rollback at step '{}' in PIPELINED mode",
                            maxRollbackRetries, e.getStep().getId());
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in PIPELINED mode", e));
                    listener.onFlowFailed(flow, failedResult);
                    persistFlowComplete(flow, FlowStatus.FAILED);
                    return failedResult;
                }

                log.info("Restarting PIPELINED flow (attempt {}/{}) due to rollback at step '{}'",
                        flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                        "Rollback detected at step '" + e.getStep().getId() + "' in PIPELINED mode");
                waitForBackendReadyAfterRollback(); // Wait for backend to sync after rollback
                handle.resetCompletedSteps();
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                handle.updateStatus(FlowStatus.FAILED);
                FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                        .startedAt(Instant.now())
                        .completedAt(Instant.now());
                FlowResult failedResult = errorBuilder.failure(e);
                listener.onFlowFailed(flow, failedResult);
                persistFlowComplete(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        handle.updateStatus(FlowStatus.FAILED);
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("PIPELINED flow execution failed: exceeded maximum restart attempts"));
        listener.onFlowFailed(flow, failedResult);
        persistFlowComplete(flow, FlowStatus.FAILED);
        return failedResult;
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
                                                 java.util.Map<String, Object> variables, boolean pipelined) {
        // Determine retry policy (step-level overrides default)
        RetryPolicy policy = step.hasRetryPolicy() ? step.getRetryPolicy() : defaultRetryPolicy;
        int maxAttempts = (policy != null) ? policy.getMaxAttempts() : 1;

        Throwable lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                FlowStepResult result = pipelined
                        ? executeStepPipelined(step, context, variables)
                        : executeStepSequential(step, context, variables);

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
                                                  java.util.Map<String, Object> variables) {
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
            TxResult result = txContext.completeAndWait(confirmationTimeout, checkInterval,
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
                    Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step);
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
        if (!step.hasDependencies()) {
            return baseUtxoSupplier;
        }

        // Create FlowUtxoSupplier with dependencies
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
     * Execute flow in BATCH mode: build all transactions first, then submit all at once.
     * <p>
     * This mode provides the highest likelihood of all transactions landing in the same
     * block, as all submissions happen within milliseconds of each other.
     * <p>
     * Phase 1: Build and sign ALL transactions (computing hashes client-side)
     * Phase 2: Submit ALL transactions in rapid succession
     * Phase 3: Wait for all confirmations
     */
    private FlowResult executeBatch(TxFlow flow) {
        FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
        FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now());

        listener.onFlowStarted(flow);

        List<FlowStep> steps = flow.getSteps();
        int totalSteps = steps.size();

        // Store built transactions for batch submission
        List<Transaction> builtTransactions = new ArrayList<>();
        List<String> precomputedTxHashes = new ArrayList<>();
        List<FlowStepResult> stepResults = new ArrayList<>();

        try {
            // ============ PHASE 1: BUILD ALL TRANSACTIONS ============
            log.info("BATCH mode: Building {} transactions", totalSteps);
            for (int i = 0; i < totalSteps; i++) {
                FlowStep step = steps.get(i);
                listener.onStepStarted(step, i, totalSteps);

                // Build step WITHOUT submitting
                BuildResult buildResult = buildStepOnly(step, context, flow.getVariables());

                if (buildResult.isSuccessful()) {
                    Transaction tx = buildResult.getTransaction();
                    String txHash = TransactionUtil.getTxHash(tx);

                    builtTransactions.add(tx);
                    precomputedTxHashes.add(txHash);

                    // Capture outputs using pre-computed hash
                    List<Utxo> outputUtxos = captureOutputUtxos(tx, txHash);
                    List<TransactionInput> spentInputs = captureSpentInputs(tx);

                    // Record result (not submitted yet, but hash is known)
                    FlowStepResult stepResult = FlowStepResult.success(step.getId(), txHash, outputUtxos, spentInputs);
                    stepResults.add(stepResult);
                    resultBuilder.addStepResult(stepResult);
                    context.recordStepResult(step.getId(), stepResult);

                    log.debug("Step '{}' built: {} with {} outputs", step.getId(), txHash, outputUtxos.size());
                } else {
                    // Build failed
                    FlowStepResult failedResult = FlowStepResult.failure(step.getId(), buildResult.getError());
                    listener.onStepFailed(step, failedResult);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(failedResult.getError())
                            .build();
                    listener.onFlowFailed(flow, flowFailed);
                    return flowFailed;
                }
            }

            // ============ PHASE 2: SUBMIT ALL TRANSACTIONS ============
            log.info("BATCH mode: Submitting {} transactions", builtTransactions.size());
            for (int i = 0; i < builtTransactions.size(); i++) {
                Transaction tx = builtTransactions.get(i);
                FlowStep step = steps.get(i);
                String expectedHash = precomputedTxHashes.get(i);

                TxResult result = submitTransaction(tx);

                if (result.isSuccessful()) {
                    String actualHash = result.getValue();
                    // Verify hash matches (should always match)
                    if (!actualHash.equals(expectedHash)) {
                        log.warn("Hash mismatch! Expected: {}, Actual: {}", expectedHash, actualHash);
                    }
                    listener.onTransactionSubmitted(step, actualHash);
                    log.debug("Step '{}' submitted: {}", step.getId(), actualHash);
                } else {
                    // Submission failed
                    FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                            new RuntimeException("Transaction submission failed: " + result.getResponse()));
                    listener.onStepFailed(step, failedResult);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(failedResult.getError())
                            .build();
                    listener.onFlowFailed(flow, flowFailed);
                    return flowFailed;
                }
            }

            // ============ PHASE 3: WAIT FOR CONFIRMATIONS ============
            log.info("BATCH mode: Waiting for {} confirmations", precomputedTxHashes.size());
            for (int i = 0; i < precomputedTxHashes.size(); i++) {
                String txHash = precomputedTxHashes.get(i);
                FlowStep step = steps.get(i);

                Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step);
                if (confirmResult.isPresent()) {
                    listener.onTransactionConfirmed(step, txHash);
                    listener.onStepCompleted(step, stepResults.get(i));
                } else {
                    FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                            new ConfirmationTimeoutException(txHash));
                    listener.onStepFailed(step, failedResult);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(failedResult.getError())
                            .build();
                    listener.onFlowFailed(flow, flowFailed);
                    return flowFailed;
                }
            }

            resultBuilder.completedAt(Instant.now());
            FlowResult successResult = resultBuilder.success();
            listener.onFlowCompleted(flow, successResult);
            return successResult;

        } catch (Exception e) {
            log.error("Flow execution failed", e);
            resultBuilder.completedAt(Instant.now());
            FlowResult failedResult = resultBuilder.failure(e);
            listener.onFlowFailed(flow, failedResult);
            return failedResult;
        }
    }

    /**
     * Execute flow in BATCH mode with a FlowHandle for async tracking.
     */
    private FlowResult executeWithHandleBatch(TxFlow flow, FlowHandle handle) {
        FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
        FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now());

        listener.onFlowStarted(flow);

        List<FlowStep> steps = flow.getSteps();
        int totalSteps = steps.size();

        List<Transaction> builtTransactions = new ArrayList<>();
        List<String> precomputedTxHashes = new ArrayList<>();
        List<FlowStepResult> stepResults = new ArrayList<>();

        try {
            // Phase 1: Build all transactions
            log.info("BATCH mode: Building {} transactions", totalSteps);
            for (int i = 0; i < totalSteps; i++) {
                FlowStep step = steps.get(i);
                handle.updateCurrentStep(step.getId());
                listener.onStepStarted(step, i, totalSteps);

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
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(failedResult.getError())
                            .build();
                    listener.onFlowFailed(flow, flowFailed);
                    return flowFailed;
                }
            }

            // Phase 2: Submit all transactions
            log.info("BATCH mode: Submitting {} transactions", builtTransactions.size());
            for (int i = 0; i < builtTransactions.size(); i++) {
                Transaction tx = builtTransactions.get(i);
                FlowStep step = steps.get(i);
                String expectedHash = precomputedTxHashes.get(i);

                TxResult result = submitTransaction(tx);

                if (result.isSuccessful()) {
                    String actualHash = result.getValue();
                    if (!actualHash.equals(expectedHash)) {
                        log.warn("Hash mismatch! Expected: {}, Actual: {}", expectedHash, actualHash);
                    }
                    listener.onTransactionSubmitted(step, actualHash);
                } else {
                    FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                            new RuntimeException("Transaction submission failed: " + result.getResponse()));
                    listener.onStepFailed(step, failedResult);
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(failedResult.getError())
                            .build();
                    listener.onFlowFailed(flow, flowFailed);
                    return flowFailed;
                }
            }

            // Phase 3: Wait for confirmations
            log.info("BATCH mode: Waiting for {} confirmations", precomputedTxHashes.size());
            for (int i = 0; i < precomputedTxHashes.size(); i++) {
                String txHash = precomputedTxHashes.get(i);
                FlowStep step = steps.get(i);

                Optional<ConfirmationResult> confirmResult = waitForConfirmation(txHash, step);
                if (confirmResult.isPresent()) {
                    handle.incrementCompletedSteps();
                    listener.onTransactionConfirmed(step, txHash);
                    listener.onStepCompleted(step, stepResults.get(i));
                } else {
                    FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                            new ConfirmationTimeoutException(txHash));
                    listener.onStepFailed(step, failedResult);
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(failedResult.getError())
                            .build();
                    listener.onFlowFailed(flow, flowFailed);
                    return flowFailed;
                }
            }

            handle.updateStatus(FlowStatus.COMPLETED);
            resultBuilder.completedAt(Instant.now());
            FlowResult successResult = resultBuilder.success();
            listener.onFlowCompleted(flow, successResult);
            return successResult;

        } catch (Exception e) {
            log.error("Flow execution failed", e);
            handle.updateStatus(FlowStatus.FAILED);
            resultBuilder.completedAt(Instant.now());
            FlowResult failedResult = resultBuilder.failure(e);
            listener.onFlowFailed(flow, failedResult);
            return failedResult;
        }
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
}
