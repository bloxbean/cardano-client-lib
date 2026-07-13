package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Process-local reference implementation of {@link FlowExecutionStore}.
 *
 * <p>Synchronized compound operations model the atomic idempotency, optimistic-revision,
 * journal, and epoch-fencing guarantees that a durable adapter must preserve. The implementation
 * also makes returned snapshots and event pages safe to share according to their value-type
 * contracts.</p>
 *
 * <p>All contents disappear with the process. This store is intended for tests, examples, and
 * non-durable single-process use; it is not a production recovery store and cannot coordinate
 * workers in different processes.</p>
 */
public final class InMemoryFlowExecutionStore implements FlowExecutionStore {
    private final Clock clock;
    private final Map<String, FlowExecutionSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new HashMap<>();
    private final Map<String, List<FlowEvent>> journals = new HashMap<>();
    private final Map<String, ExecutionLease> executionLeases = new HashMap<>();
    private final Map<String, ResourceLease> resourceLeases = new HashMap<>();
    private long nextLeaseEpoch;

    /** Creates a store whose fence checks use the system UTC clock. */
    public InMemoryFlowExecutionStore() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a store with an explicit clock for deterministic fence-expiry checks.
     *
     * @param clock clock used when validating a {@link MutationFence} during append
     */
    public InMemoryFlowExecutionStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized IdempotencyClaimResult createOrGet(String namespace, String key,
                                                            FlowExecutionSnapshot initial) {
        if (namespace == null || namespace.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotency namespace and key cannot be blank");
        }
        String claim = namespace + "\u0000" + key;
        String existingId = idempotency.get(claim);
        if (existingId != null) {
            FlowExecutionSnapshot existing = snapshots.get(existingId);
            if (!existing.definitionFingerprint().equals(initial.definitionFingerprint())
                    || !existing.requestFingerprint().equals(initial.requestFingerprint())) {
                throw new FlowStoreException("TXFLOW_IDEMPOTENCY_CONFLICT",
                        "Idempotency claim fingerprints do not match");
            }
            return new IdempotencyClaimResult(existing, false);
        }
        if (snapshots.putIfAbsent(initial.executionId(), initial) != null) {
            throw new FlowStoreException("TXFLOW_EXECUTION_ID_CONFLICT", "Execution ID already exists");
        }
        idempotency.put(claim, initial.executionId());
        journals.put(initial.executionId(), new ArrayList<>());
        return new IdempotencyClaimResult(initial, true);
    }

    @Override
    public Optional<FlowExecutionSnapshot> get(String executionId) {
        return Optional.ofNullable(snapshots.get(executionId));
    }

