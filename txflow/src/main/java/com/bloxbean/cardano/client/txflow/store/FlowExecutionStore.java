package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Durable boundary for execution snapshots, event journals, idempotency claims, and leases.
 *
 * <p>An adapter is responsible for making each method atomic at its persistence boundary.
 * In particular, {@link #append(String, long, MutationFence, List, UnaryOperator)} must compare
 * the snapshot revision, validate every lease epoch, append the events, and replace the snapshot
 * as one operation. A read followed by an unconditional write does not provide the required
 * optimistic concurrency or fencing guarantees.</p>
 *
 * <p>Execution leases serialize mutation of one execution. Resource leases additionally
 * serialize executions that may spend the same logical resource. Fencing protects durable state
 * from a stale worker; it cannot prevent that worker from submitting already signed bytes to the
 * Cardano network.</p>
 *
 * @see InMemoryFlowExecutionStore
 * @see FlowExecutionSnapshot
 */
public interface FlowExecutionStore {
    /**
     * Atomically claims an idempotency key or returns the execution that already owns it.
     *
     * <p>The claim identity is the {@code (namespace, key)} pair. When it already exists, both
     * fingerprints in {@code initialSnapshot} must match the stored fingerprints; otherwise the
     * store rejects the request as an idempotency conflict.</p>
     *
     * @param namespace application-defined tenant or principal scope
     * @param key idempotency key within the namespace
     * @param initialSnapshot snapshot to insert when the claim is new
     * @return the newly inserted snapshot, or the matching existing snapshot
     * @throws FlowStoreException when the claim exists with different fingerprints or the
     *         execution identifier cannot be created
     */
    IdempotencyClaimResult createOrGet(String namespace, String key,
                                       FlowExecutionSnapshot initialSnapshot);

    /**
     * Loads the latest snapshot for an execution.
     *
     * @param executionId durable execution identity
     * @return the snapshot, or an empty value when the execution is unknown
     */
    Optional<FlowExecutionSnapshot> get(String executionId);

    /**
     * Atomically appends journal events and applies a fenced snapshot transition.
     *
     * <p>{@code expectedRevision} is an optimistic compare-and-set token. Event sequence numbers
     * must continue immediately after the snapshot's journal tail, and every event must name the
     * target execution. The execution lease and every resource lease must belong to that same
     * execution and owner. The store owns the next revision and journal metadata; the mutation
     * supplies the new execution state, timestamp, and snapshot data.</p>
     *
     * @param executionId execution to mutate
     * @param expectedRevision revision observed by the caller
     * @param fence current execution lease and all resource leases required for this mutation
     * @param events ordered, contiguous events to append; may be empty
     * @param mutation transition to apply to the current snapshot
     * @return the committed snapshot with its new revision and journal tail
     * @throws FlowStoreException when the revision, event sequence, or any fence is stale or
     *         invalid
     */
    FlowExecutionSnapshot append(String executionId, long expectedRevision,
                                 MutationFence fence, List<FlowEvent> events,
                                 UnaryOperator<FlowExecutionSnapshot> mutation);

    /**
     * Reads events strictly after a durable sequence cursor.
     *
     * <p>If {@code afterSequence} predates the snapshot's compaction watermark, the store reports
     * {@code EVENTS_COMPACTED}; the consumer must reload the snapshot and establish a new
     * baseline instead of treating the missing events as an empty page.</p>
     *
     * @param executionId execution whose journal is read
     * @param afterSequence exclusive, non-negative sequence cursor
     * @param limit maximum number of events to return; must be positive
     * @return an immutable event page and the cursor to use for the next page
     * @throws FlowStoreException when the execution is unknown or the requested history was
     *         compacted
     */
    EventReadResult readEvents(String executionId, long afterSequence, int limit);

    /**
     * Acquires the mutation lease for an execution and mints a new fencing epoch.
     *
     * @param executionId execution to own
     * @param ownerToken opaque identity of the worker acquiring the lease
     * @param now time against which an existing lease is evaluated
     * @param duration positive lease duration
     * @return the acquired lease
     * @throws FlowStoreException when another owner holds an unexpired lease
     */
    ExecutionLease acquireExecutionLease(String executionId, String ownerToken,
                                         Instant now, Duration duration);

    /**
     * Extends the current execution lease without changing its fencing epoch.
     *
     * @param lease current lease returned by this store
     * @param now start of the renewed interval
     * @param duration positive lease duration
     * @return the renewed lease value
     * @throws FlowStoreException when the supplied lease is no longer current or has expired
     */
    ExecutionLease renewExecutionLease(ExecutionLease lease, Instant now, Duration duration);

    /**
     * Releases the current execution lease. A stale lease cannot release its successor.
     *
     * @param lease current lease returned by this store
     * @throws FlowStoreException when the supplied lease is no longer current
     */
    void releaseExecutionLease(ExecutionLease lease);

    /**
     * Acquires exclusive ownership of a canonical spending-resource identity.
     *
     * <p>The lease associates the resource with an execution as well as a worker. Callers that
     * need several resources should acquire them in a deterministic order to avoid deadlock in
     * database-backed adapters.</p>
     *
     * @param resourceId canonical resource identity
     * @param executionId execution claiming the resource
     * @param ownerToken opaque identity of the worker acquiring the lease
     * @param now time against which an existing lease is evaluated
     * @param duration positive lease duration
     * @return the acquired resource lease with a new fencing epoch
     * @throws FlowStoreException when an unexpired lease belongs to another execution
     */
    ResourceLease acquireResourceLease(String resourceId, String executionId, String ownerToken,
                                       Instant now, Duration duration);

    /**
     * Extends the current resource lease without changing its fencing epoch.
     *
     * @param lease current resource lease returned by this store
     * @param now start of the renewed interval
     * @param duration positive lease duration
     * @return the renewed lease value
     * @throws FlowStoreException when the supplied lease is no longer current or has expired
     */
    ResourceLease renewResourceLease(ResourceLease lease, Instant now, Duration duration);

    /**
     * Releases the current resource lease. A stale lease cannot release its successor.
     *
     * @param lease current resource lease returned by this store
     * @throws FlowStoreException when the supplied lease is no longer current
     */
    void releaseResourceLease(ResourceLease lease);

    /**
     * Removes terminal-execution events through an inclusive sequence and advances the watermark.
     *
     * <p>Compaction never resets the journal sequence: later events, if supported, continue after
     * {@link FlowExecutionSnapshot#lastSequence()}. Consumers with older cursors must re-baseline
     * from the retained snapshot.</p>
     *
     * <p>A request at or below the current watermark is an idempotent no-op; compaction never
     * moves the watermark backward.</p>
     *
     * @param executionId terminal execution to compact
     * @param throughSequence inclusive sequence through which events may be removed
     * @throws FlowStoreException when the execution is not terminal
     * @throws IllegalArgumentException when the sequence is negative or beyond the journal tail
     */
    void compactEvents(String executionId, long throughSequence);
}
