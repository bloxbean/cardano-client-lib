package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultScriptSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.script.ScriptRegistry;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.config.RollbackAction;
import com.bloxbean.cardano.client.txflow.config.RollbackMonitoringHorizon;
import com.bloxbean.cardano.client.txflow.config.RollbackPolicy;
import com.bloxbean.cardano.client.txflow.config.RollbackRebuildScope;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.internal.FlowExtensions;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Package-private step execution engine used internally by {@link FlowEngine}.
 *
 * <p>Orchestrates synchronous execution of a compiled {@link TxFlow} by resolving
 * UTXO dependencies between steps, building and submitting transactions in
 * SEQUENTIAL, PIPELINED, or BATCH mode, tracking confirmations and rollbacks,
 * and forwarding lifecycle callbacks to {@link FlowListener} and {@link PersistencePort}.</p>
 */
@Slf4j
class FlowExecutor {
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
    private volatile FlowExtensions extensions = FlowExtensions.NONE;
    private volatile ScriptSupplier scriptSupplier;
    private volatile BiFunction<UtxoSupplier, ProtocolParamsSupplier, TransactionEvaluator> txEvaluatorFactory;
    private volatile FlowListener listener = FlowListener.NOOP;
    private volatile Consumer<Transaction> txInspector;
    private volatile ChainingMode chainingMode = ChainingMode.SEQUENTIAL;
    private volatile RetryPolicy defaultRetryPolicy;
    private volatile ConfirmationConfig confirmationConfig;
    private volatile RollbackStrategy rollbackStrategy = RollbackStrategy.FAIL_IMMEDIATELY;
    private volatile boolean chainingModeConfigured;
    private volatile boolean defaultRetryPolicyConfigured;
    private volatile boolean confirmationConfigConfigured;
    private volatile boolean rollbackStrategyConfigured;
    private volatile PersistencePort persistencePort = PersistencePort.NOOP;

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
        this.scriptSupplier = backendScriptSupplier(backendService);
    }

    /** The backend's script lookup, as {@code new QuickTxBuilder(backendService)} configures it. */
    static ScriptSupplier backendScriptSupplier(BackendService backendService) {
        try {
            return backendService.getScriptService() != null
                    ? new DefaultScriptSupplier(backendService.getScriptService()) : null;
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    static FlowExecutor create(BackendService backendService) {
        return new FlowExecutor(backendService);
    }

    static FlowExecutor create(UtxoSupplier utxoSupplier,
                               ProtocolParamsSupplier protocolParamsSupplier,
                               TransactionProcessor transactionProcessor,
                               ChainDataSupplier chainDataSupplier) {
        return new FlowExecutor(utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier);
    }

    FlowExecutor withSignerRegistry(SignerRegistry registry) {
        this.signerRegistry = registry;
        return this;
    }

    FlowExecutor withScriptRegistry(ScriptRegistry registry) {
        this.scriptRegistry = registry;
        return this;
    }

    FlowExecutor withExtension(QuickTxExtension extension) {
        this.extensions = extensions.with(extension);
        return this;
    }

    FlowExecutor withScriptSupplier(ScriptSupplier supplier) {
        this.scriptSupplier = supplier;
        return this;
    }

    FlowExecutor withTxEvaluator(
            BiFunction<UtxoSupplier, ProtocolParamsSupplier, TransactionEvaluator> factory) {
        this.txEvaluatorFactory = factory;
        return this;
    }

    FlowExecutor withExtensions(FlowExtensions registered) {
        this.extensions = Objects.requireNonNull(registered, "extensions");
        return this;
    }

    FlowExecutor withListener(FlowListener listener) {
        if (listener == null || listener == FlowListener.NOOP) {
            this.listener = FlowListener.NOOP;
        } else if (listener instanceof CompositeFlowListener) {
            this.listener = listener;
        } else {
            this.listener = new CompositeFlowListener(new FlowListener[]{listener});
        }
        return this;
    }

    FlowExecutor withScheduler(FlowScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        return this;
    }

    FlowExecutor withPersistencePort(PersistencePort persistencePort) {
        this.persistencePort = Objects.requireNonNull(persistencePort, "persistencePort");
        return this;
    }

    FlowExecutor withTxInspector(Consumer<Transaction> inspector) {
        this.txInspector = inspector;
        return this;
    }

    FlowExecutor withChainingMode(ChainingMode mode) {
        this.chainingMode = mode != null ? mode : ChainingMode.SEQUENTIAL;
        this.chainingModeConfigured = true;
        return this;
    }

    FlowExecutor withDefaultRetryPolicy(RetryPolicy retryPolicy) {
        this.defaultRetryPolicy = retryPolicy;
        this.defaultRetryPolicyConfigured = true;
        return this;
    }

    FlowExecutor withConfirmationConfig(ConfirmationConfig config) {
        this.confirmationConfig = config;
        this.confirmationConfigConfigured = true;
        return this;
    }

    FlowExecutor withRollbackStrategy(RollbackStrategy strategy) {
        this.rollbackStrategy = strategy != null ? strategy : RollbackStrategy.FAIL_IMMEDIATELY;
        this.rollbackStrategyConfigured = true;
        return this;
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
            effectiveConfirmationConfig = base.toBuilder()
                    .maxRollbackRetries(flowSettings.getRollbackPolicy().maxRecoveryCycles())
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
                        == RollbackMonitoringHorizon.UNTIL_FLOW_TERMINAL,
                effectivePortableRollbackPolicy);
    }

    private RollbackStrategy portableRollbackStrategy(RollbackPolicy policy) {
        if (policy == null) return RollbackStrategy.FAIL_IMMEDIATELY;
        return switch (policy.action()) {
            case FAIL, PAUSE_FOR_RECOVERY -> RollbackStrategy.FAIL_IMMEDIATELY;
            case WAIT_FOR_REINCLUSION -> RollbackStrategy.NOTIFY_ONLY;
            case RECONCILE_AND_REBUILD -> policy.rebuildScope() == RollbackRebuildScope.AFFECTED_STEP
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

    private void verifyLiveAttempts(FlowHorizonMonitor monitor,
                                    BooleanSupplier cancelCheck,
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
                        persistencePort.onConfirmed(step, transactionHash);
                        return;
                    }
                    Throwable reinclusionError = confirmationFailure(
                            step, transactionHash, reinclusion);
                    persistencePort.onRolledBack(step, transactionHash, previousHeight);
                    throw new FlowExecutionException(
                            "Transaction rolled back while monitoring the live flow", reinclusionError);
                }
                boolean rebuild = settings.getRollbackStrategy() == RollbackStrategy.REBUILD_ENTIRE_FLOW
                        || settings.getRollbackStrategy() == RollbackStrategy.REBUILD_FROM_FAILED;
                RollbackException rollback = rebuild
                        ? RollbackException.forFlowRestart(transactionHash, step, previousHeight)
                        : RollbackException.forStepRebuild(transactionHash, step, previousHeight);
                listener.onTransactionRolledBack(step, transactionHash, previousHeight);
                persistencePort.onRolledBack(step, transactionHash, previousHeight);
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

    // ==================== Execution ====================

    private void validateConfiguration(EffectiveFlowExecutionSettings settings) {
        if (settings.getRollbackStrategy() != RollbackStrategy.FAIL_IMMEDIATELY
                && settings.getConfirmationConfig() == null) {
            throw new IllegalStateException(
                    "Rollback strategy " + settings.getRollbackStrategy() + " requires confirmation tracking. " +
                    "Call withConfirmationConfig() or set context.confirmation before execution.");
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
        List<FlowDiagnostic> extensionErrors = extensions.check(flow);
        if (!extensionErrors.isEmpty()) {
            throw new FlowExecutionException("Flow validation failed: " + extensionErrors);
        }
    }

    private void recordRetainedStep(FlowStep step, FlowStepResult result,
                                    FlowExecutionContext context,
                                    FlowResult.Builder resultBuilder,
                                    List<String> flowTransactionHashes,
                                    FlowHorizonMonitor liveMonitor) {
        context.recordStepResult(step, result);
        resultBuilder.addStepResult(result);
        flowTransactionHashes.add(result.getTransactionHash());
        if (liveMonitor != null) {
            liveMonitor.track(step, result.getTransactionHash());
        }
        listener.onTransactionConfirmed(step, result.getTransactionHash());
        listener.onStepCompleted(step, result);
        log.info("Step '{}' retained after chain reconciliation: {}",
                step.getId(), result.getTransactionHash());
    }

    FlowResult executeSync(TxFlow flow) {
        return executeSync(flow, () -> false);
    }

    FlowResult executeSync(TxFlow flow, BooleanSupplier cancelCheck) {
        EffectiveFlowExecutionSettings settings = effectiveSettings(flow);
        validateConfiguration(settings);

        TxFlow.ValidationResult validation = flow.validate();
        if (!validation.isValid()) {
            throw new FlowExecutionException("Flow validation failed: " + validation.getErrors());
        }
        validateExecutableFlow(flow);

        BooleanSupplier effectiveCancel = cancelCheck != null ? cancelCheck : () -> false;
        return ChainingStrategy.forMode(settings.getChainingMode()).execute(
                () -> doExecuteSequential(flow, effectiveCancel, settings),
                () -> doExecutePipelined(flow, effectiveCancel, settings),
                () -> doExecuteBatch(flow, effectiveCancel, settings));
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
    private FlowResult doExecuteSequential(TxFlow flow, BooleanSupplier cancelCheck,
                                           EffectiveFlowExecutionSettings settings) {
        int maxRollbackRetries = getMaxRollbackRetries(settings);
        int flowRestartAttempts = 0;
        Map<String, Integer> stepRollbackAttempts = new ConcurrentHashMap<>();
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>();
        List<String> flowTxHashes = new ArrayList<>();
        String executionId = UUID.randomUUID().toString();
        log.info("Starting TxFlow execution {} for definition {}", executionId, flow.getId());

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), executionId, flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(scheduler.now());
            FlowHorizonMonitor liveMonitor = createLiveAttemptMonitor(settings);

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
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
                return cancelledFlowResult(flow, resultBuilder, cancellation);
            }

            try {
                for (int i = 0; i < totalSteps; i++) {
                    if (cancelCheck.getAsBoolean()) {
                        return cancelledFlowResult(flow, resultBuilder, null);
                    }

                    FlowStep step = steps.get(i);
                    if (skippedStepIndices.contains(i)) {
                        FlowStepResult retained = previousConfirmedSteps.get(i);
                        attemptTxHashes.add(retained.getTransactionHash());
                        attemptStepResults.add(retained);
                        recordRetainedStep(
                                step, retained, context, resultBuilder, flowTxHashes, liveMonitor);
                        continue;
                    }
                    verifyLiveAttempts(liveMonitor, cancelCheck, settings);
                    listener.onStepStarted(step, i, totalSteps);

                    FlowStepResult stepResult = executeStepWithRollbackHandling(
                            step, context, flow.getVariables(), false,
                            stepRollbackAttempts, maxRollbackRetries, cancelCheck, settings);

                    if (stepResult.isSuccessful()) {
                        String txHash = stepResult.getTransactionHash();
                        attemptTxHashes.add(txHash);
                        attemptStepResults.add(stepResult);
                        flowTxHashes.add(txHash);

                        ConfirmationOutcome confirmation = waitForConfirmation(txHash, step, cancelCheck, settings);
                        if (confirmation.isConfirmed()) {
                            resultBuilder.addStepResult(stepResult);
                            listener.onStepCompleted(step, stepResult);
                            persistencePort.onConfirmed(step, txHash);
                            if (liveMonitor != null) liveMonitor.track(step, txHash);
                        } else {
                            return terminalConfirmationResult(
                                    flow, step, stepResult, confirmation, resultBuilder);
                        }
                    } else {
                        resultBuilder.addStepResult(stepResult);
                        if (isCancellationProjection(stepResult)) {
                            return cancelledFlowResult(
                                    flow, resultBuilder, stepResult.getError());
                        }
                        notifyStepTerminal(step, stepResult);
                        resultBuilder.completedAt(scheduler.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        notifyFlowTerminal(flow, failedResult);
                        return failedResult;
                    }
                }

                verifyLiveAttempts(liveMonitor, cancelCheck, settings);

                if (settings.getConfirmationTracker() != null) {
                    for (String hash : flowTxHashes) {
                        settings.getConfirmationTracker().stopTracking(hash);
                    }
                }

                resultBuilder.completedAt(scheduler.now());
                FlowResult successResult = resultBuilder.success();
                listener.onFlowCompleted(flow, successResult);
                return successResult;

            } catch (CancellationException cancellation) {
                return cancelledFlowResult(flow, resultBuilder, cancellation);
            } catch (RollbackException e) {
                persistencePort.onRolledBack(e.getStep(), e.getTxHash(), e.getPreviousBlockHeight());

                if (e.isRequiresFlowRestart()) {
                    try {
                        previousConfirmedSteps = findStillConfirmedSteps(
                                e.getStep().getId(), steps, attemptTxHashes, attemptStepResults,
                                cancelCheck, settings);
                    } catch (CancellationException cancellation) {
                        resultBuilder.withStepResults(attemptStepResults);
                        return cancelledFlowResult(flow, resultBuilder, cancellation);
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
                        return failedResult;
                    }

                    log.info("Restarting flow (attempt {}/{}) due to rollback at step '{}'",
                            flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                    listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                            "Rollback detected at step '" + e.getStep().getId() + "'");
                    waitForBackendReadyAfterRollback(rollbackCleanupHashes(flowTxHashes, e.getTxHash()),
                            cancelCheck, settings);
                    stepRollbackAttempts.clear();
                    continue;
                } else {
                    log.error("Step rebuild failed for step '{}'", e.getStep().getId());
                    resultBuilder.withStepResults(
                            withRolledBackAttemptFailure(attemptStepResults, e));
                    resultBuilder.completedAt(scheduler.now());
                    FlowResult failedResult = resultBuilder.failure(e);
                    notifyFlowTerminal(flow, failedResult);
                    return failedResult;
                }
            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.completedAt(scheduler.now());
                FlowResult failedResult = resultBuilder.failure(e);
                notifyFlowTerminal(flow, failedResult);
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
            FlowResult.Builder resultBuilder) {
        String txHash = submittedStep.getTransactionHash();
        Throwable error = confirmationFailure(step, txHash, confirmation);
        FlowStepResult terminalStep = confirmationStepResult(
                submittedStep, confirmation, error);
        resultBuilder.addStepResult(terminalStep);
        return finishTerminalConfirmation(
                flow, step, terminalStep, confirmation, error, resultBuilder);
    }

    private FlowResult finishTerminalConfirmation(
            TxFlow flow, FlowStep step, FlowStepResult terminalStep,
            ConfirmationOutcome confirmation, Throwable error,
            FlowResult.Builder resultBuilder) {
        if (confirmation.isRolledBack()) {
            long previousBlockHeight = ((RollbackException) error).getPreviousBlockHeight();
            persistencePort.onRolledBack(step, terminalStep.getTransactionHash(), previousBlockHeight);
        }

        if (confirmation.getType() == ConfirmationOutcome.Type.CANCELLED) {
            return cancelledFlowResult(flow, resultBuilder, error);
        }

        notifyStepTerminal(step, terminalStep);
        FlowResult terminal = resultBuilder.withStatus(FlowStatus.FAILED)
                .withError(error)
                .completedAt(scheduler.now())
                .build();
        notifyFlowTerminal(flow, terminal);
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
            TxFlow flow, FlowResult.Builder resultBuilder, Throwable cause) {
        Throwable cancellation = cause != null ? cause
                : new CancellationException("Flow execution cancelled");
        FlowResult result = resultBuilder.withStatus(FlowStatus.CANCELLED)
                .withError(cancellation)
                .completedAt(scheduler.now())
                .build();
        notifyFlowTerminal(flow, result);
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
    private FlowResult doExecutePipelined(TxFlow flow, BooleanSupplier cancelCheck,
                                          EffectiveFlowExecutionSettings settings) {
        int maxRollbackRetries = getMaxRollbackRetries(settings);
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>();
        List<String> flowTxHashes = new ArrayList<>();
        String executionId = UUID.randomUUID().toString();
        log.info("Starting TxFlow execution {} for definition {}", executionId, flow.getId());

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), executionId, flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(scheduler.now());
            FlowHorizonMonitor liveMonitor = createLiveAttemptMonitor(settings);

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
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
                return cancelledFlowResult(flow, resultBuilder, cancellation);
            }
            Set<Integer> submittedStepIndices = new HashSet<>();
            Set<Integer> confirmedStepIndices = new HashSet<>(skippedStepIndices);

            try {
                // Phase 1: Build and submit all transactions without waiting
                log.info("PIPELINED mode: Submitting {} transactions (skipping {} confirmed)",
                        totalSteps, skippedStepIndices.size());
                for (int i = 0; i < totalSteps; i++) {
                    if (cancelCheck.getAsBoolean()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, null);
                    }

                    FlowStep step = steps.get(i);
                    listener.onStepStarted(step, i, totalSteps);

                    if (skippedStepIndices.contains(i)) {
                        FlowStepResult prevResult = previousConfirmedSteps.get(i);
                        stepResults.add(prevResult);
                        submittedTxHashes.add(prevResult.getTransactionHash());
                        recordRetainedStep(
                                step, prevResult, context, resultBuilder, flowTxHashes, liveMonitor);
                        continue;
                    }

                    verifyLiveAttempts(liveMonitor, cancelCheck, settings);
                    FlowStepResult stepResult = executeStepWithRetry(step, context, flow.getVariables(), true,
                            cancelCheck, settings);
                    stepResults.add(stepResult);

                    if (stepResult.isSuccessful()) {
                        submittedStepIndices.add(i);
                        submittedTxHashes.add(stepResult.getTransactionHash());
                        flowTxHashes.add(stepResult.getTransactionHash());
                        listener.onTransactionSubmitted(step, stepResult.getTransactionHash());
                        log.debug("Step '{}' submitted: {}", step.getId(), stepResult.getTransactionHash());
                    } else {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        if (isCancellationProjection(stepResult)) {
                            return cancelledFlowResult(
                                    flow, resultBuilder, stepResult.getError());
                        }
                        notifyStepTerminal(step, stepResult);
                        resultBuilder.completedAt(scheduler.now());
                        FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                                .withError(stepResult.getError())
                                .build();
                        notifyFlowTerminal(flow, failedResult);
                        return failedResult;
                    }
                }

                // Phase 2: Wait for all transactions to be confirmed
                log.info("PIPELINED mode: Waiting for {} transactions to confirm", submittedTxHashes.size());
                for (int i = 0; i < submittedTxHashes.size(); i++) {
                    String txHash = submittedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        continue;
                    }

                    verifyLiveAttempts(liveMonitor, cancelCheck, settings);
                    ConfirmationOutcome confirmation = waitForConfirmation(txHash, step, cancelCheck, settings);
                    if (confirmation.isConfirmed()) {
                        confirmedStepIndices.add(i);
                        ConfirmationResult result = confirmation.getResult();
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        persistencePort.onConfirmed(step, txHash);
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
                                resultBuilder);
                    }
                }

                verifyLiveAttempts(liveMonitor, cancelCheck, settings);

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
                return successResult;

            } catch (CancellationException cancellation) {
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                return cancelledFlowResult(flow, resultBuilder, cancellation);
            } catch (RollbackException e) {
                persistencePort.onRolledBack(e.getStep(), e.getTxHash(), e.getPreviousBlockHeight());

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
                    return cancelledFlowResult(flow, resultBuilder, cancellation);
                }

                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck, settings);
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                resultBuilder.completedAt(scheduler.now());
                FlowResult failedResult = resultBuilder.failure(e);
                notifyFlowTerminal(flow, failedResult);
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
                                && portablePolicy.action() == RollbackAction.WAIT_FOR_REINCLUSION) {
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
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, scriptSupplier, transactionProcessor);

            // Get TxContext from step (either from TxPlan or TxContext factory)
            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                txContext = composePlanStep(step, context, variables, quickTxBuilder, utxoSupplier);
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
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, scriptSupplier, transactionProcessor);

            // Get TxContext from step (either from TxPlan or TxContext factory)
            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                txContext = composePlanStep(step, context, variables, quickTxBuilder, utxoSupplier);
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
     * Composes a {@code TxPlan} step on the step's builder. Registered extensions are applied
     * only to builders of plans that use them, and the configured evaluator, if any, is created
     * over the step's evaluation view.
     */
    private QuickTxBuilder.TxContext composePlanStep(FlowStep step, FlowExecutionContext context,
                                                     Map<String, Object> variables,
                                                     QuickTxBuilder quickTxBuilder, UtxoSupplier utxoSupplier) {
        TxPlan plan = copyPlanForExecution(step.getTxPlan(), variables, context);
        if (plan.hasExtensions()) extensions.applyTo(quickTxBuilder);
        QuickTxBuilder.TxContext txContext = quickTxBuilder.compose(plan, signerRegistry, scriptRegistry);
        if (txEvaluatorFactory != null)
            txContext.withTxEvaluator(txEvaluatorFactory.apply(
                    evaluationUtxoSupplier(step, context, utxoSupplier), protocolParamsSupplier));
        return txContext;
    }

    /**
     * UTxO view for the step's script-cost evaluator. An evaluator resolves every input and
     * reference input of the transaction already built by exact lookup, so it also needs exact
     * access to the funding steps' pending outputs the build selected by address. Selection
     * itself keeps the narrower build supplier; the evaluator only reads what the built
     * transaction references.
     */
    private UtxoSupplier evaluationUtxoSupplier(FlowStep step, FlowExecutionContext context,
                                                UtxoSupplier buildSupplier) {
        if (!(buildSupplier instanceof FlowUtxoSupplier) || step.getFundingFrom().isEmpty()) {
            return buildSupplier;
        }
        Set<String> exactPendingStepIds = new LinkedHashSet<>(
                planMaterializer.referencedStepIds(step.getTxPlan()));
        exactPendingStepIds.addAll(step.getFundingFrom());
        return new FlowUtxoSupplier(baseUtxoSupplier, context, step.getDependencies(),
                exactPendingStepIds, step.getFundingFrom());
    }

    /**
     * Create appropriate UTXO supplier based on step dependencies.
     */
    private UtxoSupplier createUtxoSupplier(FlowStep step, FlowExecutionContext context) {
        if (!step.hasDependencies() && step.getFundingFrom().isEmpty()
                && context.getCompletedStepCount() == 0) {
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
                exactPendingStepIds, step.getFundingFrom());
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
    private FlowResult doExecuteBatch(TxFlow flow, BooleanSupplier cancelCheck,
                                      EffectiveFlowExecutionSettings settings) {
        int maxRollbackRetries = getMaxRollbackRetries(settings);
        int flowRestartAttempts = 0;
        Map<Integer, FlowStepResult> previousConfirmedSteps = new HashMap<>();
        List<String> flowTxHashes = new ArrayList<>();
        String executionId = UUID.randomUUID().toString();
        log.info("Starting TxFlow execution {} for definition {}", executionId, flow.getId());

        while (flowRestartAttempts <= maxRollbackRetries) {
            FlowExecutionContext context = new FlowExecutionContext(flow.getId(), executionId, flow.getVariables());
            FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                    .startedAt(scheduler.now());
            FlowHorizonMonitor liveMonitor = createLiveAttemptMonitor(settings);

            if (flowRestartAttempts == 0) {
                listener.onFlowStarted(flow);
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
                return cancelledFlowResult(flow, resultBuilder, cancellation);
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
                    if (cancelCheck.getAsBoolean()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, null);
                    }

                    FlowStep step = steps.get(i);
                    listener.onStepStarted(step, i, totalSteps);

                    if (skippedStepIndices.contains(i)) {
                        FlowStepResult prevResult = previousConfirmedSteps.get(i);
                        builtTransactions.add(null); // placeholder to maintain index alignment
                        precomputedTxHashes.add(prevResult.getTransactionHash());
                        stepResults.add(prevResult);
                        recordRetainedStep(
                                step, prevResult, context, resultBuilder, flowTxHashes, liveMonitor);
                        continue;
                    }

                    verifyLiveAttempts(liveMonitor, cancelCheck, settings);
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
                        return flowFailed;
                    }
                }

                // ============ PHASE 2: SUBMIT ALL TRANSACTIONS ============
                log.info("BATCH mode: Submitting {} transactions (skipping {} confirmed)",
                        builtTransactions.size(), skippedStepIndices.size());
                for (int i = 0; i < builtTransactions.size(); i++) {
                    if (skippedStepIndices.contains(i)) {
                        FlowStep step = steps.get(i);
                        log.debug("Step '{}' submit skipped (still confirmed): {}", step.getId(), precomputedTxHashes.get(i));
                        continue;
                    }

                    Transaction tx = builtTransactions.get(i);
                    FlowStep step = steps.get(i);
                    String expectedHash = precomputedTxHashes.get(i);

                    verifyLiveAttempts(liveMonitor, cancelCheck, settings);
                    if (cancelCheck.getAsBoolean()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, null);
                    }
                    persistencePort.onSubmitting(step, tx);
                    if (cancelCheck.getAsBoolean()) {
                        resultBuilder.withStepResults(observableStepResults(
                                stepResults, submittedStepIndices, confirmedStepIndices));
                        return cancelledFlowResult(flow, resultBuilder, null);
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
                    log.debug("Step '{}' submitted: {}", step.getId(), actualHash);
                }

                // ============ PHASE 3: WAIT FOR CONFIRMATIONS ============
                log.info("BATCH mode: Waiting for {} confirmations (skipping {} confirmed)",
                        precomputedTxHashes.size(), skippedStepIndices.size());
                for (int i = 0; i < precomputedTxHashes.size(); i++) {
                    String txHash = precomputedTxHashes.get(i);
                    FlowStep step = steps.get(i);

                    if (skippedStepIndices.contains(i)) {
                        continue;
                    }

                    verifyLiveAttempts(liveMonitor, cancelCheck, settings);
                    ConfirmationOutcome confirmation = waitForConfirmation(txHash, step, cancelCheck, settings);
                    if (confirmation.isConfirmed()) {
                        confirmedStepIndices.add(i);
                        listener.onTransactionConfirmed(step, txHash);
                        listener.onStepCompleted(step, stepResults.get(i));
                        persistencePort.onConfirmed(step, txHash);
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
                                resultBuilder);
                    }
                }

                verifyLiveAttempts(liveMonitor, cancelCheck, settings);

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
                return successResult;

            } catch (CancellationException cancellation) {
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                return cancelledFlowResult(flow, resultBuilder, cancellation);
            } catch (RollbackException e) {
                persistencePort.onRolledBack(e.getStep(), e.getTxHash(), e.getPreviousBlockHeight());

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
                    return failedResult;
                }

                log.info("Restarting BATCH flow (attempt {}/{}) due to rollback at step '{}'",
                        flowRestartAttempts, maxRollbackRetries, e.getStep().getId());
                listener.onFlowRestarting(flow, flowRestartAttempts, maxRollbackRetries,
                        "Rollback detected at step '" + e.getStep().getId() + "' in BATCH mode");

                try {
                    previousConfirmedSteps = findStillConfirmedSteps(
                            e.getStep().getId(), steps, precomputedTxHashes, stepResults,
                            cancelCheck, settings);
                } catch (CancellationException cancellation) {
                    resultBuilder.withStepResults(observableStepResults(
                            stepResults, submittedStepIndices, confirmedStepIndices));
                    return cancelledFlowResult(flow, resultBuilder, cancellation);
                }

                waitForBackendReadyAfterRollback(flowTxHashes, cancelCheck, settings);
                continue;

            } catch (Exception e) {
                log.error("Flow execution failed", e);
                resultBuilder.withStepResults(observableStepResults(
                        stepResults, submittedStepIndices, confirmedStepIndices));
                resultBuilder.completedAt(scheduler.now());
                FlowResult failedResult = resultBuilder.failure(e);
                notifyFlowTerminal(flow, failedResult);
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
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, scriptSupplier, transactionProcessor);

            QuickTxBuilder.TxContext txContext;
            if (step.hasTxPlan()) {
                txContext = composePlanStep(step, context, variables, quickTxBuilder, utxoSupplier);
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
        return planMaterializer.materialize(source, flowVariables, context, extensions);
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
}
