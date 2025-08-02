package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.ChainContext;
import com.bloxbean.cardano.client.watcher.chain.StepResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single executable step in a transaction chain.
 *
 * A WatchableStep encapsulates a WatchableTxContext along with execution
 * state and metadata needed for chain execution and monitoring.
 */
public class WatchableStep {
    private final WatchableQuickTxBuilder.WatchableTxContext txContext;
    private final String stepId;
    private final String description;

    // Execution state
    private WatchStatus status = WatchStatus.PENDING;
    private String transactionHash;
    private List<Utxo> outputUtxos;
    private Throwable lastError;
    private int retryCount = 0;
    private Transaction builtTransaction;

    /**
     * Create a WatchableStep from a WatchableTxContext.
     *
     * @param txContext the transaction context to wrap
     */
    public WatchableStep(WatchableQuickTxBuilder.WatchableTxContext txContext) {
        this.txContext = txContext;
        this.stepId = txContext.getStepId();
        this.description = txContext.getDescription();
    }

    /**
     * Get the step ID.
     *
     * @return the step ID
     */
    public String getStepId() {
        return stepId;
    }

    /**
     * Get the step description.
     *
     * @return the description, or null if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the underlying transaction context.
     *
     * @return the transaction context
     */
    public WatchableQuickTxBuilder.WatchableTxContext getTxContext() {
        return txContext;
    }

    /**
     * Get the current execution status.
     *
     * @return the current status
     */
    public WatchStatus getStatus() {
        return status;
    }

    /**
     * Set the execution status.
     *
     * @param status the new status
     */
    public void setStatus(WatchStatus status) {
        this.status = status;
    }

    /**
     * Get the transaction hash if the step has been submitted.
     *
     * @return the transaction hash, or null if not submitted yet
     */
    public String getTransactionHash() {
        return transactionHash;
    }

    /**
     * Set the transaction hash.
     *
     * @param transactionHash the transaction hash
     */
    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    /**
     * Get the output UTXOs if the step has completed successfully.
     *
     * @return the output UTXOs, or null if not completed
     */
    public List<Utxo> getOutputUtxos() {
        return outputUtxos;
    }

    /**
     * Set the output UTXOs.
     *
     * @param outputUtxos the output UTXOs
     */
    public void setOutputUtxos(List<Utxo> outputUtxos) {
        this.outputUtxos = outputUtxos;
    }

    /**
     * Get the last error if the step failed.
     *
     * @return the last error, or null if no error
     */
    public Throwable getLastError() {
        return lastError;
    }

    /**
     * Set the last error.
     *
     * @param lastError the error
     */
    public void setLastError(Throwable lastError) {
        this.lastError = lastError;
    }

    /**
     * Get the retry count.
     *
     * @return the number of retries attempted
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Increment the retry count.
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Execute this step within the given chain context.
     *
     * This method handles:
     * - UTXO dependency resolution from previous steps
     * - Transaction building using the wrapped TxContext
     * - Transaction submission
     * - Result recording in the chain context
     *
     * @param chainContext the chain context for UTXO resolution and result storage
     * @return the execution result
     */
    public StepResult execute(ChainContext chainContext) {
        try {
            setStatus(WatchStatus.BUILDING);

            // Validate that all required dependencies are ready
            validateDependencies(chainContext);

            setStatus(WatchStatus.SUBMITTED);

            // Build and submit transaction using appropriate UtxoSupplier
            Result<String> result = executeTransaction(chainContext);

            if (result.isSuccessful()) {
                setTransactionHash(result.getValue());

                // Capture output UTXOs from the transaction
                List<Utxo> outputUtxos = captureOutputUtxos(result.getValue());
                setOutputUtxos(outputUtxos);

                StepResult stepResult = new StepResult(stepId, WatchStatus.CONFIRMED, result.getValue(), outputUtxos);
                chainContext.recordStepResult(stepId, stepResult);

                setStatus(WatchStatus.CONFIRMED);
                return stepResult;
            } else {
                setStatus(WatchStatus.FAILED);
                setLastError(new RuntimeException("Transaction failed: " + result.getResponse()));

                StepResult stepResult = StepResult.failure(stepId,
                    new RuntimeException(result.getResponse()));
                chainContext.recordStepResult(stepId, stepResult);
                return stepResult;
            }

        } catch (Exception e) {
            e.printStackTrace();
            setStatus(WatchStatus.FAILED);
            setLastError(e);
            incrementRetryCount();

            StepResult stepResult = StepResult.failure(stepId, e);
            chainContext.recordStepResult(stepId, stepResult);
            return stepResult;
        }
    }

