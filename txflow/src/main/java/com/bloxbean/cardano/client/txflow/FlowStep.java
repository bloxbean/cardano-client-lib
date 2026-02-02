package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single step in a transaction flow.
 * <p>
 * A FlowStep can contain either:
 * <ul>
 *     <li>A {@link TxPlan} for YAML-first workflows</li>
 *     <li>An {@link AbstractTx} (Tx or ScriptTx) for Java-first workflows</li>
 * </ul>
 * <p>
 * Steps can declare dependencies on outputs from previous steps using {@link StepDependency}.
 */
@Getter
public class FlowStep {
    private final String id;
    private final String description;
    private final TxPlan txPlan;
    private final AbstractTx<?> tx;
    private final List<StepDependency> dependencies;
    private final TxSigner signer;

    private FlowStep(Builder builder) {
        this.id = builder.id;
        this.description = builder.description;
        this.txPlan = builder.txPlan;
        this.tx = builder.tx;
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(builder.dependencies));
        this.signer = builder.signer;
    }

    /**
     * Check if this step has a TxPlan.
     *
     * @return true if this step uses a TxPlan
     */
    public boolean hasTxPlan() {
        return txPlan != null;
    }

    /**
     * Check if this step has an AbstractTx.
     *
     * @return true if this step uses an AbstractTx
     */
    public boolean hasTx() {
        return tx != null;
    }

    /**
     * Check if this step has any dependencies.
     *
     * @return true if this step depends on other steps
     */
    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    /**
     * Check if this step has a step-level signer.
     *
     * @return true if this step has its own signer
     */
    public boolean hasSigner() {
        return signer != null;
    }

    /**
     * Get the IDs of all steps this step depends on.
     *
     * @return list of dependency step IDs
     */
    public List<String> getDependencyStepIds() {
        List<String> ids = new ArrayList<>();
        for (StepDependency dep : dependencies) {
            ids.add(dep.getStepId());
        }
        return ids;
    }

    /**
     * Create a new builder for a FlowStep.
     *
     * @param id the unique step ID
     * @return a new builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    @Override
    public String toString() {
        return "FlowStep{" +
                "id='" + id + '\'' +
                ", description='" + description + '\'' +
                ", hasTxPlan=" + hasTxPlan() +
                ", hasTx=" + hasTx() +
                ", dependencies=" + dependencies.size() +
                '}';
    }

    /**
     * Builder for FlowStep.
     */
    public static class Builder {
        private final String id;
        private String description;
        private TxPlan txPlan;
        private AbstractTx<?> tx;
        private final List<StepDependency> dependencies = new ArrayList<>();
        private TxSigner signer;

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Step ID cannot be null or empty");
            }
            this.id = id;
        }

        /**
         * Set a description for this step.
         *
         * @param description the step description
         * @return this builder
         */
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set a TxPlan for this step.
         *
         * @param txPlan the transaction plan
         * @return this builder
         */
        public Builder withTxPlan(TxPlan txPlan) {
            if (this.tx != null) {
                throw new IllegalStateException("Cannot set TxPlan when AbstractTx is already set");
            }
            this.txPlan = txPlan;
            return this;
        }

        /**
         * Set an AbstractTx (Tx or ScriptTx) for this step.
         *
         * @param tx the transaction
         * @return this builder
         */
        public Builder withTx(AbstractTx<?> tx) {
            if (this.txPlan != null) {
                throw new IllegalStateException("Cannot set AbstractTx when TxPlan is already set");
            }
            this.tx = tx;
            return this;
        }

        /**
         * Set a step-level signer for this step.
         * <p>
         * Step-level signers take precedence over the default signer set on the FlowExecutor.
         * This is useful when different steps require different signers.
         *
         * @param signer the signer for this step
         * @return this builder
         */
        public Builder withSigner(TxSigner signer) {
            this.signer = signer;
            return this;
        }

        /**
         * Add a dependency on all outputs from a previous step.
         *
         * @param stepId the ID of the step to depend on
         * @return this builder
         */
        public Builder dependsOn(String stepId) {
            return dependsOn(stepId, SelectionStrategy.ALL);
        }

        /**
         * Add a dependency with a specific selection strategy.
         *
         * @param stepId the ID of the step to depend on
         * @param strategy the selection strategy
         * @return this builder
         */
        public Builder dependsOn(String stepId, SelectionStrategy strategy) {
            this.dependencies.add(StepDependency.builder(stepId).withStrategy(strategy).build());
            return this;
        }

        /**
         * Add a dependency on a specific output index from a previous step.
         *
         * @param stepId the ID of the step to depend on
         * @param utxoIndex the output index to use
         * @return this builder
         */
        public Builder dependsOnIndex(String stepId, int utxoIndex) {
            this.dependencies.add(StepDependency.atIndex(stepId, utxoIndex));
            return this;
        }

        /**
         * Add a dependency on the change output from a previous step.
         *
         * @param stepId the ID of the step to depend on
         * @return this builder
         */
        public Builder dependsOnChange(String stepId) {
            this.dependencies.add(StepDependency.change(stepId));
            return this;
        }

        /**
         * Add a custom StepDependency.
         *
         * @param dependency the step dependency
         * @return this builder
         */
        public Builder dependsOn(StepDependency dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        /**
         * Build the FlowStep.
         *
         * @return the built FlowStep
         * @throws IllegalStateException if neither TxPlan nor AbstractTx is set
         */
        public FlowStep build() {
            if (txPlan == null && tx == null) {
                throw new IllegalStateException("FlowStep must have either a TxPlan or AbstractTx");
            }
            return new FlowStep(this);
        }
    }
}
