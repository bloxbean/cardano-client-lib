package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionContext;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents a dependency on outputs from a previous step in a transaction flow.
 * <p>
 * This class encapsulates the relationship between a step and the outputs it
 * depends on from previous steps, along with the strategy for selecting which
 * specific UTXOs to use.
 */
@Getter
public class StepDependency {
    private final String stepId;
    private final SelectionStrategy strategy;
    private final Integer utxoIndex;
    private final Predicate<Utxo> filterPredicate;
    private final boolean optional;

    private StepDependency(Builder builder) {
        this.stepId = builder.stepId;
        this.strategy = builder.strategy;
        this.utxoIndex = builder.utxoIndex;
        this.filterPredicate = builder.filterPredicate;
        this.optional = builder.optional;
    }

    /**
     * Resolve this dependency using the flow context to get the actual UTXOs.
     *
     * @param context the flow execution context containing step results
     * @return the list of UTXOs selected by this dependency
     * @throws FlowDependencyException if the dependency cannot be resolved and is not optional
     */
    public List<Utxo> resolveUtxos(FlowExecutionContext context) {
        List<Utxo> stepOutputs = context.getStepOutputs(stepId);

        if (stepOutputs.isEmpty() && !optional) {
            throw new FlowDependencyException("Required step '" + stepId + "' has no outputs available");
        }

        return selectUtxos(stepOutputs);
    }

    /**
     * Apply the selection strategy to select UTXOs from the available outputs.
     *
     * @param availableUtxos the UTXOs available from the previous step
     * @return the selected UTXOs
     */
    private List<Utxo> selectUtxos(List<Utxo> availableUtxos) {
        if (availableUtxos == null || availableUtxos.isEmpty()) {
            return Collections.emptyList();
        }

        switch (strategy) {
            case ALL:
                return new ArrayList<>(availableUtxos);

            case INDEX:
                if (utxoIndex == null || utxoIndex < 0 || utxoIndex >= availableUtxos.size()) {
                    if (!optional) {
                        throw new FlowDependencyException(
                            "UTXO index " + utxoIndex + " is out of bounds for step '" + stepId +
                            "' which has " + availableUtxos.size() + " outputs");
                    }
                    return Collections.emptyList();
                }
                return Collections.singletonList(availableUtxos.get(utxoIndex));

            case FILTER:
                return applyFilter(availableUtxos);

            default:
                return new ArrayList<>(availableUtxos);
        }
    }

    private List<Utxo> applyFilter(List<Utxo> availableUtxos) {
        List<Utxo> filtered = new ArrayList<>();

        for (Utxo utxo : availableUtxos) {
            boolean matches = true;

            // Apply filterPredicate if present
            if (filterPredicate != null) {
                matches = filterPredicate.test(utxo);
            }

            if (matches) {
                filtered.add(utxo);
            }
        }

        return filtered;
    }

    /**
     * Create a dependency that uses all outputs from the specified step.
     *
     * @param stepId the ID of the step to depend on
     * @return a new StepDependency
     */
    public static StepDependency all(String stepId) {
        return builder(stepId).withStrategy(SelectionStrategy.ALL).build();
    }

    /**
     * Create a dependency that uses a specific output by index.
     *
     * @param stepId the ID of the step to depend on
     * @param index the output index to use
     * @return a new StepDependency
     */
    public static StepDependency atIndex(String stepId, int index) {
        return builder(stepId).withStrategy(SelectionStrategy.INDEX).withUtxoIndex(index).build();
    }

    /**
     * Create a dependency with a predicate filter.
     *
     * @param stepId the ID of the step to depend on
     * @param predicate the filter predicate
     * @return a new StepDependency
     */
    public static StepDependency filter(String stepId, Predicate<Utxo> predicate) {
        return builder(stepId).withStrategy(SelectionStrategy.FILTER).withFilterPredicate(predicate).build();
    }

    /**
     * Create a new builder for a StepDependency.
     *
     * @param stepId the ID of the step to depend on
     * @return a new builder
     */
    public static Builder builder(String stepId) {
        return new Builder(stepId);
    }

    @Override
    public String toString() {
        return "StepDependency{" +
                "stepId='" + stepId + '\'' +
                ", strategy=" + strategy +
                (utxoIndex != null ? ", utxoIndex=" + utxoIndex : "") +
                ", optional=" + optional +
                '}';
    }

    /**
     * Builder for StepDependency.
     */
    public static class Builder {
        private final String stepId;
        private SelectionStrategy strategy = SelectionStrategy.ALL;
        private Integer utxoIndex;
        private Predicate<Utxo> filterPredicate;
        private boolean optional = false;

        private Builder(String stepId) {
            if (stepId == null || stepId.isEmpty()) {
                throw new IllegalArgumentException("Step ID cannot be null or empty");
            }
            this.stepId = stepId;
        }

        public Builder withStrategy(SelectionStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder withUtxoIndex(int index) {
            this.utxoIndex = index;
            return this;
        }

        public Builder withFilterPredicate(Predicate<Utxo> predicate) {
            this.filterPredicate = predicate;
            return this;
        }

        public Builder optional() {
            this.optional = true;
            return this;
        }

        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public StepDependency build() {
            // Validate configuration
            if (strategy == SelectionStrategy.INDEX && utxoIndex == null) {
                throw new IllegalStateException("INDEX strategy requires utxoIndex to be set");
            }
            return new StepDependency(this);
        }
    }
}
