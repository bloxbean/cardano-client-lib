package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionPolicy;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.resource.FlowResourceCatalog;

import java.util.Objects;

/**
 * Inputs to the deterministic TxFlow compilation and preflight phase.
 *
 * <p>Bindings default to an empty immutable set and policy defaults to
 * {@link FlowExecutionPolicy#permissive()}. A resource catalog is optional; when
 * omitted, URI authorization still follows the policy but catalog-backed
 * existence, network, and capability checks cannot be performed.</p>
 */
public final class FlowCompilationRequest {
    private final TxFlow definition;
    private final FlowBindings bindings;
    private final FlowResourceCatalog resources;
    private final FlowExecutionPolicy policy;

    private FlowCompilationRequest(Builder builder) {
        this.definition = Objects.requireNonNull(builder.definition, "definition");
        this.bindings = builder.bindings != null ? builder.bindings : FlowBindings.empty();
        this.resources = builder.resources;
        this.policy = builder.policy != null ? builder.policy : FlowExecutionPolicy.permissive();
    }

    /**
     * Creates a request with bindings, no resource catalog, and permissive policy.
     *
     * @param definition reusable flow definition
     * @param bindings runtime values, or {@code null} for none
     * @return compilation request
     */
    public static FlowCompilationRequest of(TxFlow definition, FlowBindings bindings) {
        return builder(definition).bindings(bindings).build();
    }

    /**
     * Starts a request for a flow definition.
     *
     * @param definition reusable flow definition
     * @return request builder
     */
    public static Builder builder(TxFlow definition) {
        return new Builder(definition);
    }

    /**
     * Returns the reusable definition to compile.
     *
     * @return flow definition
     */
    public TxFlow definition() { return definition; }

    /**
     * Returns runtime parameter bindings.
     *
     * @return immutable bindings
     */
    public FlowBindings bindings() { return bindings; }

    /**
     * Returns the resource catalog used for preflight.
     *
     * @return resource catalog, or {@code null} when absent
     */
    public FlowResourceCatalog resources() { return resources; }

    /**
     * Returns the server policy applied during compilation.
     *
     * @return effective compilation policy
     */
    public FlowExecutionPolicy policy() { return policy; }

    /** Builder for optional compilation inputs around a required definition. */
    public static final class Builder {
        private final TxFlow definition;
        private FlowBindings bindings;
        private FlowResourceCatalog resources;
        private FlowExecutionPolicy policy;

        private Builder(TxFlow definition) { this.definition = definition; }

        /**
         * Sets runtime bindings; {@code null} is treated as no bindings.
         *
         * @param value immutable binding set
         * @return this builder
         */
        public Builder bindings(FlowBindings value) { this.bindings = value; return this; }

        /**
         * Sets the optional catalog used to resolve and preflight resource URIs.
         *
         * @param value catalog, or {@code null} to omit catalog-backed checks
         * @return this builder
         */
        public Builder resources(FlowResourceCatalog value) { this.resources = value; return this; }

        /**
         * Sets the execution policy; {@code null} selects the permissive policy.
         *
         * @param value policy to evaluate
         * @return this builder
         */
        public Builder policy(FlowExecutionPolicy value) { this.policy = value; return this; }

        /**
         * Creates the immutable request.
         *
         * @return compilation request
         * @throws NullPointerException if the definition is {@code null}
         */
        public FlowCompilationRequest build() { return new FlowCompilationRequest(this); }
    }
}
