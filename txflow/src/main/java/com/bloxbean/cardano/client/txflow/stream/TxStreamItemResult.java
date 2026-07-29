package com.bloxbean.cardano.client.txflow.stream;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable point-in-time result for a stream work item.
 * <p>
 * An item produces a sequence of result snapshots as its projection advances.
 * A transaction hash, once present on any snapshot, is never dropped from a
 * later one.
 */
public final class TxStreamItemResult {
    private final String streamId;
    private final String itemId;
    private final TxStreamItemStatus status;
    private final String executionId;
    private final String stepId;
    private final String laneName;
    private final String transactionHash;
    private final Throwable error;
    private final Instant updatedAt;

    private TxStreamItemResult(Builder builder) {
        this.streamId = Objects.requireNonNull(builder.streamId, "streamId");
        this.itemId = Objects.requireNonNull(builder.itemId, "itemId");
        this.status = Objects.requireNonNull(builder.status, "status");
        this.executionId = builder.executionId;
        this.stepId = builder.stepId;
        this.laneName = builder.laneName;
        this.transactionHash = builder.transactionHash;
        this.error = builder.error;
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt");
    }

    /**
     * Returns the stream that accepted the item.
     *
     * @return stream id
     */
    public String getStreamId() { return streamId; }

    /**
     * Returns the caller-visible item identity.
     *
     * @return item id
     */
    public String getItemId() { return itemId; }

    /**
     * Returns the item status at this snapshot.
     *
     * @return item status
     */
    public TxStreamItemStatus getStatus() { return status; }

    /**
     * Returns the deterministic engine execution identity bound to this item.
     *
     * @return execution id, or {@code null} before binding
     */
    public String getExecutionId() { return executionId; }

    /**
     * Returns the generated flow step identity carrying the item's transaction.
     *
     * @return step id, or {@code null} before binding
     */
    public String getStepId() { return stepId; }

    /**
     * Returns the user-facing label of the lane the item runs on.
     *
     * @return lane name, or {@code null} before binding
     */
    public String getLaneName() { return laneName; }

    /**
     * Returns the submitted transaction hash.
     *
     * @return transaction hash, or {@code null} when no transaction was submitted
     */
    public String getTransactionHash() { return transactionHash; }

    /**
     * Returns the failure associated with this snapshot.
     *
     * @return failure cause, or {@code null}
     */
    public Throwable getError() { return error; }

    /**
     * Returns the time this snapshot was projected.
     *
     * @return snapshot timestamp
     */
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Reports whether this snapshot is final and immutable.
     * <p>
     * {@link TxStreamItemStatus#RECOVERY_REQUIRED} is deliberately not final:
     * it settles the receipt's completion but remains repairable through
     * read-through reconciliation.
     *
     * @return {@code true} for confirmed, failed, or cancelled items
     */
    public boolean isTerminal() {
        return status == TxStreamItemStatus.CONFIRMED
                || status == TxStreamItemStatus.FAILED
                || status == TxStreamItemStatus.CANCELLED;
    }

    /**
     * Reports whether the item completed successfully.
     *
     * @return {@code true} only for {@link TxStreamItemStatus#CONFIRMED}
     */
    public boolean isSuccessful() {
        return status == TxStreamItemStatus.CONFIRMED;
    }

    /**
     * Creates a result builder.
     *
     * @param streamId stream id
     * @param itemId item id
     * @param status item status
     * @return result builder
     */
    public static Builder builder(String streamId, String itemId, TxStreamItemStatus status) {
        return new Builder().streamId(streamId).itemId(itemId).status(status);
    }

    /**
     * Creates a builder pre-populated with this snapshot's values.
     *
     * @return builder carrying every field of this result
     */
    public Builder toBuilder() {
        return new Builder()
                .streamId(streamId)
                .itemId(itemId)
                .status(status)
                .executionId(executionId)
                .stepId(stepId)
                .laneName(laneName)
                .transactionHash(transactionHash)
                .error(error)
                .updatedAt(updatedAt);
    }

    @Override
    public String toString() {
        return "TxStreamItemResult{itemId='" + itemId + "', status=" + status
                + ", executionId='" + executionId + "', transactionHash='" + transactionHash
                + "', error=" + (error != null ? error.getMessage() : "null")
                + ", updatedAt=" + updatedAt + '}';
    }

    /** Builder for immutable {@link TxStreamItemResult} snapshots. */
    public static final class Builder {
        private String streamId;
        private String itemId;
        private TxStreamItemStatus status;
        private String executionId;
        private String stepId;
        private String laneName;
        private String transactionHash;
        private Throwable error;
        private Instant updatedAt;

        private Builder() {
        }

        /**
         * Sets the stream id.
         *
         * @param value stream id
         * @return this builder
         */
        public Builder streamId(String value) { this.streamId = value; return this; }

        /**
         * Sets the item id.
         *
         * @param value item id
         * @return this builder
         */
        public Builder itemId(String value) { this.itemId = value; return this; }

        /**
         * Sets the item status.
         *
         * @param value item status
         * @return this builder
         */
        public Builder status(TxStreamItemStatus value) { this.status = value; return this; }

        /**
         * Sets the deterministic execution id.
         *
         * @param value execution id
         * @return this builder
         */
        public Builder executionId(String value) { this.executionId = value; return this; }

        /**
         * Sets the generated step id.
         *
         * @param value step id
         * @return this builder
         */
        public Builder stepId(String value) { this.stepId = value; return this; }

        /**
         * Sets the lane label.
         *
         * @param value lane name
         * @return this builder
         */
        public Builder laneName(String value) { this.laneName = value; return this; }

        /**
         * Sets the submitted transaction hash.
         *
         * @param value transaction hash
         * @return this builder
         */
        public Builder transactionHash(String value) { this.transactionHash = value; return this; }

        /**
         * Sets the failure cause.
         *
         * @param value failure cause, or {@code null} to clear it
         * @return this builder
         */
        public Builder error(Throwable value) { this.error = value; return this; }

        /**
         * Sets the snapshot timestamp.
         *
         * @param value projection time
         * @return this builder
         */
        public Builder updatedAt(Instant value) { this.updatedAt = value; return this; }

        /**
         * Builds the immutable result snapshot.
         *
         * @return item result
         */
        public TxStreamItemResult build() {
            return new TxStreamItemResult(this);
        }
    }
}
