package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Represents a single step in a transaction flow.
 * <p>
 * A FlowStep can contain either:
 * <ul>
 *     <li>A {@link TxPlan} for YAML-first workflows</li>
 *     <li>A factory function that creates a configured {@link QuickTxBuilder.TxContext} for Java-first workflows</li>
 * </ul>
 * <p>
 * The factory function pattern allows full access to all TxContext configuration methods
 * (feePayer, collateralPayer, validFrom, etc.) without FlowStep needing to duplicate them.
 * <p>
 * Steps can declare dependencies on outputs from previous steps using {@link StepDependency}.
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Simple payment
 * FlowStep.builder("payment")
 *     .withTxContext(builder -> builder
 *         .compose(new Tx()
 *             .payToAddress(receiver, Amount.ada(10))
 *             .from(sender))
 *         .withSigner(SignerProviders.signerFrom(account)))
 *     .build()
 *
 * // ScriptTx with full configuration
 * FlowStep.builder("unlock")
 *     .withTxContext(builder -> builder
 *         .compose(new ScriptTx()
 *             .collectFrom(scriptUtxo, redeemer)
 *             .payToAddress(receiver, Amount.ada(5))
 *             .attachSpendingValidator(script))
 *         .feePayer(feeAddr)
 *         .collateralPayer(collateralAddr)
 *         .withSigner(signer)
 *         .validFrom(currentSlot))
 *     .dependsOn("lock")
 *     .build()
 * }</pre>
 */
@Getter
public class FlowStep {
    private final String id;
    private final String description;
    private final TxPlan txPlan;
    private final Function<QuickTxBuilder, QuickTxBuilder.TxContext> txContextFactory;
    private final List<StepDependency> dependencies;
    private final RetryPolicy retryPolicy;

    private FlowStep(Builder builder) {
        this.id = builder.id;
        this.description = builder.description;
        this.txPlan = builder.txPlan;
        this.txContextFactory = builder.txContextFactory;
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(builder.dependencies));
        this.retryPolicy = builder.retryPolicy;
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
     * Check if this step has a TxContext factory.
     *
     * @return true if this step uses a TxContext factory
     */
    public boolean hasTxContextFactory() {
        return txContextFactory != null;
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
     * Check if this step has a step-level retry policy.
     *
     * @return true if this step has its own retry policy
     */
    public boolean hasRetryPolicy() {
        return retryPolicy != null;
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
                ", hasTxContextFactory=" + hasTxContextFactory() +
                ", dependencies=" + dependencies.size() +
                ", hasRetryPolicy=" + hasRetryPolicy() +
                '}';
    }

    /**
     * Builder for FlowStep.
     */
    public static class Builder {
        private final String id;
        private String description;
        private TxPlan txPlan;
        private Function<QuickTxBuilder, QuickTxBuilder.TxContext> txContextFactory;
        private final List<StepDependency> dependencies = new ArrayList<>();
        private RetryPolicy retryPolicy;

        private Builder(String id) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Step ID cannot be null, empty, or whitespace");
            }
            this.id = id.trim();
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
         * Set a TxPlan for this step (YAML-first workflow).
         *
         * @param txPlan the transaction plan
         * @return this builder
         */
        public Builder withTxPlan(TxPlan txPlan) {
            if (this.txContextFactory != null) {
                throw new IllegalStateException("Cannot set TxPlan when TxContext factory is already set");
            }
            this.txPlan = txPlan;
            return this;
        }

        /**
         * Set a TxContext factory function for this step (Java-first workflow).
         * <p>
         * The factory function receives a {@link QuickTxBuilder} configured with the appropriate
         * UTXO supplier (including pending UTXOs from previous steps) and should return a
         * fully-configured {@link QuickTxBuilder.TxContext}.
         * <p>
         * This gives full access to all TxContext configuration methods like
         * feePayer(), collateralPayer(), validFrom(), withSigner(), etc.
         * </p>
         * Example:
         * <pre>{@code
         * .withTxContext(builder -> builder
         *     .compose(new ScriptTx()
         *         .collectFrom(utxo, redeemer)
         *         .payToAddress(receiver, amount)
         *         .attachSpendingValidator(script))
         *     .feePayer(feePayerAddr)
         *     .collateralPayer(collateralAddr)
         *     .withSigner(signer)
         *     .validFrom(slot))
         * }</pre>
         *
         * @param txContextFactory a function that creates a configured TxContext
         * @return this builder
         */
        public Builder withTxContext(Function<QuickTxBuilder, QuickTxBuilder.TxContext> txContextFactory) {
            if (this.txPlan != null) {
                throw new IllegalStateException("Cannot set TxContext factory when TxPlan is already set");
            }
            this.txContextFactory = txContextFactory;
            return this;
        }

        /**
         * Set a step-level retry policy for this step.
         * <p>
         * Step-level retry policies take precedence over the default retry policy set on the FlowExecutor.
         * This is useful when different steps require different retry behavior.
         *
         * @param retryPolicy the retry policy for this step
         * @return this builder
         */
        public Builder withRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
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
         * @throws IllegalStateException if neither TxPlan nor TxContext factory is set
         */
        public FlowStep build() {
            if (txPlan == null && txContextFactory == null) {
                throw new IllegalStateException("FlowStep must have either a TxPlan or TxContext factory");
            }
            return new FlowStep(this);
        }
    }
}
