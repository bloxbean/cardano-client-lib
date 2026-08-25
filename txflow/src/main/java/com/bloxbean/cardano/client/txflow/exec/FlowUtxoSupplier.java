package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.txflow.FlowDependencyException;
import com.bloxbean.cardano.client.txflow.StepDependency;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * A flow-aware UTXO supplier that combines UTXOs from a base supplier
 * with pending UTXOs from previous steps in a transaction flow.
 * <p>
 * This supplier enables transaction chaining by making pending UTXOs from
 * dependent steps available as inputs for the current transaction, even
 * before they are confirmed on the blockchain.
 * <p>
 * Usage:
 * <ul>
 *     <li>Step 1: Uses regular UtxoSupplier (no dependencies)</li>
 *     <li>Step 2: Uses FlowUtxoSupplier that includes outputs from Step 1</li>
 * </ul>
 * <p>
 * This solves the "insufficient funds" issue when a step depends on
 * outputs from previous steps that haven't been confirmed yet.
 */
@Slf4j
@Getter
public class FlowUtxoSupplier implements UtxoSupplier {

    private final UtxoSupplier baseSupplier;
    private final FlowExecutionContext context;
    private final List<StepDependency> dependencies;
    private final Set<String> exactPendingStepIds;
    private final List<String> fundingStepIds;

    /**
     * Create a flow-aware UTXO supplier.
     *
     * @param baseSupplier the underlying UTXO supplier to delegate to
     * @param context the flow execution context containing step results
     * @param dependencies the list of step dependencies for UTXO resolution
     */
    public FlowUtxoSupplier(UtxoSupplier baseSupplier,
                            FlowExecutionContext context,
                            List<StepDependency> dependencies) {
        this(baseSupplier, context, dependencies, Set.of(), List.of());
    }

    FlowUtxoSupplier(UtxoSupplier baseSupplier,
                     FlowExecutionContext context,
                     List<StepDependency> dependencies,
                     Set<String> exactPendingStepIds) {
        this(baseSupplier, context, dependencies, exactPendingStepIds, List.of());
    }

    FlowUtxoSupplier(UtxoSupplier baseSupplier,
                     FlowExecutionContext context,
                     List<StepDependency> dependencies,
                     Set<String> exactPendingStepIds,
                     List<String> fundingStepIds) {
        this.baseSupplier = baseSupplier;
        this.context = context;
        this.dependencies = new ArrayList<>(dependencies);
        this.exactPendingStepIds = Set.copyOf(exactPendingStepIds);
        this.fundingStepIds = List.copyOf(fundingStepIds);
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        // Get UTXOs from base supplier
        List<Utxo> baseUtxos = baseSupplier.getPage(address, nrOfItems, page, order);

        // Filter out spent UTXOs from base supplier
        List<Utxo> unspentBaseUtxos = filterOutSpentUtxos(baseUtxos);

        // Add pending UTXOs from dependent steps for this address (only on first page)
        List<Utxo> pendingUtxos = page <= 1 ? resolvePendingUtxosForAddress(address) : Collections.emptyList();

        // Combine and return (unspent base UTXOs first, then pending)
        Set<Utxo> combinedUtxos = new LinkedHashSet<>(unspentBaseUtxos);
        combinedUtxos.addAll(pendingUtxos);

        return new ArrayList<>(combinedUtxos);
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        // First try to get from base supplier
        Optional<Utxo> baseResult = baseSupplier.getTxOutput(txHash, outputIndex);
        if (baseResult.isPresent()) {
            return baseResult;
        }

        // If not found in base, check pending UTXOs from flow context
        return findPendingUtxo(txHash, outputIndex);
    }

    @Override
    public List<Utxo> getAll(String address) {
        // Get all UTXOs from base supplier
        List<Utxo> baseUtxos = baseSupplier.getAll(address);

        // Filter out spent UTXOs from base supplier
        List<Utxo> unspentBaseUtxos = filterOutSpentUtxos(baseUtxos);

        // Add all pending UTXOs for this address
        List<Utxo> pendingUtxos = resolvePendingUtxosForAddress(address);

        // Combine and return
        List<Utxo> allUtxos = new ArrayList<>(unspentBaseUtxos);
        allUtxos.addAll(pendingUtxos);

        return allUtxos;
    }

    @Override
    public boolean isUsedAddress(Address address) {
        // Delegate to base supplier
        return baseSupplier.isUsedAddress(address);
    }

    @Override
    public void setSearchByAddressVkh(boolean flag) {
        // Delegate to base supplier
        baseSupplier.setSearchByAddressVkh(flag);
    }

