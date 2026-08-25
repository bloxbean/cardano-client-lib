package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.exec.ExecutionEventView;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionHandle;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Production {@link EngineGateway} delegating to a caller-owned
 * {@link FlowEngine}.
 */
final class FlowEngineGateway implements EngineGateway {
    private final FlowEngine engine;

    FlowEngineGateway(FlowEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public ExecutionHandle start(FlowExecutionRequest request) {
        return new HandleView(engine.start(request));
    }

    @Override
    public boolean durableExecution() {
        return engine.capabilities().durableExecution();
    }

    @Override
    public Optional<Executor> executionExecutor() {
        return Optional.of(engine.executionExecutor());
    }

    @Override
    public Optional<FlowExecutionSnapshot> executionSnapshot(String executionId) {
        return engine.executionSnapshot(executionId);
    }

    @Override
    public Optional<ExecutionEventView> executionEvents(String executionId,
                                                        long afterSequence, int limit) {
        return engine.executionEvents(executionId, afterSequence, limit);
    }

    private static final class HandleView implements ExecutionHandle {
        private final FlowExecutionHandle handle;

        private HandleView(FlowExecutionHandle handle) {
            this.handle = handle;
        }

        @Override
        public String executionId() {
            return handle.getExecutionId();
        }

        @Override
        public boolean isDone() {
            return handle.isDone();
        }

        @Override
        public FlowExecutionResult resultIfDone() {
            return handle.isDone() ? handle.await() : null;
        }

        @Override
        public CompletionStage<FlowExecutionResult> completion() {
            return handle.completion();
        }

        @Override
        public List<FlowEvent> events() {
            return handle.getEvents();
        }

        @Override
        public List<FlowEvent> eventsAfter(long sequence) {
            return handle.getEventsAfter(sequence);
        }

        @Override
        public void requestCancel(String reason) {
            handle.requestCancel(reason);
        }
    }
}
