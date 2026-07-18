package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.exec.FlowListener;
import com.bloxbean.cardano.client.txflow.exec.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.exec.store.FlowStateStore;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;

import java.time.Duration;
import java.util.Optional;

/**
 * Stream-oriented transaction workflow API for continuously accepting transaction work.
 * <p>
 * A stream buffers submitted {@link TxWorkItem} instances, windows them with a
 * {@link WindowPolicy}, asks a {@link TxStreamPlanner} to create bounded
 * {@link com.bloxbean.cardano.client.txflow.TxFlow} executions, and exposes
 * item/batch status through receipts and query methods.
 * <p>
 * The MVP implementation is intentionally conservative: it executes generated
 * bounded flows serially and does not imply transaction-count reduction unless a
 * custom planner explicitly aggregates compatible work.
 */
public interface TxFlowStream extends AutoCloseable {
    /**
     * Start accepting source-driven work and begin processing queued submissions.
     * <p>
     * Calling this method more than once is a no-op after the first successful start.
     */
    void start();

    /**
     * Pause direct submissions and pause the configured source if it supports pausing.
     */
    void pause();

    /**
     * Resume direct submissions and resume the configured source if it supports resuming.
     */
    void resume();

    /**
     * Request immediate planning of the current partial window.
     */
    void flush();

    /**
     * Stop accepting new direct-submit work, flush accepted work, and wait for all
     * accepted items to reach a terminal status.
     */
    void drain();

    /**
     * Wait until the stream has no buffered work, no current window, and no active batch.
     */
    void awaitDrain();

    /**
     * Wait until the stream is drained or the timeout elapses.
     *
     * @param timeout maximum time to wait
     */
    void awaitDrain(Duration timeout);

    /**
     * Stop accepting work, drain accepted work, stop the worker, and release stream resources.
     */
    void shutdown();

    /**
     * Submit one work item, blocking until buffer capacity is available.
     *
     * @param item work to accept
     * @return receipt for tracking this item
     */
    TxStreamReceipt submit(TxWorkItem item);

    /**
     * Submit one work item without blocking for buffer capacity.
     *
     * @param item work to accept
     * @return non-blocking emit result, including a receipt when accepted
     */
    EmitResult trySubmit(TxWorkItem item);

    /**
     * Get the latest known item status from the stream state store.
     *
     * @param itemId caller-provided work item id
     * @return latest item result if present
     */
    Optional<TxStreamItemResult> getItemStatus(String itemId);

    /**
     * Get the latest known batch/window status from the stream state store.
     *
     * @param batchId generated batch id
     * @return latest batch result if present
     */
    Optional<TxStreamBatchResult> getBatchStatus(String batchId);

    /**
     * Return current point-in-time stream counters.
     *
     * @return stream stats snapshot
     */
    TxStreamStats getStats();

    /**
     * Close the stream. This is equivalent to orderly shutdown and is idempotent.
     */
    @Override
    void close();

    /**
     * Create a stream builder backed by the provided backend service.
     *
     * @param streamId stable stream id used in generated batch/flow ids and status records
     * @param backendService backend service used by the generated {@code FlowExecutor}
     * @return stream builder
     */
    static Builder builder(String streamId, BackendService backendService) {
        return new Builder(streamId, backendService);
    }

    /**
     * Builder for {@link TxFlowStream}.
     */
    final class Builder {
        private final String streamId;
        private final BackendService backendService;
        private TxWorkSource source = TxWorkSource.inMemory();
        private WindowPolicy windowPolicy = WindowPolicy.defaults();
        private TxStreamPlanner planner = TxStreamPlanner.defaultPlanner();
        private ChainingMode chainingMode = ChainingMode.SEQUENTIAL;
        private ConfirmationConfig confirmationConfig;
        private RollbackStrategy rollbackStrategy = RollbackStrategy.FAIL_IMMEDIATELY;
        private RetryPolicy retryPolicy;
        private SignerRegistry signerRegistry;
        private FlowListener flowListener;
        private FlowStateStore flowStateStore;
        private TxStreamStateStore streamStateStore = TxStreamStateStore.inMemory();
        private TxStreamEventListener eventListener = TxStreamEventListener.NOOP;
        private UtxoReservationPolicy reservationPolicy = UtxoReservationPolicy.serialByFundingScope();
        private int maxBufferSize = 1000;
        private FlowExecutionRunner runner;

        private Builder(String streamId, BackendService backendService) {
            if (streamId == null || streamId.trim().isEmpty()) {
                throw new IllegalArgumentException("streamId cannot be null, empty, or whitespace");
            }
            this.streamId = streamId.trim();
            this.backendService = backendService;
        }

        /**
         * Configure the source that feeds work into this stream.
         *
         * @param source source adapter; {@link TxWorkSource#inMemory()} is used when null
         * @return this builder
         */
        public Builder withSource(TxWorkSource source) {
            this.source = source != null ? source : TxWorkSource.inMemory();
            return this;
        }

        /**
         * Configure count/time windowing.
         *
         * @param windowPolicy window policy; defaults are used when null
         * @return this builder
         */
        public Builder withWindow(WindowPolicy windowPolicy) {
            this.windowPolicy = windowPolicy != null ? windowPolicy : WindowPolicy.defaults();
            return this;
        }

        /**
         * Configure the planner that maps windows to bounded TxFlow executions.
         *
         * @param planner planner to use; the default one-item-per-step planner is used when null
         * @return this builder
         */
        public Builder withPlanner(TxStreamPlanner planner) {
            this.planner = planner != null ? planner : TxStreamPlanner.defaultPlanner();
            return this;
        }

