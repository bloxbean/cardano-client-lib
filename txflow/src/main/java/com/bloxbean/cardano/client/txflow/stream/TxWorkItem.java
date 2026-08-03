package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Normalized transaction work accepted by {@link TxFlowStream}.
 * <p>
 * A work item carries a portable payload — a QuickTx {@link TxPlan} or a
 * portable single-step {@link FlowStep} — plus the idempotency key that
 * defines the item's engine claim. Payload portability is validated eagerly at
 * {@link TxFlowStream#submit(TxWorkItem)}: payloads carrying Java transaction
 * factories cannot be compiled by the engine and are rejected there with a
 * typed diagnostic. Items are immutable, but payloads are held <em>by
 * reference</em>: the stream fingerprints the payload's content at submit
 * time, so mutating a {@link TxPlan} (or a step's plan) after submitting it
 * diverges the executed content from the recorded fingerprint — redelivery
 * comparison, duplicate detection, and the engine claim then disagree about
 * what this item is, and behavior is undefined. Treat a submitted payload as
 * frozen.
 */
public final class TxWorkItem {
    /** Kind of transaction work carried by this item. */
    public enum Kind {
        /** Work supplied as a portable {@link FlowStep}. */
        FLOW_STEP,
        /** Work supplied as a QuickTx {@link TxPlan}. */
        TX_PLAN,
        /**
         * Work supplied as a reference to a pre-registered, parameterized
         * portable {@link com.bloxbean.cardano.client.txflow.TxFlow} template
         * plus this item's parameter bindings — one parameterized invocation of
         * a flow the stream compiled, validated, and fingerprinted once
         * (ADR 0004, iteration 3).
         */
        TEMPLATE
    }

    private final String itemId;
    private final Kind kind;
    private final FlowStep flowStep;
    private final TxPlan txPlan;
    private final String templateId;
    private final String idempotencyKey;
    private final String lane;
    private final Map<String, String> metadata;
    private final Map<String, Object> bindings;
    private final Map<String, String> secureBindingReferences;
    private final Map<String, Object> sensitiveBindings;

    private TxWorkItem(Builder builder) {
        this.itemId = requireId(builder.itemId);
        this.kind = builder.kind;
        this.flowStep = builder.flowStep;
        this.txPlan = builder.txPlan;
        this.templateId = builder.templateId;
        this.idempotencyKey = builder.idempotencyKey;
        this.lane = builder.lane;
        this.metadata = Collections.unmodifiableMap(new TreeMap<>(builder.metadata));
        this.bindings = Collections.unmodifiableMap(new TreeMap<>(builder.bindings));
        this.secureBindingReferences =
                Collections.unmodifiableMap(new TreeMap<>(builder.secureBindingReferences));
        this.sensitiveBindings =
                Collections.unmodifiableMap(new TreeMap<>(builder.sensitiveBindings));
    }

    /**
     * Creates a work item from a portable flow step.
     *
     * @param itemId caller-visible item id used for receipts and status lookup
     * @param step portable flow step to execute
     * @return work item
     */
    public static TxWorkItem fromFlowStep(String itemId, FlowStep step) {
        return builder(itemId).withFlowStep(step).build();
    }

    /**
     * Creates a work item from a QuickTx plan.
     *
     * @param itemId caller-visible item id used for receipts and status lookup
     * @param plan transaction plan to execute
     * @return work item
     */
    public static TxWorkItem fromTxPlan(String itemId, TxPlan plan) {
        return builder(itemId).withTxPlan(plan).build();
    }

    /**
     * Creates a work item builder.
     *
     * @param itemId caller-visible item id used for receipts and status lookup
     * @return builder
     */
    public static Builder builder(String itemId) {
        return new Builder(itemId);
    }

    /**
     * Returns the caller-visible item identity.
     *
     * @return item id
     */
    public String getItemId() { return itemId; }

    /**
     * Returns the payload kind.
     *
     * @return payload kind
     */
    public Kind getKind() { return kind; }

    /**
     * Returns the flow-step payload.
     *
     * @return flow step, or {@code null} for {@link Kind#TX_PLAN} items
     */
    public FlowStep getFlowStep() { return flowStep; }

    /**
     * Returns the transaction-plan payload.
     *
     * @return transaction plan, or {@code null} for {@link Kind#FLOW_STEP} items
     */
    public TxPlan getTxPlan() { return txPlan; }

    /**
     * Returns the pre-registered template id this item invokes.
     *
     * @return template id, or {@code null} unless this is a {@link Kind#TEMPLATE}
     *         item
     */
    public String getTemplateId() { return templateId; }

    /**
     * Returns the idempotency key defining this item's engine claim.
     *
     * @return idempotency key, or {@code null} to default to the item id
     */
    public String getIdempotencyKey() { return idempotencyKey; }

    /**
     * Returns the lane name this item was submitted on.
     *
     * @return lane name, or {@code null} when the item relies on the stream's
     *         statically configured lane
     */
    public String getLane() { return lane; }

    /**
     * Returns the immutable, key-ordered item metadata.
     *
     * @return metadata map
     */
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Returns the non-sensitive portable scalar bindings supplied for this
     * item's flow parameters. These are persisted verbatim in a durable
     * stream's planned record (they are not secrets).
     *
     * @return immutable, key-ordered non-sensitive bindings
     */
    public Map<String, Object> getBindings() { return bindings; }

    /**
     * Returns the secure-binding references supplied for this item's sensitive
     * flow parameters. A durable stream persists only the reference (an opaque
     * pointer) and its fingerprint — never the resolved secret value.
     *
     * @return immutable, key-ordered secure-binding references
     */
    public Map<String, String> getSecureBindingReferences() { return secureBindingReferences; }

    /**
     * Returns the inline sensitive bindings supplied as literal values rather
     * than secure references. A durable stream cannot persist these without
     * becoming a plaintext secret store, so an item carrying any inline
     * sensitive binding is <b>not persistable</b> and fails the item typed
     * {@code TXSTREAM_NON_PERSISTABLE_SECRET} at bind time in durable mode; use
     * {@link Builder#withSecureBindingReference(String, String)} instead. In
     * non-durable mode the value is passed to the engine and never persisted.
     *
     * @return immutable, key-ordered inline sensitive bindings
     */
    public Map<String, Object> getSensitiveBindings() { return sensitiveBindings; }

    private static String requireId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("itemId cannot be null, empty, or whitespace");
        }
        return id.trim();
    }

    /** Builder for {@link TxWorkItem}. */
    public static final class Builder {
        private final String itemId;
        private Kind kind;
        private FlowStep flowStep;
        private TxPlan txPlan;
        private String templateId;
        private String idempotencyKey;
        private String lane;
        private final Map<String, String> metadata = new TreeMap<>();
        private final Map<String, Object> bindings = new TreeMap<>();
        private final Map<String, String> secureBindingReferences = new TreeMap<>();
        private final Map<String, Object> sensitiveBindings = new TreeMap<>();

        private Builder(String itemId) {
            this.itemId = itemId;
        }

        /**
         * Uses a portable flow step as the item payload. The three payload
         * kinds — {@link #withFlowStep(FlowStep)}, {@link #withTxPlan(TxPlan)},
         * and {@link #withTemplate(String)} — are mutually exclusive; setting a
         * second, different kind throws.
         *
         * @param step portable single-transaction flow step
         * @return this builder
         * @throws IllegalStateException when a different payload kind is set
         */
        public Builder withFlowStep(FlowStep step) {
            requireNoConflictingPayload(Kind.FLOW_STEP);
            this.kind = Kind.FLOW_STEP;
            this.flowStep = Objects.requireNonNull(step, "step cannot be null");
            this.txPlan = null;
            this.templateId = null;
            return this;
        }

        /**
         * Uses a QuickTx plan as the item payload. Mutually exclusive with the
         * other payload kinds (see {@link #withFlowStep(FlowStep)}).
         *
         * @param plan single-transaction plan
         * @return this builder
         * @throws IllegalStateException when a different payload kind is set
         */
        public Builder withTxPlan(TxPlan plan) {
            requireNoConflictingPayload(Kind.TX_PLAN);
            this.kind = Kind.TX_PLAN;
            this.txPlan = Objects.requireNonNull(plan, "plan cannot be null");
            this.flowStep = null;
            this.templateId = null;
            return this;
        }

        /**
         * Uses a reference to a pre-registered, parameterized portable template
         * as the item payload. The template must have been registered on the
         * stream builder via
         * {@link TxFlowStream.Builder#template(String, com.bloxbean.cardano.client.txflow.TxFlow)}
         * under this id; the item's {@link #withBinding(String, Object)},
         * {@link #withSecureBindingReference(String, String)}, and
         * {@link #withSensitiveBinding(String, Object)} calls supply the
         * parameter values for this invocation. The template is compiled,
         * validated, and fingerprinted once at build time and reused for every
         * invocation — a stream becomes a stream of parameterized invocations of
         * one flow (ADR 0004, iteration 3). An item referencing an unregistered
         * template id fails typed {@code TXSTREAM_TEMPLATE_UNKNOWN} at submit.
         * Mutually exclusive with the other payload kinds (see
         * {@link #withFlowStep(FlowStep)}).
         *
         * @param templateId id of a template registered on the stream builder
         * @return this builder
         * @throws IllegalStateException when a different payload kind is set
         */
        public Builder withTemplate(String templateId) {
            requireNoConflictingPayload(Kind.TEMPLATE);
            if (templateId == null || templateId.trim().isEmpty()) {
                throw new IllegalArgumentException("templateId cannot be null, empty, or whitespace");
            }
            this.kind = Kind.TEMPLATE;
            this.templateId = templateId.trim();
            this.txPlan = null;
            this.flowStep = null;
            return this;
        }

        private void requireNoConflictingPayload(Kind incoming) {
            if (kind != null && kind != incoming) {
                throw new IllegalStateException(
                        "A work item carries exactly one payload kind; " + kind
                                + " is already set and cannot be combined with " + incoming);
            }
        }

        /**
         * Sets the idempotency key for the item's engine claim. Redeliveries
         * carrying the same key attach to the same execution.
         *
         * @param idempotencyKey application-provided idempotency key;
         *        defaults to the item id when unset
         * @return this builder
         */
        public Builder withIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * Names the lane this item runs on. Required under
         * {@link LanePolicy#explicit()}, where the stream's
         * {@link LaneIdentityResolver} resolves the name to a canonical
         * scheduling identity at first use; optional under
         * {@link LanePolicy#single(ResolvedLane)}, where a supplied name must
         * match the single lane's name (a different name fails the item
         * typed). The name is trimmed; the trimmed value drives resolution
         * and participates in the item's redelivery fingerprint: a redelivery
         * on a different lane is different content.
         *
         * @param laneName lane name, trimmed; {@code null} clears a
         *        previously set lane
         * @return this builder
         * @throws IllegalArgumentException when the name is blank
         */
        public Builder withLane(String laneName) {
            if (laneName != null && laneName.trim().isEmpty()) {
                throw new IllegalArgumentException("laneName cannot be blank");
            }
            this.lane = laneName != null ? laneName.trim() : null;
            return this;
        }

        /**
         * Adds one non-sensitive portable scalar binding for a flow parameter.
         * Non-sensitive bindings are persisted verbatim by a durable stream
         * and participate in the item's redelivery fingerprint.
         *
         * @param name parameter name
         * @param value portable scalar value (string, boolean, or integral
         *        number up to {@code long})
         * @return this builder
         */
        public Builder withBinding(String name, Object value) {
            if (name != null && value != null) {
                this.bindings.put(name, value);
            }
            return this;
        }

        /**
         * Binds a sensitive flow parameter to an external secure reference —
         * the durable-safe way to supply a secret. The stream persists only the
         * reference and its fingerprint and resolves the value afresh through
         * the engine's secure-binding mechanism at dispatch; the secret value is
         * never persisted. Participates in the redelivery fingerprint.
         *
         * @param name sensitive parameter name
         * @param reference opaque application-owned secret reference
         * @return this builder
         */
        public Builder withSecureBindingReference(String name, String reference) {
            if (name != null && reference != null) {
                this.secureBindingReferences.put(name, reference);
            }
            return this;
        }

        /**
         * Binds a sensitive flow parameter to an inline literal value. This is
         * the <b>unsafe</b> form: a durable stream cannot persist it without
         * becoming a plaintext secret store, so an item carrying any inline
         * sensitive binding fails the item typed
         * {@code TXSTREAM_NON_PERSISTABLE_SECRET} at bind time in durable mode.
         * Use {@link #withSecureBindingReference(String, String)} for durable
         * streams. Participates in the redelivery fingerprint but is never
         * persisted.
         *
         * @param name sensitive parameter name
         * @param value inline sensitive scalar value
         * @return this builder
         */
        public Builder withSensitiveBinding(String name, Object value) {
            if (name != null && value != null) {
                this.sensitiveBindings.put(name, value);
            }
            return this;
        }

        /**
         * Adds one metadata value. Metadata participates in the item's
         * redelivery fingerprint.
         *
         * @param key metadata key
         * @param value metadata value
         * @return this builder
         */
        public Builder addMetadata(String key, String value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        /**
         * Replaces the metadata values.
         *
         * @param metadata metadata map; {@code null} clears existing metadata
         * @return this builder
         */
        public Builder withMetadata(Map<String, String> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        /**
         * Builds the immutable work item.
         *
         * @return work item
         * @throws IllegalStateException when no {@link TxPlan}, {@link FlowStep},
         *         or {@link #withTemplate(String) template} payload has been set
         */
        public TxWorkItem build() {
            if (kind == null) {
                throw new IllegalStateException(
                        "Work item requires a TxPlan, FlowStep, or template payload");
            }
            return new TxWorkItem(this);
        }
    }
}