    /**
     * Validate that all required dependencies are ready for this step.
     * 
     * This method checks that all required dependency steps have completed successfully
     * and have output UTXOs available. The actual UTXO resolution is now handled by
     * the ChainAwareUtxoSupplier during transaction building.
     * 
     * @param chainContext the chain context to check dependencies against
     * @throws UtxoDependencyException if any required dependency is not ready
     */
    private void validateDependencies(ChainContext chainContext) {
        List<StepOutputDependency> dependencies = txContext.getUtxoDependencies();
        if (dependencies.isEmpty()) {
            return; // No dependencies to validate
        }

        for (StepOutputDependency dependency : dependencies) {
            String dependencyStepId = dependency.getStepId();

            // Check if the dependency step has completed successfully
            if (!chainContext.hasSuccessfulStep(dependencyStepId)) {
                if (!dependency.isOptional()) {
                    throw new UtxoDependencyException(
                        "Required dependency step '" + dependencyStepId + "' has not completed successfully");
                } else {
                    System.out.println("⚠️  Optional dependency step '" + dependencyStepId + "' is not ready, continuing anyway");
                    continue;
                }
            }

            // For required dependencies, check that UTXOs are available
            if (!dependency.isOptional()) {
                List<Utxo> stepOutputs = chainContext.getStepOutputs(dependencyStepId);
                if (stepOutputs == null || stepOutputs.isEmpty()) {
                    throw new UtxoDependencyException(
                        "Required dependency step '" + dependencyStepId + "' has no output UTXOs available");
                }
                
                System.out.println("✅ Dependency '" + dependencyStepId + "' is ready with " 
                    + stepOutputs.size() + " output UTXOs");
            }
        }
    }

    /**
     * Execute the transaction using the wrapped TxContext with chain-aware UTXO supplier.
     *
     * @param chainContext the chain context for UTXO resolution
     * @return the transaction submission result
     */
    private Result<String> executeTransaction(ChainContext chainContext) {
        try {
            // Determine the appropriate UtxoSupplier and create effective builder
            UtxoSupplier effectiveSupplier = determineUtxoSupplier(chainContext);
            QuickTxBuilder.TxContext effectiveContext = createEffectiveTxContext(effectiveSupplier);
            
            // Build, sign and submit the transaction using complete()
            TxResult result = effectiveContext.complete();
            System.out.println("🔗 Transaction submitted: " + result.getTxHash() + " for step '" + stepId + "'");

            // If successful, we need to rebuild the transaction to get access to outputs
            // This is necessary because complete() doesn't give us access to the built transaction
            if (result.isSuccessful()) {
                try {
                    // Rebuild the transaction to get access to outputs using effective context
                    this.builtTransaction = effectiveContext.buildAndSign();
                } catch (Exception e) {
                    System.out.println("⚠️  Warning: Could not rebuild transaction for UTXO extraction: " + e.getMessage());
                    // Continue anyway since the transaction was successfully submitted
                }

                return Result.success(result.getTxHash()).withValue(result.getTxHash());
            } else {
                return Result.error(result.getResponse());
            }

        } catch (Exception e) {
            // Convert exception to failed Result
            return Result.error("Transaction execution failed: " + e.getMessage());
        }
    }

    /**
     * Capture output UTXOs from a submitted transaction.
     *
     * This extracts the actual transaction outputs from the built transaction
     * and converts them to Utxo objects for use by dependent steps.
     *
     * @param transactionHash the transaction hash
     * @return list of output UTXOs from the transaction
     */
    private List<Utxo> captureOutputUtxos(String transactionHash) {
        List<Utxo> outputUtxos = new ArrayList<>();

        if (builtTransaction == null || builtTransaction.getBody() == null) {
            System.out.println("⚠️  No built transaction available for step '" + stepId + "'");
            return outputUtxos;
        }

        try {
            List<TransactionOutput> outputs = builtTransaction.getBody().getOutputs();
            if (outputs == null || outputs.isEmpty()) {
                System.out.println("⚠️  No outputs found in transaction for step '" + stepId + "'");
                return outputUtxos;
            }

            // Convert each TransactionOutput to a Utxo
            for (int i = 0; i < outputs.size(); i++) {
                TransactionOutput output = outputs.get(i);

                Utxo utxo = new Utxo();
                utxo.setTxHash(transactionHash);
                utxo.setOutputIndex(i);
                utxo.setAddress(output.getAddress());

                // Convert Value to List<Amount>
                if (output.getValue() != null) {
                    List<com.bloxbean.cardano.client.api.model.Amount> amounts = new ArrayList<>();

                    // Add lovelace amount
                    if (output.getValue().getCoin() != null) {
                        amounts.add(com.bloxbean.cardano.client.api.model.Amount.lovelace(output.getValue().getCoin()));
                    }

                    // Add multi-asset amounts if present
                    if (output.getValue().getMultiAssets() != null) {
                        for (com.bloxbean.cardano.client.transaction.spec.MultiAsset multiAsset : output.getValue().getMultiAssets()) {
                            String policyId = multiAsset.getPolicyId();
                            for (com.bloxbean.cardano.client.transaction.spec.Asset asset : multiAsset.getAssets()) {
                                amounts.add(com.bloxbean.cardano.client.api.model.Amount.asset(policyId, asset.getName(), asset.getValue()));
                            }
                        }
                    }

                    utxo.setAmount(amounts);
                }

                // Set data hash if present
                if (output.getDatumHash() != null) {
                    utxo.setDataHash(com.bloxbean.cardano.client.util.HexUtil.encodeHexString(output.getDatumHash()));
                }

                outputUtxos.add(utxo);
                System.out.println("📦 Captured UTXO for step '" + stepId + "': " + transactionHash + "#" + i + " (" + output.getAddress() + ")");
            }

        } catch (Exception e) {
            System.out.println("⚠️  Failed to capture output UTXOs for step '" + stepId + "': " + e.getMessage());
            e.printStackTrace();
        }

        return outputUtxos;
    }