        /**
         * Configure chaining mode for generated bounded flows.
         *
         * @param chainingMode mode passed to {@link FlowExecutor}
         * @return this builder
         */
        public Builder withChainingMode(ChainingMode chainingMode) {
            this.chainingMode = chainingMode != null ? chainingMode : ChainingMode.SEQUENTIAL;
            return this;
        }

        /**
         * Configure confirmation tracking for generated bounded flows.
         *
         * @param confirmationConfig confirmation settings passed to {@link FlowExecutor}
         * @return this builder
         */
        public Builder withConfirmationConfig(ConfirmationConfig confirmationConfig) {
            this.confirmationConfig = confirmationConfig;
            return this;
        }

        /**
         * Configure rollback handling for generated bounded flows.
         *
         * @param rollbackStrategy rollback strategy passed to {@link FlowExecutor}
         * @return this builder
         */
        public Builder withRollbackStrategy(RollbackStrategy rollbackStrategy) {
            this.rollbackStrategy = rollbackStrategy != null ? rollbackStrategy : RollbackStrategy.FAIL_IMMEDIATELY;
            return this;
        }

        /**
         * Configure default retry policy for generated bounded flow steps.
         *
         * @param retryPolicy retry policy passed to {@link FlowExecutor}
         * @return this builder
         */
        public Builder withRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        /**
         * Configure signer registry for TxPlan/YAML-style generated steps.
         *
         * @param signerRegistry signer registry passed to {@link FlowExecutor}
         * @return this builder
         */
        public Builder withSignerRegistry(SignerRegistry signerRegistry) {
            this.signerRegistry = signerRegistry;
            return this;
        }

        /**
         * Configure flow listener for generated bounded flow events.
         *
         * @param flowListener listener passed to {@link FlowExecutor}
         * @return this builder
         */
        public Builder withFlowListener(FlowListener flowListener) {
            this.flowListener = flowListener;
            return this;
        }

        /**
         * Configure bounded-flow state persistence.
         *
         * @param flowStateStore state store passed to {@link FlowExecutor}
         * @return this builder
         */
        public Builder withFlowStateStore(FlowStateStore flowStateStore) {
            this.flowStateStore = flowStateStore;
            return this;
        }

        /**
         * Configure stream-level item and batch state persistence.
         *
         * @param streamStateStore stream state store; in-memory store is used when null
         * @return this builder
         */
        public Builder withStateStore(TxStreamStateStore streamStateStore) {
            this.streamStateStore = streamStateStore != null ? streamStateStore : TxStreamStateStore.inMemory();
            return this;
        }

        /**
         * Configure structured stream lifecycle/item/batch event callbacks.
         *
         * @param eventListener event listener; no-op listener is used when null
         * @return this builder
         */
        public Builder withEventListener(TxStreamEventListener eventListener) {
            this.eventListener = eventListener != null ? eventListener : TxStreamEventListener.NOOP;
            return this;
        }

        /**
         * Configure stream-level UTXO coordination policy.
         * <p>
         * The MVP supports serial execution by funding scope, represented by
         * {@link UtxoReservationPolicy#SERIAL_BY_FUNDING_SCOPE}.
         *
         * @param reservationPolicy reservation policy; serial policy is used when null
         * @return this builder
         */
        public Builder withUtxoReservationPolicy(UtxoReservationPolicy reservationPolicy) {
            this.reservationPolicy = reservationPolicy != null
                    ? reservationPolicy
                    : UtxoReservationPolicy.serialByFundingScope();
            return this;
        }

        /**
         * Configure maximum number of accepted-but-not-yet-windowed items.
         *
         * @param maxBufferSize bounded queue size
         * @return this builder
         */
        public Builder withMaxBufferSize(int maxBufferSize) {
            if (maxBufferSize <= 0) {
                throw new IllegalArgumentException("maxBufferSize must be positive");
            }
            this.maxBufferSize = maxBufferSize;
            return this;
        }

        Builder withRunner(FlowExecutionRunner runner) {
            this.runner = runner;
            return this;
        }

        /**
         * Build the stream instance.
         *
         * @return configured stream
         */
        public TxFlowStream build() {
            FlowExecutionRunner effectiveRunner = runner != null ? runner : createRunner();
            return new DefaultTxFlowStream(
                    streamId,
                    source,
                    windowPolicy,
                    planner,
                    streamStateStore,
                    eventListener,
                    reservationPolicy,
                    maxBufferSize,
                    effectiveRunner);
        }

        private FlowExecutionRunner createRunner() {
            if (backendService == null) {
                throw new IllegalStateException("backendService is required when no custom runner is configured");
            }

            FlowExecutor executor = FlowExecutor.create(backendService)
                    .withChainingMode(chainingMode)
                    .withRollbackStrategy(rollbackStrategy);

            if (confirmationConfig != null) {
                executor.withConfirmationConfig(confirmationConfig);
            }
            if (retryPolicy != null) {
                executor.withDefaultRetryPolicy(retryPolicy);
            }
            if (signerRegistry != null) {
                executor.withSignerRegistry(signerRegistry);
            }
            if (flowListener != null) {
                executor.withListener(flowListener);
            }
            if (flowStateStore != null) {
                executor.withStateStore(flowStateStore);
            }

            return new FlowExecutorRunner(executor);
        }
    }
}
