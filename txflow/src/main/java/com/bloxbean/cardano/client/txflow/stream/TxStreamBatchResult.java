package com.bloxbean.cardano.client.txflow.stream;

import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Current or terminal status for a stream batch/window.
 * <p>
 * A batch result records which accepted items were grouped together and which
 * generated bounded flows were used to execute them.
 */
@Getter
public final class TxStreamBatchResult {
    private final String streamId;
    private final String batchId;
    private final TxStreamBatchStatus status;
    private final List<String> itemIds;
    private final List<String> flowIds;
    private final Throwable failure;
    private final Instant updatedAt;

    private TxStreamBatchResult(Builder builder) {
        this.streamId = builder.streamId;
        this.batchId = builder.batchId;
        this.status = builder.status;
        this.itemIds = Collections.unmodifiableList(new ArrayList<>(builder.itemIds));
        this.flowIds = Collections.unmodifiableList(new ArrayList<>(builder.flowIds));
        this.failure = builder.failure;
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
    }

    /**
     * Create a batch result builder.
     *
     * @param streamId stream id
     * @param batchId generated batch id
     * @param status batch status
     * @return builder
     */
    public static Builder builder(String streamId, String batchId, TxStreamBatchStatus status) {
        return new Builder(streamId, batchId, status);
    }

    /**
     * Builder for immutable {@link TxStreamBatchResult} snapshots.
     */
    public static final class Builder {
        private final String streamId;
        private final String batchId;
        private final TxStreamBatchStatus status;
        private final List<String> itemIds = new ArrayList<>();
        private final List<String> flowIds = new ArrayList<>();
        private Throwable failure;
        private Instant updatedAt;

        private Builder(String streamId, String batchId, TxStreamBatchStatus status) {
            this.streamId = streamId;
            this.batchId = batchId;
            this.status = status;
        }

        /**
         * Set item ids contained in the batch.
         *
         * @param itemIds item ids
         * @return this builder
         */
        public Builder itemIds(List<String> itemIds) {
            this.itemIds.clear();
            if (itemIds != null) {
                this.itemIds.addAll(itemIds);
            }
            return this;
        }

        /**
         * Set generated bounded flow ids used by the batch.
         *
         * @param flowIds generated flow ids
         * @return this builder
         */
        public Builder flowIds(List<String> flowIds) {
            this.flowIds.clear();
            if (flowIds != null) {
                this.flowIds.addAll(flowIds);
            }
            return this;
        }

        /**
         * Set the failure that affected the batch, if any.
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
         * Build the immutable batch result.
         *
         * @return batch result
         */
        public TxStreamBatchResult build() {
            return new TxStreamBatchResult(this);
        }
    }
}