    /**
     * Determine the appropriate UtxoSupplier to use based on step dependencies.
     * 
     * @param chainContext the chain context for dependency resolution
     * @return the appropriate UtxoSupplier (base or chain-aware)
     */
    private UtxoSupplier determineUtxoSupplier(ChainContext chainContext) {
        List<StepOutputDependency> dependencies = txContext.getUtxoDependencies();
        
        // Get the base UtxoSupplier from the parent builder
        WatchableQuickTxBuilder parentBuilder = txContext.getParentBuilder();
        UtxoSupplier baseSupplier = parentBuilder.getOriginalUtxoSupplier();
        
        // If no explicit UtxoSupplier was provided, create one from BackendService
        if (baseSupplier == null && parentBuilder.getBackendService() != null) {
            baseSupplier = new DefaultUtxoSupplier(parentBuilder.getBackendService().getUtxoService());
        }
        
        if (dependencies.isEmpty()) {
            // No dependencies, use the base supplier
            return baseSupplier;
        } else {
            // Has dependencies, create chain-aware supplier that includes pending UTXOs
            System.out.println("🔗 Step '" + stepId + "' has " + dependencies.size() + 
                " UTXO dependencies, using ChainAwareUtxoSupplier");
            
            return new ChainAwareUtxoSupplier(
                baseSupplier,       // base supplier
                chainContext,       // chain context
                dependencies        // specific dependencies for this step
            );
        }
    }
    
    /**
     * Create an effective TxContext with the appropriate UtxoSupplier.
     * 
     * This creates a new QuickTxBuilder with the right UtxoSupplier and copies
     * all the configuration from the original TxContext.
     * 
     * @param utxoSupplier the UtxoSupplier to use
     * @return a new TxContext with the specified UtxoSupplier
     */
    private QuickTxBuilder.TxContext createEffectiveTxContext(UtxoSupplier utxoSupplier) {
        // Get the parent WatchableQuickTxBuilder to access services
        WatchableQuickTxBuilder parentBuilder = txContext.getParentBuilder();
        
        // Create a new QuickTxBuilder with the specified UtxoSupplier
        QuickTxBuilder newBuilder;
        if (parentBuilder.getBackendService() != null) {
            // If originally created with BackendService, use that approach
            newBuilder = new QuickTxBuilder(parentBuilder.getBackendService(), utxoSupplier);
        } else {
            // If originally created with individual services
            newBuilder = new QuickTxBuilder(
                utxoSupplier, 
                parentBuilder.getProtocolParamsSupplier(), 
                parentBuilder.getTransactionProcessor()
            );
        }
        
        // Apply the same Tx configuration from the WatchableTxContext
        // Note: We need to copy the configuration, not the UTXOs, since UTXOs 
        // will be resolved by the new UtxoSupplier
        QuickTxBuilder.TxContext newContext = newBuilder.compose(txContext.getOriginalTxs());
        
        // Apply stored configurations to the new context
        if (txContext.getStoredSigner() != null) {
            newContext.withSigner(txContext.getStoredSigner());
            System.out.println("🔑 Applied stored signer to effective context for step '" + stepId + "'");
        }
        
        if (txContext.getStoredFeePayer() != null) {
            newContext.feePayer(txContext.getStoredFeePayer());
            System.out.println("💰 Applied stored fee payer '" + txContext.getStoredFeePayer() + "' to effective context for step '" + stepId + "'");
        }
        
        return newContext;
    }

    @Override
    public String toString() {
        return "WatchableStep{" +
                "stepId='" + stepId + '\'' +
                ", description='" + description + '\'' +
                ", status=" + status +
                ", transactionHash='" + transactionHash + '\'' +
                ", retryCount=" + retryCount +
                '}';
    }
}
