package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.*;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private final BackendService backendService;
    private final UtxoSupplier baseUtxoSupplier;
    private SignerRegistry signerRegistry;
    private TxSigner defaultSigner;
    private FlowListener listener = FlowListener.NOOP;
    private Duration confirmationTimeout = Duration.ofSeconds(60);
    private Duration checkInterval = Duration.ofSeconds(2);
    private Executor executor;
    private Consumer<Transaction> txInspector;
    private ChainingMode chainingMode = ChainingMode.SEQUENTIAL;

    private FlowExecutor(BackendService backendService) {
        this.backendService = backendService;
        this.baseUtxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
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
     * Set the signer registry for resolving signer references.
     *
     * @param registry the signer registry
     * @return this executor
     */
    public FlowExecutor withSignerRegistry(SignerRegistry registry) {
        this.signerRegistry = registry;
        return this;
    }

    /**
     * Set a default signer for all steps.
     *
     * @param signer the default signer
     * @return this executor
     */
    public FlowExecutor withDefaultSigner(TxSigner signer) {
        this.defaultSigner = signer;
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
     * <p>
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

        if (chainingMode == ChainingMode.PIPELINED) {
            return executePipelined(flow);
        } else {
            return executeSequential(flow);
        }
    }

    /**
     * Execute flow in SEQUENTIAL mode - wait for each step to confirm before next.
     */
    private FlowResult executeSequential(TxFlow flow) {
        FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
        FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now());

        listener.onFlowStarted(flow);

        List<FlowStep> steps = flow.getSteps();
        int totalSteps = steps.size();

        try {
            for (int i = 0; i < totalSteps; i++) {
                FlowStep step = steps.get(i);
                listener.onStepStarted(step, i, totalSteps);

                FlowStepResult stepResult = executeStepSequential(step, context, flow.getVariables());
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
                    return failedResult;
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
     * Execute flow in PIPELINED mode - submit all transactions, then wait for confirmations.
     * <p>
     * This enables true transaction chaining where multiple transactions can land in the same block.
     */
    private FlowResult executePipelined(TxFlow flow) {
        FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
        FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now());

        listener.onFlowStarted(flow);

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

                FlowStepResult stepResult = executeStepPipelined(step, context, flow.getVariables());
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

                boolean confirmed = waitForConfirmation(txHash);
                if (confirmed) {
                    listener.onTransactionConfirmed(step, txHash);
                    listener.onStepCompleted(step, stepResults.get(i));
                    log.debug("Step '{}' confirmed: {}", step.getId(), txHash);
                } else {
                    // Confirmation timeout
                    FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                            new RuntimeException("Transaction confirmation timeout: " + txHash));
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

        } catch (Exception e) {
            log.error("Flow execution failed", e);
            resultBuilder.completedAt(Instant.now());
            FlowResult failedResult = resultBuilder.failure(e);
            listener.onFlowFailed(flow, failedResult);
            return failedResult;
        }
    }

    /**
     * Wait for a transaction to be confirmed on-chain.
     *
     * @param txHash the transaction hash to wait for
     * @return true if confirmed, false if timeout
     */
    private boolean waitForConfirmation(String txHash) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = confirmationTimeout.toMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                var result = backendService.getTransactionService().getTransaction(txHash);
                if (result.isSuccessful() && result.getValue() != null) {
                    return true;
                }
                Thread.sleep(checkInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.debug("Waiting for tx confirmation: {}", txHash);
                try {
                    Thread.sleep(checkInterval.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Execute a flow asynchronously.
     *
     * @param flow the flow to execute
     * @return a handle for monitoring the execution
     */
    public FlowHandle execute(TxFlow flow) {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

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

        if (chainingMode == ChainingMode.PIPELINED) {
            return executeWithHandlePipelined(flow, handle);
        } else {
            return executeWithHandleSequential(flow, handle);
        }
    }

    private FlowResult executeWithHandleSequential(TxFlow flow, FlowHandle handle) {
        FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
        FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now());

        listener.onFlowStarted(flow);

        List<FlowStep> steps = flow.getSteps();
        int totalSteps = steps.size();

        try {
            for (int i = 0; i < totalSteps; i++) {
                FlowStep step = steps.get(i);
                handle.updateCurrentStep(step.getId());
                listener.onStepStarted(step, i, totalSteps);

                FlowStepResult stepResult = executeStepSequential(step, context, flow.getVariables());
                resultBuilder.addStepResult(stepResult);

                if (stepResult.isSuccessful()) {
                    handle.incrementCompletedSteps();
                    listener.onStepCompleted(step, stepResult);
                } else {
                    listener.onStepFailed(step, stepResult);
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(stepResult.getError())
                            .build();
                    listener.onFlowFailed(flow, failedResult);
                    return failedResult;
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

    private FlowResult executeWithHandlePipelined(TxFlow flow, FlowHandle handle) {
        FlowExecutionContext context = new FlowExecutionContext(flow.getId(), flow.getVariables());
        FlowResult.Builder resultBuilder = FlowResult.builder(flow.getId())
                .startedAt(Instant.now());

        listener.onFlowStarted(flow);

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

                FlowStepResult stepResult = executeStepPipelined(step, context, flow.getVariables());
                stepResults.add(stepResult);
                resultBuilder.addStepResult(stepResult);

                if (stepResult.isSuccessful()) {
                    submittedTxHashes.add(stepResult.getTransactionHash());
                    listener.onTransactionSubmitted(step, stepResult.getTransactionHash());
                } else {
                    listener.onStepFailed(step, stepResult);
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult failedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(stepResult.getError())
                            .build();
                    listener.onFlowFailed(flow, failedResult);
                    return failedResult;
                }
            }

            // Phase 2: Wait for all confirmations
            log.info("PIPELINED mode: Waiting for {} transactions to confirm", submittedTxHashes.size());
            for (int i = 0; i < submittedTxHashes.size(); i++) {
                String txHash = submittedTxHashes.get(i);
                FlowStep step = steps.get(i);

                boolean confirmed = waitForConfirmation(txHash);
                if (confirmed) {
                    handle.incrementCompletedSteps();
                    listener.onTransactionConfirmed(step, txHash);
                    listener.onStepCompleted(step, stepResults.get(i));
                } else {
                    FlowStepResult failedResult = FlowStepResult.failure(step.getId(),
                            new RuntimeException("Transaction confirmation timeout: " + txHash));
                    listener.onStepFailed(step, failedResult);
                    handle.updateStatus(FlowStatus.FAILED);
                    resultBuilder.completedAt(Instant.now());
                    FlowResult flowFailedResult = resultBuilder.withStatus(FlowStatus.FAILED)
                            .withError(failedResult.getError())
                            .build();
                    listener.onFlowFailed(flow, flowFailedResult);
                    return flowFailedResult;
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
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);

            // Get transactions from step (either from TxPlan or AbstractTx)
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
            } else {
                txContext = quickTxBuilder.compose(step.getTx());
                if (signerRegistry != null) {
                    txContext.withSignerRegistry(signerRegistry);
                }
            }

            // Apply step-level signer if set, otherwise use default signer
            if (step.hasSigner()) {
                txContext.withSigner(step.getSigner());
            } else if (defaultSigner != null) {
                txContext.withSigner(defaultSigner);
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
            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);

            // Get transactions from step (either from TxPlan or AbstractTx)
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
            } else {
                txContext = quickTxBuilder.compose(step.getTx());
                if (signerRegistry != null) {
                    txContext.withSignerRegistry(signerRegistry);
                }
            }

            // Apply step-level signer if set, otherwise use default signer
            if (step.hasSigner()) {
                txContext.withSigner(step.getSigner());
            } else if (defaultSigner != null) {
                txContext.withSigner(defaultSigner);
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
                                String unit = policyId + asset.getName();
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
                        utxo.setInlineDatum(HexUtil.encodeHexString(output.getInlineDatum().serializeToHex().getBytes()));
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
}
