package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized transaction work accepted by {@link TxFlowStream}.
 * <p>
 * The MVP supports work expressed as either a bounded {@link FlowStep} or a
 * QuickTx {@link TxPlan}. The default planner converts one item into one
 * generated flow step. Future planners may inspect richer item types to merge
 * compatible work into fewer transactions.
 */
@Getter
public final class TxWorkItem {
    /**
     * Kind of transaction work carried by this item.
     */
    public enum Kind {
        /**
         * Work supplied as a pre-built {@link FlowStep}.
         */
        FLOW_STEP,
        /**
         * Work supplied as a QuickTx {@link TxPlan}.
         */
        TX_PLAN
    }

    private final String itemId;
    private final Kind kind;
    private final FlowStep flowStep;
    private final TxPlan txPlan;
    private final String idempotencyKey;
    private final Map<String, String> metadata;

    private TxWorkItem(Builder builder) {
        this.itemId = requireId(builder.itemId, "itemId");
        this.kind = Objects.requireNonNull(builder.kind, "kind cannot be null");
        this.flowStep = builder.flowStep;
        this.txPlan = builder.txPlan;
        this.idempotencyKey = builder.idempotencyKey;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    /**
     * Create a work item from an existing flow step.
     *
     * @param itemId caller-visible item id used for receipts and status lookup
     * @param step flow step to execute
     * @return work item
     */
    public static TxWorkItem fromFlowStep(String itemId, FlowStep step) {
        return builder(itemId)
                .withFlowStep(step)
                .build();
    }

    /**
     * Create a work item from a QuickTx plan.
     *
     * @param itemId caller-visible item id used for receipts and status lookup
     * @param plan transaction plan to execute
     * @return work item
     */
    public static TxWorkItem fromTxPlan(String itemId, TxPlan plan) {
        return builder(itemId)
                .withTxPlan(plan)
                .build();
    }

    /**
     * Create a work item builder.
     *
     * @param itemId caller-visible item id used for receipts and status lookup
     * @return builder
     */
    public static Builder builder(String itemId) {
        return new Builder(itemId);
    }

    FlowStep toFlowStep(String generatedStepId) {
        if (kind == Kind.FLOW_STEP) {
            return flowStep;
        }

        return FlowStep.builder(generatedStepId)
                .withTxPlan(txPlan)
                .build();
    }

    private static String requireId(String id, String field) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be null, empty, or whitespace");
        }
        return id.trim();
    }

    /**
     * Builder for {@link TxWorkItem}.
     */
    public static final class Builder {
        private final String itemId;
        private Kind kind;
        private FlowStep flowStep;
        private TxPlan txPlan;
        private String idempotencyKey;
        private final Map<String, String> metadata = new HashMap<>();

        private Builder(String itemId) {
            this.itemId = itemId;
        }

        /**
         * Use a pre-built flow step as the item payload.
         *
         * @param step flow step to execute
         * @return this builder
         */
        public Builder withFlowStep(FlowStep step) {
            this.kind = Kind.FLOW_STEP;
            this.flowStep = Objects.requireNonNull(step, "step cannot be null");
            this.txPlan = null;
            return this;
        }

        /**
         * Use a QuickTx plan as the item payload.
         *
         * @param plan transaction plan to execute
         * @return this builder
         */
        public Builder withTxPlan(TxPlan plan) {
            this.kind = Kind.TX_PLAN;
            this.txPlan = Objects.requireNonNull(plan, "plan cannot be null");
            this.flowStep = null;
            return this;
        }

        /**
         * Set an optional idempotency key carried through source metadata.
         *
         * @param idempotencyKey application-provided idempotency key
         * @return this builder
         */
        public Builder withIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * Add one metadata value.
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
         * Replace metadata values.
         *
         * @param metadata metadata map; null clears existing metadata
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
         * Build the work item.
         *
         * @return work item
         */
        public TxWorkItem build() {
            if (kind == null) {
                throw new IllegalStateException("Work item must have a FlowStep or TxPlan");
            }
            return new TxWorkItem(this);
        }
    }
}
