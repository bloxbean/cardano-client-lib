package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.script.ScriptRegistry;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.config.RollbackPolicy;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
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
 *     .withListener(new LoggingFlowListener())
 *     .withExecutor(applicationExecutor);
 *
 * FlowHandle handle = executor.execute(flow);
 * FlowResult result = handle.await();
 * }</pre>
 * Configure an executor before starting flows. Each execution snapshots the effective
 * settings for that flow, but mutating executor configuration while flows are in flight
 * is not an atomic reconfiguration operation.
 */
@Slf4j
public class FlowExecutor implements AutoCloseable {
    private final UtxoSupplier baseUtxoSupplier;
    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final TransactionProcessor transactionProcessor;
    private final ChainDataSupplier chainDataSupplier;
    private final TxPlanExecutionMaterializer planMaterializer = new TxPlanExecutionMaterializer();
    private FlowScheduler scheduler = FlowScheduler.system();
    private static final Duration DEFAULT_CONFIRMATION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(2);

    private volatile SignerRegistry signerRegistry;
    private volatile ScriptRegistry scriptRegistry;
    private volatile FlowListener listener = FlowListener.NOOP;
    private volatile Executor executor;
    private volatile Consumer<Transaction> txInspector;
    private volatile ChainingMode chainingMode = ChainingMode.SEQUENTIAL;
    private volatile RetryPolicy defaultRetryPolicy;
    private volatile ConfirmationConfig confirmationConfig;
    private volatile RollbackStrategy rollbackStrategy = RollbackStrategy.FAIL_IMMEDIATELY;
    private volatile boolean chainingModeConfigured;
    private volatile boolean defaultRetryPolicyConfigured;
    private volatile boolean confirmationConfigConfigured;
    private volatile boolean rollbackStrategyConfigured;
    private volatile FlowRegistry flowRegistry;
    private volatile FlowStateStore flowStateStore;
    private volatile PersistencePort persistencePort = PersistencePort.NOOP;
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
     * Configures logical script-reference resolution for {@code TxPlan}-backed steps.
     *
     * @param registry application-owned script registry, or {@code null} when plans use no references
     * @return this executor
     */
    public FlowExecutor withScriptRegistry(ScriptRegistry registry) {
        this.scriptRegistry = registry;
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
     * Async execution requires an application-managed executor. FlowExecutor never
     * creates or owns execution threads.
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

    private Executor requireAsyncExecutor() {
        if (executor == null) {
            throw new IllegalStateException(
                    "Async flow execution requires a caller-supplied Executor. "
                            + "Call withExecutor(...); Java 21 callers may supply a virtual-thread executor.");
        }
        return executor;
    }

    FlowExecutor withScheduler(FlowScheduler scheduler) {
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        return this;
    }

    FlowExecutor withPersistencePort(PersistencePort persistencePort) {
        this.persistencePort = java.util.Objects.requireNonNull(persistencePort, "persistencePort");
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
     * Calling this setter marks the chaining mode as explicitly configured, even when
     * {@code mode} is null. A null value explicitly selects {@link ChainingMode#SEQUENTIAL}
     * and suppresses any flow-level chaining mode.
     *
     * @param mode the chaining mode
     * @return this executor
     */
    public FlowExecutor withChainingMode(ChainingMode mode) {
        this.chainingMode = mode != null ? mode : ChainingMode.SEQUENTIAL;
        this.chainingModeConfigured = true;
        return this;
    }

    /**
     * Set a default retry policy for all steps.
     * <p>
     * This policy will be used for any step that doesn't have its own step-level retry policy.
     * If not set, no retries will be performed by default.
     * Calling this setter marks the default retry policy as explicitly configured, even when
     * {@code retryPolicy} is null. A null value explicitly disables the executor default
     * retry policy and suppresses any flow-level default retry policy.
     *
     * @param retryPolicy the default retry policy
     * @return this executor
     */
    public FlowExecutor withDefaultRetryPolicy(RetryPolicy retryPolicy) {
        this.defaultRetryPolicy = retryPolicy;
        this.defaultRetryPolicyConfigured = true;
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
     * Calling this setter marks confirmation as explicitly configured, even when
     * {@code config} is null. A null value explicitly selects simple confirmation mode
     * and suppresses any flow-level confirmation config.
     *
     * @param config the confirmation configuration
     * @return this executor
     */
    public FlowExecutor withConfirmationConfig(ConfirmationConfig config) {
        this.confirmationConfig = config;
        this.confirmationConfigConfigured = true;
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
     * Note: Non-default rollback strategies only take effect when confirmation tracking
     * is configured either on the executor or in the flow-level execution settings.
     * Calling this setter marks the rollback strategy as explicitly configured, even when
     * {@code strategy} is null. A null value explicitly selects
     * {@link RollbackStrategy#FAIL_IMMEDIATELY} and suppresses any flow-level rollback strategy.
     *
     * @param strategy the rollback strategy
     * @return this executor
     */
    public FlowExecutor withRollbackStrategy(RollbackStrategy strategy) {
        this.rollbackStrategy = strategy != null ? strategy : RollbackStrategy.FAIL_IMMEDIATELY;
        this.rollbackStrategyConfigured = true;
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
     * Identifies whether a shared mode strategy is starting a new execution or
     * continuing one represented by a previous {@link FlowResult}. Resume must
     * not recreate the legacy state snapshot or emit a second flow-start event.
     */
    private enum ExecutionOrigin {
        FRESH,
        RESUME
    }

    private void notifyFlowStarting(TxFlow flow, ExecutionHooks hooks,
                                    ExecutionOrigin origin) {
        if (origin == ExecutionOrigin.FRESH) {
            listener.onFlowStarted(flow);
            hooks.onFlowStarting(flow);
        }
    }

    /**
     * Create hooks for synchronous (no FlowHandle) execution.
     * Only calls persistence methods, no handle tracking.
     */
    private ExecutionHooks syncHooks(TxFlow flow) {
        return syncHooks(flow, () -> false);
    }

    private ExecutionHooks syncHooks(TxFlow flow, BooleanSupplier cancelCheck) {
        return new ExecutionHooks() {
            @Override public void onFlowStarting(TxFlow f) { persistFlowStarted(f); }
            @Override public void onStepStarting(FlowStep step) { /* no handle to update */ }
            @Override public void onStepCompleted(FlowStep step, FlowStepResult result) { /* no handle to update */ }
            @Override public void onTransactionSubmitted(TxFlow f, FlowStep step, String txHash) {
                persistTransactionSubmitted(f, step, txHash);
            }
            @Override public void onTransactionConfirmed(TxFlow f, FlowStep step, String txHash, ConfirmationResult confirmResult) {
                persistencePort.onConfirmed(step, txHash);
                Long blockHeight = confirmResult != null ? confirmResult.getBlockHeight() : null;
                Integer confirmDepth = confirmResult != null ? confirmResult.getConfirmationDepth() : null;
                persistTransactionConfirmed(f, step, txHash, blockHeight, confirmDepth);
            }
            @Override public void onFlowFailed(TxFlow f, FlowStatus status) {
                persistFlowComplete(f, status);
            }
            @Override public void onFlowCompleted(TxFlow f) { persistFlowComplete(f, FlowStatus.COMPLETED); }
            @Override public void onFlowRestarting() { /* no handle to reset */ }
            @Override public void onRollbackDetected(TxFlow f, FlowStep step, String txHash, long prevBlockHeight, String msg) {
                persistencePort.onRolledBack(step, txHash, prevBlockHeight);
                persistTransactionRolledBack(f, step, txHash, prevBlockHeight, msg);
            }
            @Override public boolean isCancelled() { return cancelCheck.getAsBoolean(); }
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
                persistencePort.onConfirmed(step, txHash);
                Long blockHeight = confirmResult != null ? confirmResult.getBlockHeight() : null;
                Integer confirmDepth = confirmResult != null ? confirmResult.getConfirmationDepth() : null;
                persistTransactionConfirmed(f, step, txHash, blockHeight, confirmDepth);
            }
            @Override public void onFlowFailed(TxFlow f, FlowStatus status) {
                handle.updateStatus(status);
                persistFlowComplete(f, status);
            }
            @Override public void onFlowCompleted(TxFlow f) {
                handle.updateStatus(FlowStatus.COMPLETED);
                persistFlowComplete(f, FlowStatus.COMPLETED);
            }
            @Override public void onFlowRestarting() { handle.resetCompletedSteps(); }
            @Override public void onRollbackDetected(TxFlow f, FlowStep step, String txHash, long prevBlockHeight, String msg) {
                persistencePort.onRolledBack(step, txHash, prevBlockHeight);
                persistTransactionRolledBack(f, step, txHash, prevBlockHeight, msg);
            }
            @Override public boolean isCancelled() { return handle.isCancelled(); }
        };
    }

    @Getter
    @AllArgsConstructor
    static class EffectiveFlowExecutionSettings {
        private final ChainingMode chainingMode;
        private final RetryPolicy defaultRetryPolicy;
        private final ConfirmationConfig confirmationConfig;
        private final ConfirmationTracker confirmationTracker;
        private final RollbackStrategy rollbackStrategy;
        private final boolean monitorUntilFlowTerminal;
        private final RollbackPolicy portableRollbackPolicy;
    }

    EffectiveFlowExecutionSettings effectiveSettings(TxFlow flow) {
        FlowExecutionSettings flowSettings = flow.getExecutionSettings() != null
                ? flow.getExecutionSettings()
                : FlowExecutionSettings.empty();

        ChainingMode effectiveChainingMode = chainingModeConfigured
                ? chainingMode
                : flowSettings.getChainingMode() != null
                        ? flowSettings.getChainingMode()
                        : ChainingMode.SEQUENTIAL;

        RetryPolicy effectiveDefaultRetryPolicy = defaultRetryPolicyConfigured
                ? defaultRetryPolicy
                : flowSettings.getRetryPolicy();

        ConfirmationConfig effectiveConfirmationConfig = confirmationConfigConfigured
                ? confirmationConfig
                : flowSettings.getConfirmationConfig();

        RollbackStrategy effectiveRollbackStrategy = rollbackStrategyConfigured
                ? rollbackStrategy
                : flowSettings.getRollbackStrategy() != null
                        ? flowSettings.getRollbackStrategy()
                        : portableRollbackStrategy(flowSettings.getRollbackPolicy());
        RollbackPolicy effectivePortableRollbackPolicy = !rollbackStrategyConfigured
                && flowSettings.getRollbackStrategy() == null
                ? flowSettings.getRollbackPolicy() : null;

        if (!confirmationConfigConfigured && flowSettings.getRollbackPolicy() != null) {
            ConfirmationConfig base = effectiveConfirmationConfig != null
                    ? effectiveConfirmationConfig : ConfirmationConfig.defaults();
            effectiveConfirmationConfig = ConfirmationConfig.builder()
                    .minConfirmations(base.getMinConfirmations())
                    .checkInterval(base.getCheckInterval())
                    .timeout(base.getTimeout())
                    .maxRollbackRetries(flowSettings.getRollbackPolicy().maxRecoveryCycles())
                    .waitForBackendAfterRollback(base.isWaitForBackendAfterRollback())
                    .postRollbackWaitAttempts(base.getPostRollbackWaitAttempts())
                    .postRollbackUtxoSyncDelay(base.getPostRollbackUtxoSyncDelay())
                    .requiredAuthoritativeAbsences(flowSettings.getRollbackPolicy()
                            .minimumConsistentAbsenceObservations())
                    .build();
        }

        ConfirmationTracker effectiveConfirmationTracker = effectiveConfirmationConfig != null
                ? new ConfirmationTracker(chainDataSupplier, effectiveConfirmationConfig, scheduler)
                : null;

        return new EffectiveFlowExecutionSettings(
                effectiveChainingMode,
                effectiveDefaultRetryPolicy,
                effectiveConfirmationConfig,
                effectiveConfirmationTracker,
                effectiveRollbackStrategy,
                flowSettings.getRollbackPolicy() != null
                        && flowSettings.getRollbackPolicy().monitoringHorizon()
                        == com.bloxbean.cardano.client.txflow.config.RollbackMonitoringHorizon.UNTIL_FLOW_TERMINAL,
                effectivePortableRollbackPolicy);
    }

    private RollbackStrategy portableRollbackStrategy(
            com.bloxbean.cardano.client.txflow.config.RollbackPolicy policy) {
        if (policy == null) return RollbackStrategy.FAIL_IMMEDIATELY;
        return switch (policy.action()) {
            case FAIL, PAUSE_FOR_RECOVERY -> RollbackStrategy.FAIL_IMMEDIATELY;
            case WAIT_FOR_REINCLUSION -> RollbackStrategy.NOTIFY_ONLY;
            case RECONCILE_AND_REBUILD -> policy.rebuildScope()
                    == com.bloxbean.cardano.client.txflow.config.RollbackRebuildScope.AFFECTED_STEP
                    ? RollbackStrategy.REBUILD_FROM_FAILED
                    : RollbackStrategy.REBUILD_ENTIRE_FLOW;
        };
    }

    private FlowHorizonMonitor createLiveAttemptMonitor(
            EffectiveFlowExecutionSettings settings) {
        return settings.isMonitorUntilFlowTerminal()
                && settings.getConfirmationTracker() != null
                ? new FlowHorizonMonitor(settings.getConfirmationTracker()) : null;
    }

    private void verifyLiveAttempts(TxFlow flow, FlowHorizonMonitor monitor,
                                    ExecutionHooks hooks, BooleanSupplier cancelCheck,
                                    EffectiveFlowExecutionSettings settings) {
        if (monitor == null) return;
        FlowHorizonMonitor.HorizonResult horizon = monitor.verify(cancelCheck);
        if (horizon != null) {
            String transactionHash = horizon.transactionHash();
            FlowStep step = horizon.step();
            ConfirmationResult result = horizon.confirmation();
            Throwable observationError = result.getError();
            if (cancelCheck.getAsBoolean()
                    || observationError instanceof CancellationException
                    || observationError instanceof InterruptedException
                    || Thread.currentThread().isInterrupted()) {
                if (observationError instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (observationError instanceof CancellationException) {
                    throw (CancellationException) observationError;
                }
                CancellationException cancellation = new CancellationException(
                        "Flow cancelled while monitoring the live confirmation horizon");
                if (observationError != null) cancellation.initCause(observationError);
                throw cancellation;
            }
            if (result.isRolledBack()) {
                long previousHeight = result.getBlockHeight() != null ? result.getBlockHeight() : 0;
                if (settings.getRollbackStrategy() == RollbackStrategy.NOTIFY_ONLY) {
                    ConfirmationOutcome reinclusion = waitForConfirmationWithTracking(
                            transactionHash, step, cancelCheck, settings);
                    if (reinclusion.isConfirmed()) {
                        hooks.onTransactionConfirmed(
                                flow, step, transactionHash, reinclusion.getResult());
                        return;
                    }
                    Throwable reinclusionError = confirmationFailure(
                            step, transactionHash, reinclusion);
                    hooks.onRollbackDetected(flow, step, transactionHash,
                            previousHeight, reinclusionError.getMessage());
                    throw new FlowExecutionException(
                            "Transaction rolled back while monitoring the live flow", reinclusionError);
                }
                boolean rebuild = settings.getRollbackStrategy() == RollbackStrategy.REBUILD_ENTIRE_FLOW
                        || settings.getRollbackStrategy() == RollbackStrategy.REBUILD_FROM_FAILED;
                RollbackException rollback = rebuild
                        ? RollbackException.forFlowRestart(transactionHash, step, previousHeight)
                        : RollbackException.forStepRebuild(transactionHash, step, previousHeight);
                listener.onTransactionRolledBack(step, transactionHash, previousHeight);
                hooks.onRollbackDetected(flow, step, transactionHash, previousHeight,
                        rollback.getMessage());
                if (rebuild) {
                    throw rollback;
                }
                throw new FlowExecutionException(
                        "Transaction rolled back before the flow reached its terminal horizon", rollback);
            }
            Throwable error = result.getError() != null ? result.getError()
                    : new ReconciliationUncertainException(transactionHash);
            throw new FlowExecutionException(
                    "Transaction state remained uncertain while monitoring the live flow", error);
        }
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
    private void validateConfiguration(EffectiveFlowExecutionSettings settings) {
        if (settings.getRollbackStrategy() != RollbackStrategy.FAIL_IMMEDIATELY
                && settings.getConfirmationConfig() == null) {
            throw new IllegalStateException(
                    "Rollback strategy " + settings.getRollbackStrategy() + " requires confirmation tracking. " +
                    "Call withConfirmationConfig() or set context.confirmation before execute().");
        }
    }

    private void validateExecutableFlow(TxFlow flow) {
        List<String> templateSteps = flow.getSteps().stream()
                .filter(FlowStep::hasTransactionTemplate)
                .map(FlowStep::getId)
                .toList();
        if (!templateSteps.isEmpty()) {
            throw new FlowExecutionException(
                    "Portable transaction templates must be compiled with TxFlowCompiler "
                            + "or executed through FlowEngine before using FlowExecutor; template steps: "
                            + templateSteps);
        }
    }

    private void recordRetainedStep(FlowStep step, FlowStepResult result,
                                    FlowExecutionContext context,
                                    FlowResult.Builder resultBuilder,
                                    List<String> flowTransactionHashes,
                                    FlowHorizonMonitor liveMonitor,
                                    ExecutionHooks hooks) {
        context.recordStepResult(step, result);
        resultBuilder.addStepResult(result);
        flowTransactionHashes.add(result.getTransactionHash());
        if (liveMonitor != null) {
            liveMonitor.track(step, result.getTransactionHash());
        }
        completeRetainedStep(step, result, hooks);
        log.info("Step '{}' retained after chain reconciliation: {}",
                step.getId(), result.getTransactionHash());
    }

    private void completeRetainedStep(FlowStep step, FlowStepResult result,
                                      ExecutionHooks hooks) {
        hooks.onStepCompleted(step, result);
        listener.onTransactionConfirmed(step, result.getTransactionHash());
        listener.onStepCompleted(step, result);
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
     * @throws IllegalStateException if the previous result contains a submission-pending step
     *         ({@link FlowStatus#IN_PROGRESS} with a transaction hash) whose on-chain outcome is
     *         unknown — reconcile that transaction on chain before resuming
     * @throws FlowExecutionException if flow validation fails
     */
    public FlowResult resumeSync(TxFlow flow, FlowResult previousResult) {
        EffectiveFlowExecutionSettings settings = effectiveSettings(flow);
        validateConfiguration(settings);

        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }
        validateExecutableFlow(flow);

        validateResumeArgs(flow, previousResult);

        Map<Integer, FlowStepResult> confirmedSteps = verifyPreviousSteps(flow, previousResult, settings);

        if (!activeFlowIds.add(flow.getId())) {
            throw new IllegalStateException("Flow '" + flow.getId() + "' is already executing");
        }

        try {
            ExecutionHooks hooks = syncHooks(flow);
            return ChainingStrategy.forMode(settings.getChainingMode()).execute(
                    () -> doExecuteSequential(flow, hooks, confirmedSteps, settings, ExecutionOrigin.RESUME),
                    () -> doExecutePipelined(flow, hooks, confirmedSteps, settings, ExecutionOrigin.RESUME),
                    () -> doExecuteBatch(flow, hooks, confirmedSteps, settings, ExecutionOrigin.RESUME));
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
     * @throws IllegalStateException if the flow is already executing, or if the previous result
     *         contains a submission-pending step ({@link FlowStatus#IN_PROGRESS} with a transaction
     *         hash) whose on-chain outcome is unknown — reconcile that transaction on chain before resuming
     */
    public FlowHandle resume(TxFlow flow, FlowResult previousResult) {
        EffectiveFlowExecutionSettings settings = effectiveSettings(flow);
        validateConfiguration(settings);

        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }
        validateExecutableFlow(flow);

        validateResumeArgs(flow, previousResult);
        Executor asyncExecutor = requireAsyncExecutor();

        // Verify previous steps BEFORE launching async (fail-fast)
        Map<Integer, FlowStepResult> confirmedSteps = verifyPreviousSteps(flow, previousResult, settings);

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
                ExecutionHooks hooks = handleHooks(flow, handle);
                FlowResult result = ChainingStrategy.forMode(settings.getChainingMode()).execute(
                        () -> doExecuteSequential(flow, hooks, confirmedSteps, settings, ExecutionOrigin.RESUME),
                        () -> doExecutePipelined(flow, hooks, confirmedSteps, settings, ExecutionOrigin.RESUME),
                        () -> doExecuteBatch(flow, hooks, confirmedSteps, settings, ExecutionOrigin.RESUME));
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

        asyncExecutor.execute(task);

        return handle;
    }

    /**
     * Execute a flow synchronously.
     *
     * @param flow the flow to execute
     * @return the flow result
     */
    public FlowResult executeSync(TxFlow flow) {
        return executeSync(flow, () -> false);
    }

    FlowResult executeSync(TxFlow flow, BooleanSupplier cancelCheck) {
        EffectiveFlowExecutionSettings settings = effectiveSettings(flow);
        validateConfiguration(settings);

        // Validate the flow
        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }
        validateExecutableFlow(flow);

        if (!activeFlowIds.add(flow.getId())) {
            throw new IllegalStateException("Flow '" + flow.getId() + "' is already executing");
        }

        try {
            ExecutionHooks hooks = syncHooks(flow, cancelCheck);
            return ChainingStrategy.forMode(settings.getChainingMode()).execute(
                    () -> doExecuteSequential(flow, hooks, Map.of(), settings, ExecutionOrigin.FRESH),
                    () -> doExecutePipelined(flow, hooks, Map.of(), settings, ExecutionOrigin.FRESH),
                    () -> doExecuteBatch(flow, hooks, Map.of(), settings, ExecutionOrigin.FRESH));
        } finally {
            activeFlowIds.remove(flow.getId());
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
    private FlowResult doExecuteSequential(TxFlow flow, ExecutionHooks hooks,
                                           Map<Integer, FlowStepResult> initialConfirmedSteps,
                                           EffectiveFlowExecutionSettings settings,
                                           ExecutionOrigin origin) {
        int maxRollbackRetries = getMaxRollbackRetries(settings);
        int flowRestartAttempts = 0;
        Map<String, Integer> stepRollbackAttempts = new ConcurrentHashMap<>();
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>(initialConfirmedSteps);
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;
        String executionId = java.util.UUID.randomUUID().toString();
        log.info("Starting TxFlow execution {} for definition {}", executionId, flow.getId());

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), executionId, flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(scheduler.now());
            FlowHorizonMonitor liveMonitor = createLiveAttemptMonitor(settings);

            if (flowRestartAttempts == 0) {
                notifyFlowStarting(flow, hooks, origin);
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();
            List<String> attemptTxHashes = new ArrayList<>();
            List<FlowStepResult> attemptStepResults = new ArrayList<>();
            Set<Integer> skippedStepIndices;
            try {
                skippedStepIndices = previousConfirmedSteps.isEmpty()
                        ? Set.of()
                        : verifyRetainedSteps(
                                previousConfirmedSteps, steps, cancelCheck, settings);
            } catch (CancellationException cancellation) {
                resultBuilder.withStepResults(orderedStepResults(previousConfirmedSteps));
                return cancelledFlowResult(flow, resultBuilder, hooks, cancellation);
            }

            try {
                for (int i = 0; i < totalSteps; i++) {
                    // Check for cancellation
                    if (hooks.isCancelled()) {
                        return cancelledFlowResult(flow, resultBuilder, hooks, null);
                    }

                    FlowStep step = steps.get(i);
                    if (skippedStepIndices.contains(i)) {
                        FlowStepResult retained = previousConfirmedSteps.get(i);
                        attemptTxHashes.add(retained.getTransactionHash());
                        attemptStepResults.add(retained);
                        recordRetainedStep(
                                step, retained, context, resultBuilder, flowTxHashes, liveMonitor, hooks);
                        continue;
                    }
                    verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);
                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRollbackHandling(
                            step, context, flow.getVariables(), false,
                            stepRollbackAttempts, maxRollbackRetries, cancelCheck, settings);

                    if (stepResult.isSuccessful()) {
                        String txHash = stepResult.getTransactionHash();
                        attemptTxHashes.add(txHash);
                        attemptStepResults.add(stepResult);
                        flowTxHashes.add(txHash);
                        hooks.onTransactionSubmitted(flow, step, txHash);

                        // Get confirmation details - tx is already confirmed from completeAndWait()
                        ConfirmationOutcome confirmation = waitForConfirmation(txHash, step, cancelCheck, settings);
                        if (confirmation.isConfirmed()) {
                            resultBuilder.addStepResult(stepResult);
                            hooks.onStepCompleted(step, stepResult);
                            listener.onStepCompleted(step, stepResult);
                            hooks.onTransactionConfirmed(flow, step, txHash, confirmation.getResult());
                            if (liveMonitor != null) liveMonitor.track(step, txHash);
                        } else {
                            return terminalConfirmationResult(
                                    flow, step, stepResult, confirmation, resultBuilder, hooks);
                        }
                    } else {
                        resultBuilder.addStepResult(stepResult);
                        if (isCancellationProjection(stepResult)) {
                            return cancelledFlowResult(
                                    flow, resultBuilder, hooks, stepResult.getError());
                        }
                        notifyStepTerminal(step, stepResult);
                        resultBuilder.completedAt(scheduler.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        notifyFlowTerminal(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }
                }

                verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);

                // Cleanup tracked transactions to prevent memory leak
                if (settings.getConfirmationTracker() != null) {
                    for (String hash : flowTxHashes) {
                        settings.getConfirmationTracker().stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(scheduler.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (CancellationException cancellation) {
                return cancelledFlowResult(flow, resultBuilder, hooks, cancellation);
            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                if (e.isRequiresFlowRestart()) {
                    try {
                        previousConfirmedSteps = findStillConfirmedSteps(
                                e.getStep().getId(), steps, attemptTxHashes, attemptStepResults,
                                cancelCheck, settings);
                    } catch (CancellationException cancellation) {
                        resultBuilder.withStepResults(attemptStepResults);
                        return cancelledFlowResult(
                                flow, resultBuilder, hooks, cancellation);
                    }
                    flowRestartAttempts++;
                    if (flowRestartAttempts > maxRollbackRetries) {
                        log.error("Flow restart limit ({}) reached after rollback at step '{}'",
                                maxRollbackRetries, e.getStep().getId());
                        resultBuilder.withStepResults(
                                withRolledBackAttemptFailure(attemptStepResults, e));
                        resultBuilder.completedAt(scheduler.now());
                        FlowResult failedResult = resultBuilder.failure(
                                new FlowExecutionException("Flow restart limit reached after rollback", e));
                        notifyFlowTerminal(flow, failedResult);
                        hooks.onFlowFailed(flow, FlowStatus.FAILED);
                        return failedResult;
                    }

                    log.info("Restarting flow (attempt {}/{}) due to rollback at step '{}'",
                            flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                    listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                            "Rollback detected at step '" + e.getStep().getId() + "'");
                    waitForBackendReadyAfterRollback(rollbackCleanupHashes(flowTxHashes, e.getTxHash()),
                            cancelCheck, settings);
                    stepRollbackAttempts.clear();
                    hooks.onFlowRestarting();
                    continue;
                } else {
                    log.error("Step rebuild failed for step '{}'", e.getStep().getId());
                    resultBuilder.withStepResults(
                            withRolledBackAttemptFailure(attemptStepResults, e));
                    resultBuilder.completedAt(scheduler.now());
                    FlowResult failedResult = resultBuilder.failure(e);
                    notifyFlowTerminal(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }
            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.completedAt(scheduler.now());
                FlowResult failedResult = resultBuilder.failure(e);
                notifyFlowTerminal(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(scheduler.now())
                .completedAt(scheduler.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("Flow execution failed: exceeded maximum restart attempts"));
        notifyFlowTerminal(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Get the maximum rollback retries from configuration.
     */
    private int getMaxRollbackRetries(EffectiveFlowExecutionSettings settings) {
        return settings.getConfirmationConfig() != null ? settings.getConfirmationConfig().getMaxRollbackRetries() : 3;
    }

    /**
     * Get the confirmation timeout from configuration or use default.
     */
    private Duration getConfirmationTimeout(EffectiveFlowExecutionSettings settings) {
        return settings.getConfirmationConfig() != null ? settings.getConfirmationConfig().getTimeout() : DEFAULT_CONFIRMATION_TIMEOUT;
    }

    /**
     * Get the check interval from configuration or use default.
     */
    private Duration getCheckInterval(EffectiveFlowExecutionSettings settings) {
        return settings.getConfirmationConfig() != null ? settings.getConfirmationConfig().getCheckInterval() : DEFAULT_CHECK_INTERVAL;
    }

    /**
     * Convert every non-confirmed sequential confirmation outcome into a terminal
     * flow result. Sequential execution may advance only after confirmation.
     */
    private FlowResult terminalConfirmationResult(
            TxFlow flow, FlowStep step, FlowStepResult submittedStep,
            ConfirmationOutcome confirmation,
            FlowResult.Builder resultBuilder, ExecutionHooks hooks) {
        String txHash = submittedStep.getTransactionHash();
        Throwable error = confirmationFailure(step, txHash, confirmation);
        FlowStepResult terminalStep = confirmationStepResult(
                submittedStep, confirmation, error);
        resultBuilder.addStepResult(terminalStep);
        return finishTerminalConfirmation(
                flow, step, terminalStep, confirmation, error, resultBuilder, hooks);
    }

    private FlowResult finishTerminalConfirmation(
            TxFlow flow, FlowStep step, FlowStepResult terminalStep,
            ConfirmationOutcome confirmation, Throwable error,
            FlowResult.Builder resultBuilder, ExecutionHooks hooks) {
        if (confirmation.isRolledBack()) {
            long previousBlockHeight = ((RollbackException) error).getPreviousBlockHeight();
            hooks.onRollbackDetected(flow, step, terminalStep.getTransactionHash(),
                    previousBlockHeight, error.getMessage());
        }

        if (confirmation.getType() == ConfirmationOutcome.Type.CANCELLED) {
            return cancelledFlowResult(flow, resultBuilder, hooks, error);
        }

        notifyStepTerminal(step, terminalStep);
        FlowResult terminal = resultBuilder.withStatus(FlowStatus.FAILED)
                .withError(error)
                .completedAt(scheduler.now())
                .build();
        notifyFlowTerminal(flow, terminal);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return terminal;
    }

    /**
     * Routes a step's terminal notification honestly: a submission-pending step (uncertain
     * disposition — the transaction may still confirm) is reported through
     * {@link FlowListener#onStepUncertain}, never {@link FlowListener#onStepFailed}. A
     * failure callback for a payment that may have landed invites the exact retry-and-
     * double-pay response the pending status exists to prevent.
     */
    /**
     * Routes the flow-level terminal notification honestly. A flow whose failure is an
     * uncertain transaction disposition (confirmation timeout, or reconciliation that
     * stayed uncertain) is reported through {@link FlowListener#onFlowUncertain} — the
     * submitted transaction may still confirm, and a failure callback would invite
     * refund/retry handling for a payment that may have landed. Everything else keeps
     * {@link FlowListener#onFlowFailed}.
     */
    private void notifyFlowTerminal(TxFlow flow, FlowResult result) {
        if (isUncertainFlowError(result.getError())) {
            listener.onFlowUncertain(flow, result);
        } else {
            listener.onFlowFailed(flow, result);
        }
    }

    /** Whether the failure's cause chain marks an uncertain transaction disposition. */
    private static boolean isUncertainFlowError(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConfirmationTimeoutException
                    || current instanceof ReconciliationUncertainException) {
                return true;
            }
            if (current.getCause() == current) break;
        }
        return false;
    }

    private void notifyStepTerminal(FlowStep step, FlowStepResult result) {
        if (result.getStatus() == FlowStatus.IN_PROGRESS) {
            listener.onStepUncertain(step, result);
        } else {
            listener.onStepFailed(step, result);
        }
    }

    private FlowStepResult confirmationStepResult(
            FlowStepResult submittedStep, ConfirmationOutcome confirmation,
            Throwable error) {
        if (isUncertainDisposition(confirmation)) {
            return FlowStepResult.submissionPendingAt(
                    submittedStep.getStepId(), submittedStep.getTransactionHash(),
                    submittedStep.getOutputUtxos(), submittedStep.getSpentInputs(),
                    error, scheduler.now());
        }
        return failedAfterSubmission(submittedStep, error);
    }

    /**
     * Whether the confirmation outcome leaves the submitted transaction's disposition
     * genuinely unknown. A cancelled or timed-out wait, and a wait whose reconciliation
     * stayed uncertain, all mean the transaction was submitted and may still confirm —
     * so the step must settle as submission-pending ({@code IN_PROGRESS} with the hash
     * retained), never as {@code FAILED}. A {@code FAILED} here would invite a retry of a
     * payment that can still land, which is exactly the double-payment this contract
     * exists to prevent. Only a proven rollback or a conclusive failure may fail the step.
     */
    private static boolean isUncertainDisposition(ConfirmationOutcome confirmation) {
        return confirmation.getType() == ConfirmationOutcome.Type.CANCELLED
                || confirmation.getType() == ConfirmationOutcome.Type.TIMEOUT
                || confirmation.getType() == ConfirmationOutcome.Type.RECOVERY_REQUIRED
                // Some rollback policies preserve the ROLLED_BACK outcome so rollback
                // persistence and hooks still run, while carrying reconciliation uncertainty
                // in the error chain (for example, an exhausted same-hash reinclusion window).
                // That remains an uncertain disposition: the transaction may still reappear,
                // so the step must not emit a failure signal.
                || isUncertainFlowError(confirmation.getError());
    }

    private FlowResult cancelledFlowResult(
            TxFlow flow, FlowResult.Builder resultBuilder,
            ExecutionHooks hooks, Throwable cause) {
        Throwable cancellation = cause != null ? cause
                : new CancellationException("Flow execution cancelled");
        FlowResult result = resultBuilder.withStatus(FlowStatus.CANCELLED)
                .withError(cancellation)
                .completedAt(scheduler.now())
                .build();
        notifyFlowTerminal(flow, result);
        hooks.onFlowFailed(flow, FlowStatus.CANCELLED);
        return result;
    }

    private boolean isCancellationProjection(FlowStepResult result) {
        return result.getStatus() == FlowStatus.CANCELLED
                || result.getStatus() == FlowStatus.IN_PROGRESS
                && result.getError() instanceof CancellationException;
    }

    private Throwable confirmationFailure(
            FlowStep step, String txHash, ConfirmationOutcome confirmation) {
        Throwable error = confirmation.getError() != null
                ? confirmation.getError()
                : new FlowExecutionException("Confirmation failed for transaction " + txHash);
        if (!confirmation.isRolledBack()) return error;

        ConfirmationResult rollback = confirmation.getResult();
        long previousBlockHeight = rollback != null && rollback.getBlockHeight() != null
                ? rollback.getBlockHeight() : 0;
        return new RollbackException(
                "Transaction " + txHash + " rolled back at step '" + step.getId() + "'",
                error, txHash, step, previousBlockHeight, false);
    }

    private FlowStepResult failedAfterSubmission(
            FlowStepResult submittedStep, Throwable failure) {
        return FlowStepResult.failureAfterSubmissionAt(
                submittedStep.getStepId(), submittedStep.getTransactionHash(),
                submittedStep.getOutputUtxos(), submittedStep.getSpentInputs(),
                failure, scheduler.now());
    }

    /**
     * Replaces only the exact transaction attempt proven rolled back. Confirmed
     * prior steps and other attempts of the same logical step remain unchanged.
     */
    private List<FlowStepResult> withRolledBackAttemptFailure(
            List<FlowStepResult> results, RollbackException rollback) {
        List<FlowStepResult> projected = new ArrayList<>(results.size() + 1);
        boolean matched = false;
        for (FlowStepResult result : results) {
            if (rollback.getTxHash() != null
                    && Objects.equals(rollback.getTxHash(), result.getTransactionHash())) {
                projected.add(failedAfterSubmission(result, rollback));
                matched = true;
            } else {
                projected.add(result);
            }
        }
        if (!matched && rollback.getTxHash() != null) {
            projected.add(FlowStepResult.failureAfterSubmissionAt(
                    rollback.getStep().getId(), rollback.getTxHash(),
                    List.of(), List.of(), rollback, scheduler.now()));
        }
        return projected;
    }

    /**
     * Execute a step with rollback handling.
     * <p>
     * This method wraps executeStepWithRetry and handles RollbackException for
     * REBUILD_FROM_FAILED strategy by rebuilding and resubmitting the step.
     * <p>
     * @throws RollbackException when using REBUILD_ENTIRE_FLOW strategy or when
     *         rebuild attempts are exhausted for REBUILD_FROM_FAILED
     */
    private FlowStepResult executeStepWithRollbackHandling(FlowStep step, FlowExecutionContext context,
                                                            java.util.Map<String, Object> variables,
                                                            boolean pipelined,
                                                            Map<String, Integer> stepRollbackAttempts,
                                                            int maxRollbackRetries,
                                                            BooleanSupplier cancelCheck,
                                                            EffectiveFlowExecutionSettings settings) {
        while (true) {
            if (cancelCheck.getAsBoolean()) {
                return FlowStepResult.cancelledAt(step.getId(),
                        new CancellationException("Flow cancelled"), scheduler.now());
            }

            try {
                return executeStepWithRetry(step, context, variables, pipelined, cancelCheck, settings);
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

                waitForBackendReadyAfterRollback(List.of(e.getTxHash()), cancelCheck, settings); // Wait for backend to sync after rollback

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
     * On restart after rollback, steps whose transactions are still confirmed on-chain
     * are skipped to avoid unnecessary rebuilding.
     */
    private FlowResult doExecutePipelined(TxFlow flow, ExecutionHooks hooks,
                                          Map<Integer, FlowStepResult> initialConfirmedSteps,
                                          EffectiveFlowExecutionSettings settings,
                                          ExecutionOrigin origin) {
        int maxRollbackRetries = getMaxRollbackRetries(settings);
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>(initialConfirmedSteps);
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;
        String executionId = java.util.UUID.randomUUID().toString();
        log.info("Starting TxFlow execution {} for definition {}", executionId, flow.getId());

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), executionId, flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(scheduler.now());
            FlowHorizonMonitor liveMonitor = createLiveAttemptMonitor(settings);

            if (flowRestartAttempts == 0) {
                notifyFlowStarting(flow, hooks, origin);
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();
            List<String> submittedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();

            // Determine which steps to skip (still confirmed from previous attempt)
            Set<Integer> skippedStepIndices;
            try {
                skippedStepIndices = previousConfirmedSteps.isEmpty()
                        ? Set.of()
                        : verifyRetainedSteps(
                                previousConfirmedSteps, steps, cancelCheck, settings);
            } catch (CancellationException cancellation) {
                resultBuilder.withStepResults(orderedStepResults(previousConfirmedSteps));
                return cancelledFlowResult(flow, resultBuilder, hooks, cancellation);
            }
            Set<Integer> submittedStepIndices = new HashSet<>();
            Set<Integer> confirmedStepIndices = new HashSet<>(skippedStepIndices);

            try {
                // Phase 1: Build and submit all transactions without waiting
                log.info("PIPELINED mode: Submitting {} transactions (skipping {} confirmed)",
                        totalSteps, skippedStepIndices.size());
                for (int i = 0; i < totalSteps; i++) {
                    // Check for cancellation
                    if (hooks.isCancelled()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, hooks, null);
                    }

                    FlowStep step = steps.get(i);
                    hooks.onStepStarting(step);
                    listener.onStepStarted(step, i, totalSteps);

                    if (skippedStepIndices.contains(i)) {
                        // Reuse previous result
                        FlowStepResult prevResult = previousConfirmedSteps.get(i);
                        stepResults.add(prevResult);
                        submittedTxHashes.add(prevResult.getTransactionHash());
                        recordRetainedStep(
                                step, prevResult, context, resultBuilder, flowTxHashes, liveMonitor, hooks);
                        continue;
                    }

                    verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);
                    FlowStepResult stepResult = executeStepWithRetry(step, context, flow.getVariables(), true,
                            cancelCheck, settings);
                    stepResults.add(stepResult);

                    if (stepResult.isSuccessful()) {
                        submittedStepIndices.add(i);
                        submittedTxHashes.add(stepResult.getTransactionHash());
                        flowTxHashes.add(stepResult.getTransactionHash());
                        listener.onTransactionSubmitted(step, stepResult.getTransactionHash());
                        hooks.onTransactionSubmitted(flow, step, stepResult.getTransactionHash());
                        log.debug("Step '{}' submitted: {}", step.getId(), stepResult.getTransactionHash());
                    } else {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        if (isCancellationProjection(stepResult)) {
                            return cancelledFlowResult(
                                    flow, resultBuilder, hooks, stepResult.getError());
                        }
                        notifyStepTerminal(step, stepResult);
                        resultBuilder.completedAt(scheduler.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        notifyFlowTerminal(flow, failedResult);
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
                        // Already confirmed and completed when it was retained.
                        continue;
                    }

                    verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);
                    ConfirmationOutcome confirmation = waitForConfirmation(txHash, step, cancelCheck, settings);
                    if (confirmation.isConfirmed()) {
                        confirmedStepIndices.add(i);
                        ConfirmationResult result = confirmation.getResult();
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        hooks.onTransactionConfirmed(flow, step, txHash, result);
                        if (liveMonitor != null) liveMonitor.track(step, txHash);
                        log.debug("Step '{}' confirmed: {} at block {}", step.getId(), txHash,
                                result.getBlockHeight());
                    } else {
                        Throwable error = confirmationFailure(step, txHash, confirmation);
                        FlowStepResult terminalStep = confirmationStepResult(
                                stepResults.get(i), confirmation, error);
                        stepResults.set(i, terminalStep);
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return finishTerminalConfirmation(
                                flow, step, terminalStep, confirmation, error,
                                resultBuilder, hooks);
                    }
                }

                verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);

                // Cleanup tracked transactions to prevent memory leak
                if (settings.getConfirmationTracker() != null) {
                    for (String hash : flowTxHashes) {
                        settings.getConfirmationTracker().stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(scheduler.now());
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (CancellationException cancellation) {
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                return cancelledFlowResult(flow, resultBuilder, hooks, cancellation);
            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    log.error("Flow restart limit ({}) reached after rollback at step '{}' in PIPELINED mode",
                            maxRollbackRetries, e.getStep().getId());
                    resultBuilder.withStepResults(withRolledBackAttemptFailure(
                            observableStepResults(stepResults, submittedStepIndices,
                                    confirmedStepIndices), e));
                    resultBuilder.completedAt(scheduler.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in PIPELINED mode", e));
                    notifyFlowTerminal(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }

                log.info("Restarting PIPELINED flow (attempt {}/{}) due to rollback at step '{}'",
                        flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                        "Rollback detected at step '" + e.getStep().getId() + "' in PIPELINED mode");

                // Find steps that are still confirmed before clearing tracker
                try {
                    previousConfirmedSteps = findStillConfirmedSteps(
                            e.getStep().getId(), steps, submittedTxHashes, stepResults,
                            cancelCheck, settings);
                } catch (CancellationException cancellation) {
                    resultBuilder.withStepResults(observableStepResults(
                            stepResults, submittedStepIndices, confirmedStepIndices));
                    return cancelledFlowResult(
                            flow, resultBuilder, hooks, cancellation);
                }

                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck, settings);
                hooks.onFlowRestarting();
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                resultBuilder.completedAt(scheduler.now());
                FlowResult failedResult = resultBuilder.failure(e);
                notifyFlowTerminal(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(scheduler.now())
                .completedAt(scheduler.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("PIPELINED flow execution failed: exceeded maximum restart attempts"));
        notifyFlowTerminal(flow, failedResult);
        hooks.onFlowFailed(flow, FlowStatus.FAILED);
        return failedResult;
    }

    /**
     * Find steps whose transactions are still confirmed on-chain after a rollback.
     * Used by PIPELINED mode to skip already-confirmed steps on restart.
     */
    private Map<Integer, FlowStepResult> findStillConfirmedSteps(
            String rolledBackStepId, List<FlowStep> steps,
            List<String> txHashes, List<FlowStepResult> results,
            BooleanSupplier cancelCheck, EffectiveFlowExecutionSettings settings) {
        Set<String> invalidated = new RollbackCoordinator()
                .invalidatedLiveClosure(rolledBackStepId, results);
        Map<Integer, FlowStepResult> candidates = new HashMap<>();
        for (int i = 0; i < Math.min(txHashes.size(), results.size()); i++) {
            String txHash = txHashes.get(i);
            FlowStepResult result = results.get(i);
            if (txHash != null && !invalidated.contains(result.getStepId())) {
                candidates.put(i, result);
            }
        }
        Set<Integer> retained = reconcilePreviouslyConfirmed(candidates, cancelCheck, settings);
        Map<Integer, FlowStepResult> confirmed = new HashMap<>();
        for (Map.Entry<Integer, FlowStepResult> candidate : candidates.entrySet()) {
            int index = candidate.getKey();
            if (retained.contains(index)) {
                confirmed.put(index, candidate.getValue());
                log.debug("Step '{}' tx {} remains confirmed after rollback reconciliation",
                        steps.get(index).getId(), candidate.getValue().getTransactionHash());
            } else {
                log.info("Step '{}' tx {} is authoritatively rolled back and will be rebuilt",
                        steps.get(index).getId(), candidate.getValue().getTransactionHash());
            }
        }
        return confirmed;
    }

    /**
     * Verify previously confirmed steps are still on-chain. Retained results are
     * recorded exactly once when their position is reached in the execution loop.
     */
    private Set<Integer> verifyRetainedSteps(
            Map<Integer, FlowStepResult> previousConfirmedSteps,
            List<FlowStep> steps,
            BooleanSupplier cancelCheck, EffectiveFlowExecutionSettings settings) {
        Set<Integer> verified = reconcilePreviouslyConfirmed(
                previousConfirmedSteps, cancelCheck, settings);
        for (Map.Entry<Integer, FlowStepResult> entry : previousConfirmedSteps.entrySet()) {
            int idx = entry.getKey();
            FlowStepResult prevResult = entry.getValue();
            if (!verified.contains(idx)) {
                log.info("Previously confirmed step '{}' tx {} is authoritatively rolled back and will be rebuilt",
                        steps.get(idx).getId(), prevResult.getTransactionHash());
            }
        }
        return verified;
    }

    /**
     * Reconcile transactions that this execution previously observed as confirmed.
     * A shallow or temporarily unobservable transaction is never interpreted as a
     * rollback. Only repeated absence from a backend that explicitly advertises
     * authoritative absence permits rebuilding. Each poll reads the chain tip at
     * most once, regardless of the number of transactions being reconciled.
     */
    private Set<Integer> reconcilePreviouslyConfirmed(
            Map<Integer, FlowStepResult> candidates, BooleanSupplier cancelCheck,
            EffectiveFlowExecutionSettings settings) {
        if (candidates.isEmpty()) return Set.of();
        int minimumDepth = settings.getConfirmationConfig() != null
                ? settings.getConfirmationConfig().getMinConfirmations() : 0;
        int requiredAbsences = settings.getConfirmationConfig() != null
                ? settings.getConfirmationConfig().getRequiredAuthoritativeAbsences() : 1;
        boolean authoritativeAbsence = chainDataSupplier instanceof TransactionObservationCapabilities
                && ((TransactionObservationCapabilities) chainDataSupplier).supportsAuthoritativeAbsence();
        Instant deadline = scheduler.now().plus(getConfirmationTimeout(settings));
        Set<Integer> pending = new HashSet<>(candidates.keySet());
        Set<Integer> retained = new HashSet<>();
        Map<Integer, Integer> consistentAbsences = new HashMap<>();
        Map<Integer, Throwable> lastErrors = new HashMap<>();
        Map<Integer, Long> recordedInclusionHeights = new HashMap<>();

        while (!pending.isEmpty() && scheduler.now().isBefore(deadline)) {
            if (cancelCheck.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException(
                        "Flow cancelled while reconciling previously confirmed transactions");
            }
            if (settings.getConfirmationTracker() != null) {
                List<Integer> observedIndices = new ArrayList<>(pending);
                List<String> hashes = observedIndices.stream()
                        .map(index -> candidates.get(index).getTransactionHash()).toList();
                Map<String, ConfirmationResult> observations =
                        settings.getConfirmationTracker().checkStatuses(hashes);
                for (int index : observedIndices) {
                    String txHash = candidates.get(index).getTransactionHash();
                    ConfirmationResult observation = observations.get(txHash);
                    if (observation == null) continue;
                    if (observation.getError() != null) {
                        lastErrors.put(index, observation.getError());
                    }
                    if (observation.isRolledBack()) {
                        pending.remove(index);
                    } else if (observation.hasReached(ConfirmationStatus.CONFIRMED)) {
                        retained.add(index);
                        pending.remove(index);
                    }
                }
            } else {
                Long chainTip = null;
                try {
                    chainTip = chainDataSupplier.getChainTipHeight();
                } catch (Exception e) {
                    for (int index : pending) lastErrors.put(index, e);
                    log.debug("Could not read chain tip during transaction reconciliation: {}",
                            e.getMessage());
                }
                for (int index : new ArrayList<>(pending)) {
                    String txHash = candidates.get(index).getTransactionHash();
                    try {
                        Optional<TransactionInfo> txInfo = chainDataSupplier.getTransactionInfo(txHash);
                        if (txInfo.isPresent() && txInfo.get().getBlockHeight() != null) {
                            long inclusionHeight = txInfo.get().getBlockHeight();
                            recordedInclusionHeights.put(index, inclusionHeight);
                            consistentAbsences.remove(index);
                            if (minimumDepth == 0
                                    || chainTip != null && chainTip - inclusionHeight >= minimumDepth) {
                                retained.add(index);
                                pending.remove(index);
                            }
                        } else {
                            Long inclusionHeight = recordedInclusionHeights.get(index);
                            if (authoritativeAbsence && chainTip != null
                                    && inclusionHeight != null && chainTip >= inclusionHeight) {
                                int count = consistentAbsences.merge(index, 1, Integer::sum);
                                if (count >= requiredAbsences) pending.remove(index);
                            } else {
                                consistentAbsences.remove(index);
                            }
                        }
                    } catch (Exception e) {
                        lastErrors.put(index, e);
                        consistentAbsences.remove(index);
                        log.debug("Transaction {} reconciliation observation failed: {}",
                                txHash, e.getMessage());
                    }
                }
            }

            if (pending.isEmpty()) break;
            if (settings.getConfirmationConfig() == null) {
                int uncertainIndex = pending.iterator().next();
                String txHash = candidates.get(uncertainIndex).getTransactionHash();
                Throwable lastError = lastErrors.get(uncertainIndex);
                throw lastError != null
                        ? new ReconciliationUncertainException(txHash, lastError)
                        : new ReconciliationUncertainException(txHash);
            }
            try {
                scheduler.sleep(getCheckInterval(settings), cancelCheck);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CancellationException cancellation = new CancellationException(
                        "Interrupted while reconciling previously confirmed transactions");
                cancellation.initCause(e);
                throw cancellation;
            }
        }

        if (!pending.isEmpty()) {
            int uncertainIndex = pending.iterator().next();
            String txHash = candidates.get(uncertainIndex).getTransactionHash();
            Throwable lastError = lastErrors.get(uncertainIndex);
            throw lastError != null
                    ? new ReconciliationUncertainException(txHash, lastError)
                    : new ReconciliationUncertainException(txHash);
        }
        return retained;
    }

    /**
     * Wait for a transaction to be confirmed on-chain.
     *
     * @param txHash the transaction hash to wait for
     * @return Optional containing ConfirmationResult if confirmed, empty if timeout or failure
     */
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
    private ConfirmationOutcome waitForConfirmation(String txHash, FlowStep step,
                                                      BooleanSupplier cancelCheck,
                                                      EffectiveFlowExecutionSettings settings) {
        // Use enhanced confirmation tracking if configured
        if (settings.getConfirmationTracker() != null) {
            return waitForConfirmationWithTracking(txHash, step, cancelCheck, settings);
        }

        // Fall back to simple confirmation checking
        Instant deadline = scheduler.now().plus(getConfirmationTimeout(settings));

        while (scheduler.now().isBefore(deadline)) {
            if (cancelCheck.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return ConfirmationOutcome.cancelled(null);
            }
            try {
                Optional<TransactionInfo> txInfo = chainDataSupplier.getTransactionInfo(txHash);
                if (txInfo.isPresent()) {
                    // Build a minimal ConfirmationResult for simple polling mode
                    // Block height is available from the transaction response
                    return ConfirmationOutcome.confirmed(ConfirmationResult.builder()
                            .txHash(txHash)
                            .status(ConfirmationStatus.CONFIRMED)
                            .blockHeight(txInfo.get().getBlockHeight())
                            .blockHash(txInfo.get().getBlockHash())
                            .confirmationDepth(0)  // Not tracked in simple mode
                            .build());
                }
                scheduler.sleep(getCheckInterval(settings), cancelCheck);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ConfirmationOutcome.cancelled(null);
            } catch (Exception e) {
                log.debug("Waiting for tx confirmation: {}", txHash);
                try {
                    scheduler.sleep(getCheckInterval(settings), cancelCheck);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ConfirmationOutcome.cancelled(null);
                }
            }
        }
        return ConfirmationOutcome.timeout(txHash, null);
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
    private ConfirmationOutcome waitForConfirmationWithTracking(String txHash, FlowStep step,
                                                                 BooleanSupplier cancelCheck,
                                                                 EffectiveFlowExecutionSettings settings) {
        ConfirmationStatus targetStatus = ConfirmationStatus.CONFIRMED;

        // Track the last known status for detecting transitions
        final ConfirmationStatus[] lastStatus = {null};
        final Long[] firstBlockHeight = {null};
        final boolean[] rollbackSuspected = {false};
        int notifyOnlyRepolls = 0;
        BiConsumer<String, ConfirmationResult> progressListener = (hash, confirmResult) -> {
            if (step == null) return;

            ConfirmationStatus currentStatus = confirmResult.getStatus();
            int depth = confirmResult.getConfirmationDepth();

            if (currentStatus == ConfirmationStatus.SUBMITTED
                    && firstBlockHeight[0] != null
                    && !rollbackSuspected[0]) {
                rollbackSuspected[0] = true;
                listener.onTransactionRollbackSuspected(
                        step, txHash, firstBlockHeight[0]);
            } else if (currentStatus == ConfirmationStatus.IN_BLOCK
                    || currentStatus == ConfirmationStatus.CONFIRMED) {
                rollbackSuspected[0] = false;
            }

            if (lastStatus[0] != currentStatus) {
                if ((currentStatus == ConfirmationStatus.IN_BLOCK
                        || currentStatus == ConfirmationStatus.CONFIRMED)
                        && lastStatus[0] != ConfirmationStatus.IN_BLOCK
                        && lastStatus[0] != ConfirmationStatus.CONFIRMED) {
                    firstBlockHeight[0] = confirmResult.getBlockHeight();
                    if (confirmResult.getBlockHeight() != null) {
                        persistencePort.onInBlock(step, txHash, confirmResult.getBlockHeight());
                        listener.onTransactionInBlock(step, txHash, confirmResult.getBlockHeight());
                    }
                }
                lastStatus[0] = currentStatus;
            }

            persistencePort.onConfirmationDepth(step, txHash, depth);
            listener.onConfirmationDepthChanged(step, txHash, depth, currentStatus);
        };

        while (true) {
            if (cancelCheck.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return ConfirmationOutcome.cancelled(null);
            }
            ConfirmationResult result = settings.getConfirmationTracker().waitForConfirmation(
                    txHash, targetStatus, progressListener, cancelCheck);

            // Handle rollback
            if (result.isRolledBack()) {
                long prevHeight = firstBlockHeight[0] != null ? firstBlockHeight[0] : 0;

                if (step != null) {
                    persistencePort.onRolledBack(step, txHash, prevHeight);
                    listener.onTransactionRolledBack(step, txHash, prevHeight);
                }

                switch (settings.getRollbackStrategy()) {
                    case FAIL_IMMEDIATELY:
                        log.warn("Transaction {} rolled back, failing flow (FAIL_IMMEDIATELY strategy)", txHash);
                        return ConfirmationOutcome.rolledBack(result);

                    case NOTIFY_ONLY:
                        RollbackPolicy portablePolicy = settings.getPortableRollbackPolicy();
                        if (portablePolicy != null
                                && portablePolicy.action()
                                == com.bloxbean.cardano.client.txflow.config.RollbackAction.WAIT_FOR_REINCLUSION) {
                            Instant reinclusionDeadline = scheduler.now().plus(
                                    portablePolicy.reinclusionWindow());
                            log.warn("Transaction {} rolled back; waiting {} for same-hash re-inclusion",
                                    txHash, portablePolicy.reinclusionWindow());
                            ConfirmationResult reinclusion = settings.getConfirmationTracker()
                                    .waitForConfirmation(txHash, targetStatus, progressListener,
                                            cancelCheck, reinclusionDeadline, true);
                            if (reinclusion.hasReached(targetStatus)) {
                                return ConfirmationOutcome.confirmed(reinclusion);
                            }
                            if (cancelCheck.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                                return ConfirmationOutcome.cancelled(reinclusion);
                            }
                            Throwable exhausted = new ReconciliationUncertainException(
                                    txHash, new FlowExecutionException(
                                    "Transaction " + txHash + " was not re-included within "
                                            + portablePolicy.reinclusionWindow()));
                            return ConfirmationOutcome.rolledBack(ConfirmationResult.rolledBack(
                                    txHash, prevHeight, reinclusion.getCurrentTipHeight(), exhausted));
                        }
                        notifyOnlyRepolls++;
                        if (notifyOnlyRepolls > getMaxRollbackRetries(settings)) {
                            log.warn("NOTIFY_ONLY re-poll limit ({}) reached for tx {}",
                                    getMaxRollbackRetries(settings), txHash);
                            return ConfirmationOutcome.rolledBack(result);
                        }
                        // Preserve the original inclusion while waiting for same-hash re-inclusion.
                        // Clearing the tracker here would turn the next authoritative absence into
                        // SUBMITTED and eventually misreport exhausted rollback recovery as a timeout.
                        log.warn("Transaction {} rolled back, re-entering confirmation polling (NOTIFY_ONLY strategy, attempt {}/{})",
                                txHash, notifyOnlyRepolls, getMaxRollbackRetries(settings));
                        try {
                            if (!scheduler.sleep(getCheckInterval(settings), cancelCheck)) {
                                return ConfirmationOutcome.cancelled(result);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return ConfirmationOutcome.cancelled(result);
                        }
                        continue;  // re-enter the while loop to poll again

                    case REBUILD_FROM_FAILED:
                        log.info("Transaction {} rolled back, will rebuild step (REBUILD_FROM_FAILED strategy)", txHash);
                        if (step != null) {
                            throw RollbackException.forStepRebuild(txHash, step, prevHeight);
                        }
                        return ConfirmationOutcome.rolledBack(result);

                    case REBUILD_ENTIRE_FLOW:
                        log.info("Transaction {} rolled back, will restart flow (REBUILD_ENTIRE_FLOW strategy)", txHash);
                        if (step != null) {
                            throw RollbackException.forFlowRestart(txHash, step, prevHeight);
                        }
                        return ConfirmationOutcome.rolledBack(result);
                }
            }

            // Check if target status was reached
            if (result.hasReached(targetStatus)) {
                return ConfirmationOutcome.confirmed(result);
            }

            // Timeout or other failure — exit
            if (cancelCheck.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return ConfirmationOutcome.cancelled(result);
            }
            if (result.getError() instanceof ConfirmationTimeoutException) {
                log.warn("Confirmation wait timed out for tx {}", txHash);
                return ConfirmationOutcome.timeout(txHash, result);
            }
            if (result.getError() instanceof ReconciliationUncertainException) {
                log.warn("Confirmation reconciliation remains uncertain for tx {}", txHash);
                return ConfirmationOutcome.recoveryRequired(txHash, result);
            }
            if (result.getError() != null) {
                log.warn("Confirmation wait failed for tx {}: {}", txHash, result.getError().getMessage());
                return ConfirmationOutcome.failed(result, result.getError());
            }
            return ConfirmationOutcome.failed(result,
                    new FlowExecutionException("Confirmation wait ended without a terminal outcome for " + txHash));
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
                    scheduler.sleep(Duration.ofMillis(retryDelayMs), cancelCheck);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        log.warn("Backend may not be fully ready after {} attempts", maxAttempts);
    }

    private List<String> rollbackCleanupHashes(List<String> flowTxHashes, String rollbackTxHash) {
        List<String> hashes = new ArrayList<>(flowTxHashes);
        if (rollbackTxHash != null && !hashes.contains(rollbackTxHash)) {
            hashes.add(rollbackTxHash);
        }
        return hashes;
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
    private void waitForBackendReadyAfterRollback(List<String> flowTxHashes, BooleanSupplier cancelCheck,
                                                   EffectiveFlowExecutionSettings settings) {
        // Clear only this flow's tracked transactions (scoped, not global)
        if (settings.getConfirmationTracker() != null) {
            log.debug("Clearing confirmation tracker state for {} flow transactions after rollback", flowTxHashes.size());
            for (String txHash : flowTxHashes) {
                settings.getConfirmationTracker().stopTracking(txHash);
            }
        }

        // Skip wait logic if not configured (production default)
        if (settings.getConfirmationConfig() == null || !settings.getConfirmationConfig().isWaitForBackendAfterRollback()) {
            log.debug("Skipping backend wait (not configured for post-rollback wait)");
            return;
        }

        // Wait for backend ready (for test scenarios like Yaci DevKit)
        long retryDelayMs = settings.getConfirmationConfig().getCheckInterval().toMillis();
        int maxAttempts = settings.getConfirmationConfig().getPostRollbackWaitAttempts();
        waitForBackendReady(maxAttempts, retryDelayMs, cancelCheck);

        // Optional additional delay for UTXO indexer sync
        if (cancelCheck.getAsBoolean()) {
            return;
        }
        Duration utxoSyncDelay = settings.getConfirmationConfig().getPostRollbackUtxoSyncDelay();
        if (utxoSyncDelay != null && !utxoSyncDelay.isZero()) {
            try {
                log.debug("Waiting {}ms for UTXO indexer to sync", utxoSyncDelay.toMillis());
                scheduler.sleep(utxoSyncDelay, cancelCheck);
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
        EffectiveFlowExecutionSettings settings = effectiveSettings(flow);
        validateConfiguration(settings);
        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }
        validateExecutableFlow(flow);
        Executor asyncExecutor = requireAsyncExecutor();

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
                FlowResult result = executeWithHandle(flow, handle, settings);
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

        asyncExecutor.execute(task);

        return handle;
    }

    private FlowResult executeWithHandle(TxFlow flow, FlowHandle handle,
                                         EffectiveFlowExecutionSettings settings) {
        ExecutionHooks hooks = handleHooks(flow, handle);
        return ChainingStrategy.forMode(settings.getChainingMode()).execute(
                () -> doExecuteSequential(flow, hooks, Map.of(), settings, ExecutionOrigin.FRESH),
                () -> doExecutePipelined(flow, hooks, Map.of(), settings, ExecutionOrigin.FRESH),
                () -> doExecuteBatch(flow, hooks, Map.of(), settings, ExecutionOrigin.FRESH));
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
                                                 BooleanSupplier cancelCheck,
                                                 EffectiveFlowExecutionSettings settings) {
        // Determine retry policy (step-level overrides default)
        RetryPolicy policy = step.hasRetryPolicy() ? step.getRetryPolicy() : settings.getDefaultRetryPolicy();
        return new StepRunner(scheduler, listener).run(step, policy,
                () -> pipelined
                        ? executeStepPipelined(step, context, variables)
                        : executeStepSequential(step, context, variables, cancelCheck, settings),
                failure -> reconcileUncertainSubmission(step, context, failure), cancelCheck);
    }

    private FlowStepResult reconcileUncertainSubmission(FlowStep step, FlowExecutionContext context,
                                                         UncertainSubmissionException uncertain) {
        String txHash = uncertain.getTransactionHash();
        try {
            Optional<TransactionInfo> observed = chainDataSupplier.getTransactionInfo(txHash);
            if (observed.isEmpty()) {
                persistencePort.onSubmitting(step, uncertain.getTransaction());
                var resubmission = transactionProcessor.submitTransaction(uncertain.getSignedTransaction());
                if (!resubmission.isSuccessful()) {
                    FlowExecutionException rejected = new FlowExecutionException(
                            "Identical transaction resubmission failed while outcome remained uncertain: "
                                    + resubmission.getResponse(), uncertain);
                    return uncertainSubmissionFailure(step, uncertain,
                            new ReconciliationUncertainException(txHash, rejected));
                }
            }

            Transaction transaction = uncertain.getTransaction();
            persistencePort.onSubmitted(step, txHash);
            FlowStepResult recovered = FlowStepResult.success(step.getId(), txHash,
                    captureOutputUtxos(transaction, txHash), captureSpentInputs(transaction));
            context.recordStepResult(step, recovered);
            return recovered;
        } catch (Exception reconciliationFailure) {
            return uncertainSubmissionFailure(step, uncertain,
                    new ReconciliationUncertainException(txHash, reconciliationFailure));
        }
    }

    private FlowStepResult uncertainSubmissionFailure(
            FlowStep step, UncertainSubmissionException uncertain, Throwable failure) {
        Transaction transaction = uncertain.getTransaction();
        String transactionHash = uncertain.getTransactionHash();
        // Same rule as confirmation timeouts: the submission may have been accepted, so
        // this settles as submission-pending (IN_PROGRESS, hash retained), never FAILED —
        // a FAILED result here becomes a STEP_FAILED signal for a payment that may land.
        return FlowStepResult.submissionPendingAt(step.getId(), transactionHash,
                captureOutputUtxos(transaction, transactionHash),
                captureSpentInputs(transaction), failure, scheduler.now());
    }

    /**
     * Execute a single step in SEQUENTIAL mode (waits for confirmation).
     */
    private FlowStepResult executeStepSequential(FlowStep step, FlowExecutionContext context,
                                                  java.util.Map<String, Object> variables,
                                                  BooleanSupplier cancelCheck,
                                                  EffectiveFlowExecutionSettings settings) {
        String stepId = step.getId();
        log.debug("Executing step '{}'", stepId);
        Transaction[] builtTx = new Transaction[1];

        try {
            // Create appropriate UtxoSupplier based on dependencies
            UtxoSupplier utxoSupplier = createUtxoSupplier(step, context);

            // Create QuickTxBuilder with the appropriate UTXO supplier
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

            // Get TxContext from step (either from TxPlan or TxContext factory)
            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                TxPlan plan = copyPlanForExecution(step.getTxPlan(), variables, context);
                txContext = quickTxBuilder.compose(plan, signerRegistry, scriptRegistry);
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
            txContext.withTxInspector(tx -> {
                builtTx[0] = tx;
                persistencePort.onPrepared(step, tx);
                persistencePort.onSubmitting(step, tx);
                if (txInspector != null) {
                    txInspector.accept(tx);
                }
            });

            // Execute and wait for confirmation
            TxResult result = txContext.completeAndWait(getConfirmationTimeout(settings), getCheckInterval(settings),
                    msg -> log.debug("[{}] {}", stepId, msg));

            if (result.isSuccessful()) {
                String txHash = result.getValue();
                persistencePort.onSubmitted(step, txHash);
                listener.onTransactionSubmitted(step, txHash);

                // Capture outputs and spent inputs
                List<Utxo> outputUtxos = captureOutputUtxos(builtTx[0], txHash);
                List<TransactionInput> spentInputs = captureSpentInputs(builtTx[0]);

                // If confirmation tracking is configured, wait for deeper confirmation
                // This enables rollback detection in SEQUENTIAL mode
                if (settings.getConfirmationTracker() != null) {
                    ConfirmationOutcome confirmation = waitForConfirmation(txHash, step, cancelCheck, settings);
                    if (!confirmation.isConfirmed()) {
                        if (confirmation.isRolledBack()) {
                            ConfirmationResult rollback = confirmation.getResult();
                            persistTransactionRolledBack(context.getFlowId(), step, txHash,
                                    rollback.getBlockHeight(), confirmation.getError().getMessage());
                        }
                        Throwable error = confirmationFailure(step, txHash, confirmation);
                        // Same rule as the sequential path: an uncertain disposition
                        // (cancelled / timeout / reconciliation-uncertain) settles as
                        // submission-pending with the hash, never as FAILED.
                        FlowStepResult terminalStep = isUncertainDisposition(confirmation)
                                ? FlowStepResult.submissionPendingAt(
                                        stepId, txHash, outputUtxos, spentInputs,
                                        error, scheduler.now())
                                : FlowStepResult.failureAfterSubmissionAt(
                                        stepId, txHash, outputUtxos, spentInputs,
                                        error, scheduler.now());
                        context.recordStepResult(step, terminalStep);
                        return terminalStep;
                    }
                }

                listener.onTransactionConfirmed(step, txHash);

                FlowStepResult stepResult = FlowStepResult.success(stepId, txHash, outputUtxos, spentInputs);
                context.recordStepResult(step, stepResult);
                return stepResult;
            } else {
                FlowStepResult stepResult = FlowStepResult.failure(stepId,
                        new RuntimeException("Transaction failed: " + result.getResponse()));
                context.recordStepResult(step, stepResult);
                return stepResult;
            }

        } catch (RollbackException e) {
            throw e;  // Let RollbackException propagate for rollback handling
        } catch (Exception e) {
            log.error("Step '{}' failed", stepId, e);
            Throwable failure = builtTx[0] != null && hasSubmissionApiFailure(e)
                    ? new UncertainSubmissionException(builtTx[0], e)
                    : e;
            FlowStepResult stepResult = FlowStepResult.failure(stepId, failure);
            context.recordStepResult(step, stepResult);
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
        Transaction[] builtTx = new Transaction[1];

        try {
            // Create appropriate UtxoSupplier based on dependencies
            UtxoSupplier utxoSupplier = createUtxoSupplier(step, context);

            // Create QuickTxBuilder with the appropriate UTXO supplier
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

            // Get TxContext from step (either from TxPlan or TxContext factory)
            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                TxPlan plan = copyPlanForExecution(step.getTxPlan(), variables, context);
                txContext = quickTxBuilder.compose(plan, signerRegistry, scriptRegistry);
            } else if (step.hasTxContextFactory()) {
                // Call user's factory with our builder (which has chain-aware UTXO supplier)
                txContext = step.getTxContextFactory().apply(quickTxBuilder);
            } else {
                throw new FlowExecutionException("Step '" + stepId + "' has neither TxPlan nor TxContext factory");
            }

            // Store the built transaction for capturing outputs/inputs
            txContext.withTxInspector(tx -> {
                builtTx[0] = tx;
                persistencePort.onPrepared(step, tx);
                persistencePort.onSubmitting(step, tx);
                if (txInspector != null) {
                    txInspector.accept(tx);
                }
            });

            // Submit transaction without waiting for confirmation (PIPELINED mode)
            TxResult result = txContext.complete();

            if (result.isSuccessful()) {
                String txHash = result.getValue();
                persistencePort.onSubmitted(step, txHash);

                // Capture outputs and spent inputs BEFORE confirmation
                // This enables subsequent steps to use these outputs as inputs
                List<Utxo> outputUtxos = captureOutputUtxos(builtTx[0], txHash);
                List<TransactionInput> spentInputs = captureSpentInputs(builtTx[0]);

                log.debug("Step '{}' submitted (pipelined): {} with {} outputs",
                        stepId, txHash, outputUtxos.size());

                FlowStepResult stepResult = FlowStepResult.success(stepId, txHash, outputUtxos, spentInputs);
                context.recordStepResult(step, stepResult);
                return stepResult;
            } else {
                FlowStepResult stepResult = FlowStepResult.failure(stepId,
                        new RuntimeException("Transaction submission failed: " + result.getResponse()));
                context.recordStepResult(step, stepResult);
                return stepResult;
            }

        } catch (RollbackException e) {
            throw e;  // Let RollbackException propagate for rollback handling
        } catch (Exception e) {
            log.error("Step '{}' failed (pipelined)", stepId, e);
            Throwable failure = builtTx[0] != null && hasSubmissionApiFailure(e)
                    ? new UncertainSubmissionException(builtTx[0], e)
                    : e;
            FlowStepResult stepResult = FlowStepResult.failure(stepId, failure);
            context.recordStepResult(step, stepResult);
            return stepResult;
        }
    }

    static boolean hasSubmissionApiFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiRuntimeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
        Set<String> exactPendingStepIds = step.hasTxPlan()
                ? planMaterializer.referencedStepIds(step.getTxPlan())
                : Set.of();
        return new FlowUtxoSupplier(baseUtxoSupplier, context, step.getDependencies(),
                exactPendingStepIds);
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
     * Projects internal pipelined/batch dependency results onto public lifecycle
     * semantics. A submitted transaction remains {@link FlowStatus#IN_PROGRESS}
     * until confirmation; only confirmed transactions are successful. Signed
     * transactions that were merely built are intentionally absent.
     */
    private List<FlowStepResult> observableStepResults(
            List<FlowStepResult> internalResults, Set<Integer> submittedIndices,
            Set<Integer> confirmedIndices) {
        List<FlowStepResult> observable = new ArrayList<>();
        for (int index = 0; index < internalResults.size(); index++) {
            FlowStepResult result = internalResults.get(index);
            if (!result.isSuccessful()) {
                observable.add(result);
            } else if (confirmedIndices.contains(index)) {
                observable.add(result);
            } else if (submittedIndices.contains(index)) {
                observable.add(new FlowStepResult(result.getStepId(), FlowStatus.IN_PROGRESS,
                        result.getTransactionHash(), result.getOutputUtxos(),
                        result.getSpentInputs(), result.getCompletedAt()));
            }
        }
        return observable;
    }

    /** Returns retained prefix results in flow-index order for terminal projections. */
    private List<FlowStepResult> orderedStepResults(
            Map<Integer, FlowStepResult> indexedResults) {
        return indexedResults.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
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
    private FlowResult doExecuteBatch(TxFlow flow, ExecutionHooks hooks,
                                      Map<Integer, FlowStepResult> initialConfirmedSteps,
                                      EffectiveFlowExecutionSettings settings,
                                      ExecutionOrigin origin) {
        int maxRollbackRetries = getMaxRollbackRetries(settings);
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>(initialConfirmedSteps);
        List<String> flowTxHashes = new ArrayList<>();
        BooleanSupplier cancelCheck = hooks::isCancelled;
        String executionId = java.util.UUID.randomUUID().toString();
        log.info("Starting TxFlow execution {} for definition {}", executionId, flow.getId());

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), executionId, flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(scheduler.now());
            FlowHorizonMonitor liveMonitor = createLiveAttemptMonitor(settings);

            if (flowRestartAttempts == 0) {
                notifyFlowStarting(flow, hooks, origin);
            }

            flowTxHashes.clear();
            List<FlowStep> steps = flow.getSteps();
            int totalSteps = steps.size();

            // Determine which steps to skip (still confirmed from previous attempt)
            Set<Integer> skippedStepIndices;
            try {
                skippedStepIndices = previousConfirmedSteps.isEmpty()
                        ? Set.of()
                        : verifyRetainedSteps(
                                previousConfirmedSteps, steps, cancelCheck, settings);
            } catch (CancellationException cancellation) {
                resultBuilder.withStepResults(orderedStepResults(previousConfirmedSteps));
                return cancelledFlowResult(flow, resultBuilder, hooks, cancellation);
            }

            List<Transaction> builtTransactions = new ArrayList<>();
            List<String> precomputedTxHashes = new ArrayList<>();
            List<FlowStepResult> stepResults = new ArrayList<>();
            Set<Integer> submittedStepIndices = new HashSet<>();
            Set<Integer> confirmedStepIndices = new HashSet<>(skippedStepIndices);

            try {
                // ============ PHASE 1: BUILD ALL TRANSACTIONS ============
                log.info("BATCH mode: Building {} transactions (skipping {} confirmed)",
                        totalSteps, skippedStepIndices.size());
                for (int i = 0; i < totalSteps; i++) {
                    // Check for cancellation
                    if (hooks.isCancelled()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, hooks, null);
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
                        recordRetainedStep(
                                step, prevResult, context, resultBuilder, flowTxHashes, liveMonitor, hooks);
                        continue;
                    }

                    verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);
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
                        // The result is provisional until phase 2 submits the signed bytes.
                        // Keep it only in the execution context so later batch steps can
                        // resolve same-batch outputs; do not report a build as successful.
                        context.recordStepResult(step, stepResult);

                        log.debug("Step '{}' built: {} with {} outputs", step.getId(), txHash, outputUtxos.size());
                    } else {
                        FlowStepResult failedResult = FlowStepResult.failure(step.getId(), buildResult.getError());
                        stepResults.add(failedResult);
                        notifyStepTerminal(step, failedResult);
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        resultBuilder.completedAt(scheduler.now());
                        FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(failedResult.getError())
                                .build();
                        notifyFlowTerminal(flow, flowFailed);
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

                    verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);
                    // A signed batch attempt is still local at this point. Honour
                    // cancellation before the durable SUBMITTING transition and
                    // before any backend I/O; build-only results remain hidden.
                    if (hooks.isCancelled()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, hooks, null);
                    }
                    persistencePort.onSubmitting(step, tx);
                    if (hooks.isCancelled()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, hooks, null);
                    }
                    FlowStepResult submittedStep = stepResults.get(i);
                    String actualHash;
                    boolean reconciled = false;
                    try {
                        TxResult result = submitTransaction(tx);
                        if (!result.isSuccessful()) {
                            FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                                    new RuntimeException("Transaction submission failed: " + result.getResponse()));
                            stepResults.set(i, failedResult);
                            notifyStepTerminal(step, failedResult);
                            resultBuilder.withStepResults(observableStepResults(
                                    stepResults, submittedStepIndices, confirmedStepIndices));
                            resultBuilder.completedAt(scheduler.now());
                            FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                    .withError(failedResult.getError())
                                    .build();
                            notifyFlowTerminal(flow, flowFailed);
                            hooks.onFlowFailed(flow, FlowStatus.FAILED);
                            return flowFailed;
                        }
                        actualHash = result.getValue();
                    } catch (UncertainSubmissionException uncertain) {
                        submittedStep = reconcileUncertainSubmission(step, context, uncertain);
                        if (!submittedStep.isSuccessful()) {
                            stepResults.set(i, submittedStep);
                            notifyStepTerminal(step, submittedStep);
                            resultBuilder.withStepResults(observableStepResults(
                                    stepResults, submittedStepIndices, confirmedStepIndices));
                            resultBuilder.completedAt(scheduler.now());
                            FlowResult flowFailed = resultBuilder.withStatus(FlowStatus.FAILED)
                                    .withError(submittedStep.getError())
                                    .build();
                            notifyFlowTerminal(flow, flowFailed);
                            hooks.onFlowFailed(flow, FlowStatus.FAILED);
                            return flowFailed;
                        }
                        actualHash = submittedStep.getTransactionHash();
                        stepResults.set(i, submittedStep);
                        reconciled = true;
                    }

                    submittedStepIndices.add(i);
                    if (!Objects.equals(actualHash, expectedHash)) {
                        throw new FlowExecutionException(
                                "BATCH mode hash mismatch for step '" + step.getId() +
                                "': expected " + expectedHash + ", actual " + actualHash +
                                ". Downstream transactions would reference invalid UTXO inputs.");
                    }
                    if (!reconciled) {
                        persistencePort.onSubmitted(step, actualHash);
                    }
                    flowTxHashes.add(actualHash);
                    listener.onTransactionSubmitted(step, actualHash);
                    hooks.onTransactionSubmitted(flow, step, actualHash);
                    log.debug("Step '{}' submitted: {}", step.getId(), actualHash);
                }

                // ============ PHASE 3: WAIT FOR CONFIRMATIONS ============
                log.info("BATCH mode: Waiting for {} confirmations (skipping {} confirmed)",
                        precomputedTxHashes.size(), skippedStepIndices.size());
                for (int i = 0; i < precomputedTxHashes.size(); i++) {
                    String txHash = precomputedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        // Already confirmed and completed when it was retained.
                        continue;
                    }

                    verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);
                    ConfirmationOutcome confirmation = waitForConfirmation(txHash, step, cancelCheck, settings);
                    if (confirmation.isConfirmed()) {
                        confirmedStepIndices.add(i);
                        ConfirmationResult cr = confirmation.getResult();
                        hooks.onStepCompleted(step, stepResults.get(i));
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        hooks.onTransactionConfirmed(flow, step, txHash, cr);
                        if (liveMonitor != null) liveMonitor.track(step, txHash);
                    } else {
                        Throwable error = confirmationFailure(step, txHash, confirmation);
                        FlowStepResult terminalStep = confirmationStepResult(
                                stepResults.get(i), confirmation, error);
                        stepResults.set(i, terminalStep);
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return finishTerminalConfirmation(
                                flow, step, terminalStep, confirmation, error,
                                resultBuilder, hooks);
                    }
                }

                verifyLiveAttempts(flow, liveMonitor, hooks, cancelCheck, settings);

                // Cleanup tracked transactions to prevent memory leak
                if (settings.getConfirmationTracker() != null) {
                    for (String hash : flowTxHashes) {
                        settings.getConfirmationTracker().stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(scheduler.now());
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                hooks.onFlowCompleted(flow);
                return successResult;

            } catch (CancellationException cancellation) {
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                return cancelledFlowResult(flow, resultBuilder, hooks, cancellation);
            } catch (RollbackException e) {
                hooks.onRollbackDetected(flow, e.getStep(), e.getTxHash(),
                        e.getPreviousBlockHeight(), e.getMessage());

                flowRestartAttempts++;
                if (flowRestartAttempts > maxRollbackRetries) {
                    log.error("Flow restart limit ({}) reached after rollback at step '{}' in BATCH mode",
                            maxRollbackRetries, e.getStep().getId());
                    resultBuilder.withStepResults(withRolledBackAttemptFailure(
                            observableStepResults(stepResults, submittedStepIndices,
                                    confirmedStepIndices), e));
                    resultBuilder.completedAt(scheduler.now());
                    FlowResult failedResult = resultBuilder.failure(
                            new FlowExecutionException("Flow restart limit reached after rollback in BATCH mode", e));
                    notifyFlowTerminal(flow, failedResult);
                    hooks.onFlowFailed(flow, FlowStatus.FAILED);
                    return failedResult;
                }

                log.info("Restarting BATCH flow (attempt {}/{}) due to rollback at step '{}'",
                        flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                        "Rollback detected at step '" + e.getStep().getId() + "' in BATCH mode");

                // Find steps that are still confirmed before clearing tracker
                try {
                    previousConfirmedSteps = findStillConfirmedSteps(
                            e.getStep().getId(), steps, precomputedTxHashes, stepResults,
                            cancelCheck, settings);
                } catch (CancellationException cancellation) {
                    resultBuilder.withStepResults(observableStepResults(
                            stepResults, submittedStepIndices, confirmedStepIndices));
                    return cancelledFlowResult(
                            flow, resultBuilder, hooks, cancellation);
                }

                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck, settings);
                hooks.onFlowRestarting();
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                resultBuilder.completedAt(scheduler.now());
                FlowResult failedResult = resultBuilder.failure(e);
                notifyFlowTerminal(flow, failedResult);
                hooks.onFlowFailed(flow, FlowStatus.FAILED);
                return failedResult;
            }
        }

        // Should not reach here
        FlowResult.Builder errorBuilder = FlowResult.builder(flow.getId())
                .startedAt(scheduler.now())
                .completedAt(scheduler.now());
        FlowResult failedResult = errorBuilder.failure(
                new FlowExecutionException("BATCH flow execution failed: exceeded maximum restart attempts"));
        notifyFlowTerminal(flow, failedResult);
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
                TxPlan plan = copyPlanForExecution(step.getTxPlan(), variables, context);
                txContext = quickTxBuilder.compose(plan, signerRegistry, scriptRegistry);
            } else if (step.hasTxContextFactory()) {
                txContext = step.getTxContextFactory().apply(quickTxBuilder);
            } else {
                throw new FlowExecutionException("Step '" + stepId + "' has neither TxPlan nor TxContext factory");
            }

            // Build and sign WITHOUT submitting
            Transaction transaction = txContext.buildAndSign();
            persistencePort.onPrepared(step, transaction);
            if (txInspector != null) {
                txInspector.accept(transaction);
            }
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
            if (hasSubmissionApiFailure(e)) {
                throw new UncertainSubmissionException(transaction, e);
            }
            throw new FlowExecutionException("Transaction submission failed", e);
        }
    }

    private TxPlan copyPlanForExecution(TxPlan source, Map<String, Object> flowVariables,
                                        FlowExecutionContext context) {
        return planMaterializer.materialize(source, flowVariables, context);
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
                    .startedAt(scheduler.now())
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
            TransactionStateDetails details = TransactionStateDetails.submitted(scheduler.now());
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
                    .timestamp(scheduler.now())
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
        persistTransactionRolledBack(flow.getId(), step, txHash, previousBlockHeight, errorMessage);
    }

    private void persistTransactionRolledBack(String flowId, FlowStep step, String txHash,
                                              Long previousBlockHeight, String errorMessage) {
        if (flowStateStore == null) return;

        try {
            TransactionStateDetails details = TransactionStateDetails.rolledBack(
                    previousBlockHeight, errorMessage, scheduler.now());
            flowStateStore.updateTransactionState(flowId, step.getId(), txHash, details);
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
        // Uncertain-disposition contract: a submission-pending step (IN_PROGRESS with a
        // transaction hash) has an unknown on-chain outcome — confirmation timed out or
        // reconciliation could not decide. Re-executing it would rebuild and resubmit a
        // payment that may still confirm, so resume refuses until the operator reconciles
        // the pending transaction on chain.
        for (FlowStepResult stepResult : previousResult.getStepResults()) {
            if (stepResult.getStatus() == FlowStatus.IN_PROGRESS
                    && stepResult.getTransactionHash() != null) {
                throw new IllegalStateException("Cannot resume flow '" + flow.getId()
                        + "': step '" + stepResult.getStepId() + "' has a submission-pending transaction "
                        + stepResult.getTransactionHash() + " whose outcome is unknown."
                        + " Re-executing it could duplicate a transaction that may still confirm."
                        + " Verify the transaction on chain first: if it confirmed, run the remaining"
                        + " steps as a new flow; resume only once the transaction can no longer land.");
            }
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
    private Map<Integer, FlowStepResult> verifyPreviousSteps(
            TxFlow flow, FlowResult previousResult,
            EffectiveFlowExecutionSettings settings) {
        Map<Integer, FlowStepResult> candidates = new java.util.LinkedHashMap<>();
        List<FlowStep> steps = flow.getSteps();

        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            Optional<FlowStepResult> prevStepResult = previousResult.getStepResult(step.getId());

            if (prevStepResult.isPresent() && prevStepResult.get().isSuccessful()) {
                candidates.put(i, prevStepResult.get());
            } else {
                break; // Stop at first non-successful step
            }
        }

        Set<Integer> retained = reconcilePreviouslyConfirmed(candidates, () -> false, settings);
        Map<Integer, FlowStepResult> confirmedSteps = new HashMap<>();
        for (Map.Entry<Integer, FlowStepResult> candidate : candidates.entrySet()) {
            if (!retained.contains(candidate.getKey())) break;
            confirmedSteps.put(candidate.getKey(), candidate.getValue());
            log.info("Resume: step '{}' tx {} remains confirmed and will be skipped",
                    steps.get(candidate.getKey()).getId(), candidate.getValue().getTransactionHash());
        }
        return confirmedSteps;
    }

    /**
     * Close this executor and release associated resources.
     * <p>
     * Cancels all running flows and clears active flow tracking.
     */
    @Override
    public void close() {
        for (FlowHandle handle : activeHandles) {
            handle.cancel();
        }
        activeHandles.clear();
        activeFlowIds.clear();
    }
}
