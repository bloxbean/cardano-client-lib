package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Run-scoped owner of event sequencing and durable journal progress.
 *
 * <p>All events for one execution pass through this session. Durable appends
 * advance the optimistic revision and the in-memory event cursor together,
 * after the fenced store mutation succeeds. If an append fails, the persisted
 * cursor is not advanced, so the uncommitted tail remains available to the
 * failure/recovery path.</p>
 *
 * <p>Mutation methods are synchronized and the shared event list is safe for a
 * {@link FlowExecutionHandle} to snapshot concurrently. The session performs no
 * scheduling and owns no thread or executor.</p>
 */
final class ExecutionJournalSession {
    private final FlowExecutionStore store;
    private final String executionId;
    private final Clock clock;
    private final List<FlowEvent> events = Collections.synchronizedList(new ArrayList<>());
    private DurableLeaseGuard leases;
    private long nextSequence;
    private long revision;
    private long persistedSequence;
    private int persistedEventCount;

    ExecutionJournalSession(FlowExecutionStore store, String executionId, Clock clock) {
        this.store = store;
        this.executionId = requireExecutionId(executionId);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Attaches the lease guard required for all subsequent durable appends. */
    synchronized void attach(DurableLeaseGuard leases) {
        if (store == null) {
            throw new IllegalStateException("Cannot attach durable leases without a store");
        }
        if (this.leases != null && this.leases != leases) {
            throw new IllegalStateException("Durable leases are already attached");
        }
        this.leases = Objects.requireNonNull(leases, "leases");
    }

    /** Returns the live synchronized event list shared with the execution handle. */
    List<FlowEvent> events() {
        return events;
    }

    Instant now() {
        return clock.instant();
    }

    /** Records an in-memory event with the next execution-local sequence. */
    synchronized void record(FlowEventType type, String stepId, String transactionHash,
                             Map<String, Object> details) {
        FlowEvent event = new FlowEvent(++nextSequence, executionId,
                Objects.requireNonNull(type, "type"), clock.instant(), stepId,
                transactionHash, details);
        synchronized (events) {
            events.add(event);
        }
    }

    /** Records an event and persists the complete uncommitted event tail atomically. */
    synchronized FlowExecutionSnapshot recordAndPersist(
            FlowEventType type, String stepId, String transactionHash,
            Map<String, Object> details, FlowExecutionState state,
            Consumer<Map<String, Object>> dataTransition) {
        record(type, stepId, transactionHash, details);
        return persist(state, dataTransition);
    }

    /** Appends the uncommitted event tail and applies a fenced snapshot transition. */
    synchronized FlowExecutionSnapshot persist(
            FlowExecutionState state, Consumer<Map<String, Object>> dataTransition) {
        if (store == null) {
            throw new IllegalStateException("This execution does not have a durable store");
        }
        if (leases == null) {
            throw new IllegalStateException("Durable leases have not been attached");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(dataTransition, "dataTransition");

        PendingEvents pending = pendingEvents();
        FlowExecutionSnapshot updated = store.append(executionId, revision, leases.fence(),
                pending.events(), current -> {
                    Map<String, Object> data = new LinkedHashMap<>(current.data());
                    dataTransition.accept(data);
                    return current.withState(state, clock.instant(), data);
                });
        revision = updated.revision();
        persistedSequence = updated.lastSequence();
        persistedEventCount = pending.endIndex();
        return updated;
    }

    synchronized long revision() {
        return revision;
    }

    synchronized long persistedSequence() {
        return persistedSequence;
    }

    synchronized int persistedEventCount() {
        return persistedEventCount;
    }

    private PendingEvents pendingEvents() {
        synchronized (events) {
            int endIndex = events.size();
            return new PendingEvents(
                    List.copyOf(events.subList(persistedEventCount, endIndex)), endIndex);
        }
    }

    private String requireExecutionId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("executionId cannot be blank");
        }
        return value;
    }

    private record PendingEvents(List<FlowEvent> events, int endIndex) {
    }
}
