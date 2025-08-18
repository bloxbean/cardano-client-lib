package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableStep;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder and executor for transaction chains.
 *
 * The Watcher class provides a fluent API for building and executing
 * transaction chains with step dependencies and execution policies.
 *
 * Usage:
 * <pre>
 * WatchHandle handle = Watcher.build("my-chain")
 *     .step(step1)
 *     .step(step2)
 *     .withDescription("My transaction chain")
 *     .watch();
 * </pre>
 */
@Slf4j
public class Watcher {

    /**
     * Create a new WatcherBuilder for the given chain ID.
     *
     * @param chainId the chain identifier
     * @return a new WatcherBuilder
     */
    public static WatcherBuilder build(String chainId) {
        return new WatcherBuilder(chainId);
    }

    /**
     * Builder for constructing transaction chains.
     */
    public static class WatcherBuilder {
        private final String chainId;
        private final List<WatchableStep> steps;
        private String description;

        /**
         * Create a new WatcherBuilder.
         *
         * @param chainId the chain identifier
         */
        WatcherBuilder(String chainId) {
            this.chainId = chainId;
            this.steps = new ArrayList<>();
        }

        /**
         * Add a step to the chain for sequential execution.
         *
         * @param step the step to add
         * @return this builder for method chaining
         */
        public WatcherBuilder step(WatchableStep step) {
            this.steps.add(step);
            return this;
        }

        /**
         * Set a description for the chain.
         *
         * @param description the description
         * @return this builder for method chaining
         */
        public WatcherBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Build and start watching the chain.
         *
         * This method executes the chain steps sequentially, resolving UTXO
         * dependencies between steps.
         *
         * @return a watch handle for monitoring the chain
         */
        public WatchHandle watch() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("Cannot watch empty chain");
            }

            // Create chain context for step communication
            ChainContext chainContext = new ChainContext(chainId);

            // Create a basic watch handle
            BasicWatchHandle watchHandle = new BasicWatchHandle(chainId, steps.size(), description);

            // Initialize all planned steps so they show up in visualization even if chain fails early
            List<String> stepIds = steps.stream()
                .map(WatchableStep::getStepId)
                .collect(java.util.stream.Collectors.toList());
            watchHandle.initializePlannedSteps(stepIds);

            // Execute steps asynchronously in a separate thread
            Thread executionThread = new Thread(() -> {
                try {
                    log.info("Starting chain execution: {}", chainId);

                    for (WatchableStep step : steps) {
                        watchHandle.updateStepStatus(step.getStepId(), step.getStatus());

                        // Execute the step
                        if (log.isDebugEnabled()) {
                            log.debug("Executing step: {}", step.getStepId());
                        }
                        StepResult result = step.execute(chainContext);
                        if (log.isDebugEnabled()) {
                            log.debug("Executed step: {}, Result: {}", step.getStepId(), result);
                        }

                        // Update watch handle with result (this triggers callbacks)
                        if (log.isTraceEnabled()) {
                            log.trace("Recording step result for step: {}", step.getStepId());
                        }
                        watchHandle.recordStepResult(step.getStepId(), result);
                        if (log.isTraceEnabled()) {
                            log.trace("Step result recorded successfully");
                        }

                        if (!result.isSuccessful()) {
                            // Stop execution on first failure for MVP
                            log.error("Step failed, stopping chain execution", result.getError());
                            watchHandle.markFailed(result.getError());
                            return;
                        }
                    }

                    // Mark as completed if all steps succeeded
                    log.info("All steps completed successfully, marking chain as completed");
                    watchHandle.markCompleted();

                } catch (Exception e) {
                    log.error("Chain execution failed with exception", e);
                    watchHandle.markFailed(e);
                }
            }, "Chain-" + chainId);

            // Start execution immediately and return handle
            executionThread.start();

            return watchHandle;
        }

        /**
         * Get the chain ID.
         *
         * @return the chain ID
         */
        public String getChainId() {
            return chainId;
        }

        /**
         * Get the description.
         *
         * @return the description
         */
        public String getDescription() {
            return description;
        }

        /**
         * Get the steps.
         *
         * @return the list of steps
         */
        public List<WatchableStep> getSteps() {
            return List.copyOf(steps);
        }
    }
}