    @Override
    public synchronized FlowExecutionSnapshot append(String executionId, long expectedRevision,
                                                      MutationFence fence, List<FlowEvent> events,
                                                      UnaryOperator<FlowExecutionSnapshot> mutation) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(mutation, "mutation");
        FlowExecutionSnapshot current = requireSnapshot(executionId);
        if (current.revision() != expectedRevision) {
            throw new FlowStoreException("TXFLOW_REVISION_CONFLICT", "Snapshot revision changed");
        }
        validateFence(executionId, fence, clock.instant());
        List<FlowEvent> nextJournal = new ArrayList<>(
                journals.getOrDefault(executionId, List.of()));
        long lastSequence = current.lastSequence();
        for (FlowEvent event : events) {
            Objects.requireNonNull(event, "event");
            if (!executionId.equals(event.executionId())) {
                throw new FlowStoreException("TXFLOW_EVENT_SEQUENCE",
                        "Event execution does not match its journal");
            }
            if (event.sequence() != lastSequence + 1) {
                throw new FlowStoreException("TXFLOW_EVENT_SEQUENCE", "Events must be contiguous");
            }
            nextJournal.add(event);
            lastSequence = event.sequence();
        }
        FlowExecutionSnapshot mutated = Objects.requireNonNull(
                mutation.apply(current), "mutation result");
        FlowExecutionSnapshot next = new FlowExecutionSnapshot(current.executionId(),
                current.definitionFingerprint(), current.requestFingerprint(), mutated.state(),
                current.revision() + 1, lastSequence, current.compactedThroughSequence(),
                mutated.updatedAt(), mutated.data());
        // Publish only after every validation and caller-supplied operation has succeeded.
        journals.put(executionId, nextJournal);
        snapshots.put(executionId, next);
        return next;
    }

    @Override
    public synchronized EventReadResult readEvents(String executionId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1) {
            throw new IllegalArgumentException("event cursor must be non-negative and limit positive");
        }
        FlowExecutionSnapshot snapshot = requireSnapshot(executionId);
        if (afterSequence < snapshot.compactedThroughSequence()) {
            throw new FlowStoreException("EVENTS_COMPACTED",
                    "Requested event cursor has been compacted");
        }
        List<FlowEvent> selected = journals.getOrDefault(executionId, List.of()).stream()
                .filter(event -> event.sequence() > afterSequence)
                .limit(limit)
                .toList();
        long next = selected.isEmpty() ? afterSequence : selected.get(selected.size() - 1).sequence();
        return new EventReadResult(selected, next);
    }

    @Override
    public synchronized ExecutionLease acquireExecutionLease(String executionId, String owner,
                                                              Instant now, Duration duration) {
        validateLeaseRequest(now, duration);
        requireSnapshot(executionId);
        ExecutionLease current = executionLeases.get(executionId);
        if (current != null && current.expiresAt().isAfter(now) && !current.ownerToken().equals(owner)) {
            throw new FlowStoreException("TXFLOW_LEASE_CONFLICT", "Execution is leased by another owner");
        }
        ExecutionLease next = new ExecutionLease(executionId, owner, ++nextLeaseEpoch, now.plus(duration));
        executionLeases.put(executionId, next);
        return next;
    }

    @Override
    public synchronized ExecutionLease renewExecutionLease(ExecutionLease lease, Instant now, Duration duration) {
        validateLeaseRequest(now, duration);
        ExecutionLease current = requireCurrent(lease);
        if (!current.expiresAt().isAfter(now)) {
            throw new FlowStoreException("TXFLOW_LEASE_EXPIRED", "Execution lease has expired");
        }
        ExecutionLease renewed = new ExecutionLease(current.executionId(), current.ownerToken(),
                current.epoch(), now.plus(duration));
        executionLeases.put(current.executionId(), renewed);
        return renewed;
    }

    @Override
    public synchronized void releaseExecutionLease(ExecutionLease lease) {
        requireCurrent(lease);
        executionLeases.remove(lease.executionId());
    }

    @Override
    public synchronized ResourceLease acquireResourceLease(String resourceId, String executionId,
                                                            String owner, Instant now, Duration duration) {
        validateLeaseRequest(now, duration);
        ResourceLease current = resourceLeases.get(resourceId);
        if (current != null && current.expiresAt().isAfter(now)
                && !current.executionId().equals(executionId)) {
            throw new FlowStoreException("TXFLOW_RESOURCE_LEASE_CONFLICT", "Resource is already leased");
        }
        ResourceLease next = new ResourceLease(resourceId, executionId, owner,
                ++nextLeaseEpoch, now.plus(duration));
        resourceLeases.put(resourceId, next);
        return next;
    }

    @Override
    public synchronized ResourceLease renewResourceLease(ResourceLease lease, Instant now, Duration duration) {
        validateLeaseRequest(now, duration);
        ResourceLease current = requireCurrent(lease);
        if (!current.expiresAt().isAfter(now)) {
            throw new FlowStoreException("TXFLOW_RESOURCE_LEASE_EXPIRED",
                    "Resource lease has expired");
        }
        ResourceLease renewed = new ResourceLease(current.resourceId(), current.executionId(),
                current.ownerToken(), current.epoch(), now.plus(duration));
        resourceLeases.put(current.resourceId(), renewed);
        return renewed;
    }

    @Override
    public synchronized void releaseResourceLease(ResourceLease lease) {
        requireCurrent(lease);
        resourceLeases.remove(lease.resourceId());
    }

    @Override
    public synchronized void compactEvents(String executionId, long throughSequence) {
        FlowExecutionSnapshot current = requireSnapshot(executionId);
        switch (current.state()) {
            case COMPLETED:
            case PARTIALLY_COMPLETED:
            case FAILED:
            case ROLLED_BACK:
            case CANCELLED:
                break;
            default:
                throw new FlowStoreException("TXFLOW_COMPACTION_NOT_TERMINAL",
                        "Only terminal executions may be compacted");
        }
        if (throughSequence < 0) {
            throw new IllegalArgumentException("Compaction sequence cannot be negative");
        }
        if (throughSequence > current.lastSequence()) {
            throw new IllegalArgumentException("Cannot compact beyond the journal tail");
        }
        if (throughSequence <= current.compactedThroughSequence()) {
            return;
        }
        List<FlowEvent> compactedJournal = new ArrayList<>(
                journals.getOrDefault(executionId, List.of()));
        compactedJournal.removeIf(event -> event.sequence() <= throughSequence);
        FlowExecutionSnapshot compacted = new FlowExecutionSnapshot(current.executionId(),
                current.definitionFingerprint(), current.requestFingerprint(), current.state(),
                current.revision() + 1, current.lastSequence(), throughSequence,
                current.updatedAt(), current.data());
        journals.put(executionId, compactedJournal);
        snapshots.put(executionId, compacted);
    }

    private FlowExecutionSnapshot requireSnapshot(String id) {
        FlowExecutionSnapshot snapshot = snapshots.get(id);
        if (snapshot == null) throw new FlowStoreException("TXFLOW_EXECUTION_NOT_FOUND", id);
        return snapshot;
    }

    private void validateLeaseRequest(Instant now, Duration duration) {
        if (now == null || duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("lease time and positive duration are required");
        }
    }

    private void validateFence(String executionId, MutationFence fence, Instant now) {
        if (fence == null || fence.executionLease() == null) {
            throw new FlowStoreException("TXFLOW_FENCE_REQUIRED", "Mutation requires an execution lease");
        }
        if (!executionId.equals(fence.executionLease().executionId())) {
            throw new FlowStoreException("TXFLOW_STALE_FENCE",
                    "Execution lease does not fence the target execution");
        }
        ExecutionLease executionLease = requireCurrent(fence.executionLease());
        if (!executionLease.expiresAt().isAfter(now)) {
            throw new FlowStoreException("TXFLOW_LEASE_EXPIRED", "Execution lease has expired");
        }
        for (ResourceLease resource : fence.resourceLeases()) {
            ResourceLease current = requireCurrent(resource);
            if (!executionId.equals(current.executionId())
                    || !executionLease.ownerToken().equals(current.ownerToken())) {
                throw new FlowStoreException("TXFLOW_STALE_RESOURCE_FENCE",
                        "Resource lease does not belong to the target execution owner");
            }
            if (!current.expiresAt().isAfter(now)) {
                throw new FlowStoreException("TXFLOW_RESOURCE_LEASE_EXPIRED", "Resource lease has expired");
            }
        }
    }

    private ExecutionLease requireCurrent(ExecutionLease lease) {
        Objects.requireNonNull(lease, "lease");
        ExecutionLease current = executionLeases.get(lease.executionId());
        if (current == null || current.epoch() != lease.epoch()
                || !current.ownerToken().equals(lease.ownerToken())) {
            throw new FlowStoreException("TXFLOW_STALE_FENCE", "Execution lease fence is stale");
        }
        return current;
    }

    private ResourceLease requireCurrent(ResourceLease lease) {
        Objects.requireNonNull(lease, "lease");
        ResourceLease current = resourceLeases.get(lease.resourceId());
        if (current == null || current.epoch() != lease.epoch()
                || !current.executionId().equals(lease.executionId())
                || !current.ownerToken().equals(lease.ownerToken())) {
            throw new FlowStoreException("TXFLOW_STALE_RESOURCE_FENCE", "Resource lease fence is stale");
        }
        return current;
    }
}
