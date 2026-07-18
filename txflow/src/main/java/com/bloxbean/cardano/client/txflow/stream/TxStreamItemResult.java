package com.bloxbean.cardano.client.txflow.stream;

import lombok.Getter;

import java.time.Instant;

/**
 * Current or terminal result for a stream work item.
 * <p>
 * Results are immutable snapshots. A receipt and the state store may observe
 * several snapshots for the same item as it moves through stream execution.
 */
@Getter
public final class TxStreamItemResult {
    private final String streamId;
    private final String itemId;
    private final TxStreamItemStatus status;
    private final String batchId;
    private final String flowId;
    private final String stepId;
    private final String transactionHash;
    private final Throwable failure;
    private final Instant updatedAt;

    private TxStreamItemResult(Builder builder) {
        this.streamId = builder.streamId;
        this.itemId = builder.itemId;
        this.status = builder.status;
        this.batchId = builder.batchId;
        this.flowId = builder.flowId;
        this.stepId = builder.stepId;
        this.transactionHash = builder.transactionHash;
        this.failure = builder.failure;
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
    }

    /**
     * Check whether this result is terminal.
     *
     * @return true for confirmed, failed, or cancelled items
     */
    public boolean isTerminal() {
        return status == TxStreamItemStatus.CONFIRMED
                || status == TxStreamItemStatus.FAILED
                || status == TxStreamItemStatus.CANCELLED;
    }

    /**
     * Check whether this item completed successfully.
     *
     * @return true when status is {@link TxStreamItemStatus#CONFIRMED}
     */
    public boolean isSuccessful() {
        return status == TxStreamItemStatus.CONFIRMED;
    }

    /**
     * Create an item result builder.
     *
     * @param streamId stream id
     * @param itemId item id
     * @param status item status
     * @return builder
     */
    public static Builder builder(String streamId, String itemId, TxStreamItemStatus status) {
        return new Builder(streamId, itemId, status);
    }

    /**
     * Builder for immutable {@link TxStreamItemResult} snapshots.
     */
    public static final class Builder {
        private final String streamId;
        private final String itemId;
        private final TxStreamItemStatus status;
        private String batchId;
        private String flowId;
        private String stepId;
        private String transactionHash;
        private Throwable failure;
        private Instant updatedAt;

        private Builder(String streamId, String itemId, TxStreamItemStatus status) {
            this.streamId = streamId;
            this.itemId = itemId;
            this.status = status;
        }

        /**
         * Set the batch id that owns this item.
         *
         * @param batchId generated batch id
         * @return this builder
         */
        public Builder batchId(String batchId) {
            this.batchId = batchId;
            return this;
        }

        /**
         * Set the generated bounded flow id.
         *
         * @param flowId generated flow id
         * @return this builder
         */
        public Builder flowId(String flowId) {
            this.flowId = flowId;
            return this;
        }

        /**
         * Set the generated flow step id.
         *
         * @param stepId generated or original step id
         * @return this builder
         */
        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        /**
         * Set the transaction hash produced for this item.
         *
         * @param transactionHash transaction hash
         * @return this builder
         */
        public Builder transactionHash(String transactionHash) {
            this.transactionHash = transactionHash;
            return this;
        }

        /**
         * Set the failure that affected this item.
         *
         * @param failure failure cause
         * @return this builder
         */
        public Builder failure(Throwable failure) {
            this.failure = failure;
            return this;
        }

        /**
         * Set the timestamp for this snapshot.
         *
         * @param updatedAt update timestamp
         * @return this builder
         */
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Build the immutable item result.
         *
         * @return item result
         */
        public TxStreamItemResult build() {
            return new TxStreamItemResult(this);
        }
    }
}