    /**
     * Resolve pending UTXOs from dependent steps that belong to the specified address.
     *
     * @param address the address to filter UTXOs for
     * @return list of pending UTXOs for the address
     */
    private List<Utxo> resolvePendingUtxosForAddress(String address) {
        Set<Utxo> pendingUtxos = new LinkedHashSet<>();

        for (StepDependency dependency : dependencies) {
            try {
                // Get all UTXOs from the dependency step
                List<Utxo> selectedUtxos = dependency.resolveUtxos(context);

                if (selectedUtxos.isEmpty()) {
                    if (!dependency.isOptional()) {
                        log.warn("Required dependency step '{}' has no outputs yet", dependency.getStepId());
                    }
                    continue;
                }

                // Filter for the specific address
                for (Utxo utxo : selectedUtxos) {
                    if (address.equals(utxo.getAddress())) {
                        pendingUtxos.add(utxo);
                        if (log.isDebugEnabled()) {
                            log.debug("Adding pending UTXO from step '{}': {}#{} -> {}",
                                    dependency.getStepId(), utxo.getTxHash(), utxo.getOutputIndex(), address);
                        }
                    }
                }

            } catch (Exception e) {
                if (!dependency.isOptional()) {
                    log.error("Failed to resolve required dependency '{}'", dependency.getStepId(), e);
                    throw new FlowDependencyException(
                            "Failed to resolve required dependency '" + dependency.getStepId() + "'", e);
                } else {
                    log.warn("Optional dependency '{}' failed: {}", dependency.getStepId(), e.getMessage());
                }
            }
        }

        // Portable funding relationships expose predecessor outputs only to
        // ordinary address-based selection. Unlike an exact flow_output
        // reference, this never makes a differently-addressed output eligible.
        for (String stepId : fundingStepIds) {
            for (Utxo utxo : context.getStepOutputs(stepId)) {
                if (address.equals(utxo.getAddress())) {
                    pendingUtxos.add(utxo);
                }
            }
        }

        return new ArrayList<>(pendingUtxos);
    }

    /**
     * Find a specific pending UTXO by transaction hash and output index.
     *
     * @param txHash the transaction hash
     * @param outputIndex the output index
     * @return the UTXO if found in pending outputs
     */
    private Optional<Utxo> findPendingUtxo(String txHash, int outputIndex) {
        for (StepDependency dependency : dependencies) {
            try {
                List<Utxo> stepOutputs = dependency.resolveUtxos(context);

                for (Utxo utxo : stepOutputs) {
                    if (txHash.equals(utxo.getTxHash()) && outputIndex == utxo.getOutputIndex()) {
                        return Optional.of(utxo);
                    }
                }
            } catch (Exception e) {
                if (dependency.isOptional()) {
                    log.warn("Optional dependency '{}' failed while resolving {}#{}: {}",
                            dependency.getStepId(), txHash, outputIndex, e.getMessage());
                } else {
                    log.error("Required dependency '{}' failed while resolving {}#{}",
                            dependency.getStepId(), txHash, outputIndex, e);
                    throw new FlowDependencyException(
                            "Failed to resolve required dependency '" + dependency.getStepId() + "'", e);
                }
            }
        }

        // Portable flow_output references are exact identities, not address-selection
        // dependencies. Search all outputs only for the producer steps explicitly named
        // by those references, leaving legacy dependency selection semantics unchanged.
        for (String stepId : exactPendingStepIds) {
            for (Utxo utxo : context.getStepOutputs(stepId)) {
                if (txHash.equals(utxo.getTxHash()) && outputIndex == utxo.getOutputIndex()) {
                    return Optional.of(utxo);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Filter out UTXOs that have been spent in previous steps of the flow.
     * <p>
     * This method compares the given UTXOs against all spent inputs recorded
     * in the flow context and removes any that have been consumed.
     *
     * @param utxos the list of UTXOs to filter
     * @return a new list containing only unspent UTXOs
     */
    private List<Utxo> filterOutSpentUtxos(List<Utxo> utxos) {
        if (utxos == null || utxos.isEmpty()) {
            return new ArrayList<>();
        }

        // Get all spent inputs from previous steps in the flow
        List<TransactionInput> spentInputs = context.getAllSpentInputs();
        if (spentInputs.isEmpty()) {
            return new ArrayList<>(utxos); // No filtering needed
        }

        List<Utxo> unspentUtxos = new ArrayList<>();
        int filteredCount = 0;

        for (Utxo utxo : utxos) {
            boolean isSpent = false;

            // Check if this UTXO has been spent by any previous step
            for (TransactionInput spentInput : spentInputs) {
                if (utxo.getTxHash().equals(spentInput.getTransactionId()) &&
                        utxo.getOutputIndex() == spentInput.getIndex()) {
                    isSpent = true;
                    filteredCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("Filtered out spent UTXO: {}#{} (spent by previous step)",
                                utxo.getTxHash(), utxo.getOutputIndex());
                    }
                    break;
                }
            }

            if (!isSpent) {
                unspentUtxos.add(utxo);
            }
        }

        if (filteredCount > 0 && log.isDebugEnabled()) {
            log.debug("Filtered out {} spent UTXOs from {} total UTXOs", filteredCount, utxos.size());
        }

        return unspentUtxos;
    }
}
