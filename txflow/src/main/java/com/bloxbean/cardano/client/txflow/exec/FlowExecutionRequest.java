package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.store.FlowStoreTextPolicy;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Immutable command submitted to {@link FlowEngine}.
 *
 * <p>The request combines a portable flow definition with run-specific
 * bindings and server-owned execution metadata. Idempotency keys identify the
 * logical operation; spending-resource identities and secure binding references
 * contribute to its request fingerprint. These values must therefore remain
 * consistent across caller retries. The complete application namespace and key
 * limits defined by {@link FlowStoreTextPolicy} are available to callers; the
 * engine's internal execution-claim encoding does not consume either limit.</p>
 */
public final class FlowExecutionRequest {
    private final String executionId;
    private final TxFlow definition;
    private final FlowBindings bindings;
    private final String idempotencyNamespace;
    private final String idempotencyKey;
    private final Set<String> spendingResources;
    private final boolean allowConcurrentSpending;
    private final Map<String, String> secureBindingReferences;

    private FlowExecutionRequest(Builder builder) {
        this.executionId = builder.executionId != null ? builder.executionId : UUID.randomUUID().toString();
        this.definition = Objects.requireNonNull(builder.definition, "definition");
        this.bindings = builder.bindings != null ? builder.bindings : FlowBindings.empty();
        this.idempotencyNamespace = builder.idempotencyNamespace;
        this.idempotencyKey = builder.idempotencyKey;
        this.spendingResources = Set.copyOf(builder.spendingResources);
        this.allowConcurrentSpending = builder.allowConcurrentSpending;
        this.secureBindingReferences = Map.copyOf(builder.secureBindingReferences);
        if ((idempotencyNamespace == null) != (idempotencyKey == null)) {
            throw new IllegalStateException("idempotency namespace and key must be provided together");
        }
        FlowStoreTextPolicy.requireIdentifier(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        if (idempotencyNamespace != null) {
            FlowStoreTextPolicy.requireIdentifier(
                    idempotencyNamespace, "idempotency namespace",
                    FlowStoreTextPolicy.MAX_NAMESPACE_BYTES);
            FlowStoreTextPolicy.requireIdentifier(
                    idempotencyKey, "idempotency key",
                    FlowStoreTextPolicy.MAX_IDEMPOTENCY_KEY_BYTES);
        }
    }

    /**
     * Starts a builder for a flow definition.
     *
     * @param definition flow to compile and execute
     * @return request builder
     */
    public static Builder builder(TxFlow definition) {
        return new Builder(definition);
    }

    /**
     * Returns the execution correlation identifier.
     *
     * @return caller-supplied execution ID, or the generated ID
     */
    public String getExecutionId() { return executionId; }

    /**
     * Returns the portable definition to compile.
     *
     * @return flow definition
     */
    public TxFlow getDefinition() { return definition; }

    /**
     * Returns the run-time parameter bindings.
     *
     * @return immutable bindings
     */
    public FlowBindings getBindings() { return bindings; }

    /**
     * Returns the application-defined idempotency namespace.
     * The engine preserves this value without adding an internal prefix.
     *
     * @return namespace, or {@code null} when not requested
     */
    public String getIdempotencyNamespace() { return idempotencyNamespace; }

    /**
     * Returns the idempotency key within its namespace.
     *
     * @return key, or {@code null} when not requested
     */
    public String getIdempotencyKey() { return idempotencyKey; }

    /**
     * Returns the canonical spending-resource identities.
     *
     * @return immutable identity set
     */
    public Set<String> getSpendingResources() { return spendingResources; }

    /**
     * Reports whether the request opts out of spending-resource serialization.
     *
     * @return {@code true} when concurrent spending is requested
     */
    public boolean isConcurrentSpendingAllowed() { return allowConcurrentSpending; }

    /**
     * Returns external references for sensitive parameters.
     *
     * @return immutable references keyed by parameter name
     */
    public Map<String, String> getSecureBindingReferences() { return secureBindingReferences; }

    /** Builds an immutable {@link FlowExecutionRequest}. */
    public static final class Builder {
        private String executionId;
        private final TxFlow definition;
        private FlowBindings bindings;
        private String idempotencyNamespace;
        private String idempotencyKey;
        private final Set<String> spendingResources = new TreeSet<>();
        private boolean allowConcurrentSpending;
        private final Map<String, String> secureBindingReferences = new LinkedHashMap<>();

        private Builder(TxFlow definition) { this.definition = definition; }

        /**
         * Uses a stable execution identifier instead of a generated UUID.
         *
         * @param value non-blank execution identifier
         * @return this builder
         */
        public Builder executionId(String value) { this.executionId = value; return this; }

        /**
         * Supplies run-specific portable parameter values.
         *
         * @param value bindings validated by compilation
         * @return this builder
         */
        public Builder bindings(FlowBindings value) { this.bindings = value; return this; }

        /**
         * Associates this logical operation with a namespaced idempotency key.
         * Reusing the key with a different request fingerprint is rejected.
         * Namespace and key limits are measured in UTF-8 bytes according to
         * {@link FlowStoreTextPolicy}; no part of either public limit is reserved
         * for engine bookkeeping.
         *
         * @param namespace application-defined namespace
         * @param key idempotency key within the namespace
         * @return this builder
         */
        public Builder idempotency(String namespace, String key) {
            this.idempotencyNamespace = namespace;
            this.idempotencyKey = key;
            return this;
        }
        /**
         * Adds a canonical identity whose UTxO spending must be serialized.
         * Identities are acquired in sorted order to avoid lock-order cycles.
         *
         * @param canonicalIdentity stable server-defined resource identity
         * @return this builder
         */
        public Builder spendingResource(String canonicalIdentity) {
            FlowStoreTextPolicy.requireIdentifier(
                    canonicalIdentity, "spending resource",
                    FlowStoreTextPolicy.MAX_RESOURCE_ID_BYTES);
            this.spendingResources.add(canonicalIdentity);
            return this;
        }
        /**
         * Requests an opt-out from spending-resource serialization.
         * The engine's policy must explicitly allow this option.
         *
         * @param value whether concurrent spending is requested
         * @return this builder
         */
        public Builder allowConcurrentSpending(boolean value) {
            this.allowConcurrentSpending = value;
            return this;
        }
        /**
         * Associates a sensitive parameter with an external secret reference.
         * The secret value itself is never persisted in the request snapshot.
         *
         * @param parameterName sensitive flow parameter
         * @param reference external secret-manager reference
         * @return this builder
         */
        public Builder secureBindingReference(String parameterName, String reference) {
            if (parameterName == null || parameterName.isBlank()
                    || reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("parameter name and secure reference cannot be blank");
            }
            if (secureBindingReferences.putIfAbsent(parameterName, reference) != null) {
                throw new IllegalArgumentException("duplicate secure binding reference: " + parameterName);
            }
            return this;
        }
        /**
         * Validates and creates the request.
         *
         * @return validated immutable request
         */
        public FlowExecutionRequest build() { return new FlowExecutionRequest(this); }
    }
}
