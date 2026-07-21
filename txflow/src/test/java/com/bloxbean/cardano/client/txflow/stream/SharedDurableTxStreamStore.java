package com.bloxbean.cardano.client.txflow.stream;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only durable {@link TxStreamStateStore} backed by shared, restart-surviving
 * in-memory maps.
 *
 * <p>It reports {@link #isDurable()} {@code true} and retains item registrations,
 * write-ahead bindings, projections, and planned records across stream
 * instances: handing the <em>same</em> instance to a second
 * {@link TxFlowStream} simulates a process restart against a durable backing.
 * Eviction is a no-op — a durable store retains settled items (the documented
 * lift of the stream's retention cap) — so re-attach always sees the full
 * planning history. This is the store-agnostic correctness fixture the JDBC
 * backing will later have to match.</p>
 */
final class SharedDurableTxStreamStore implements TxStreamStateStore {
    private final Map<String, TxStreamItemRecord> records = new ConcurrentHashMap<>();
    private final Map<String, TxStreamBinding> bindings = new ConcurrentHashMap<>();
    private final Map<String, BindingOutcome> outcomes = new ConcurrentHashMap<>();
    private final Map<String, ProjectionEntry> projections = new ConcurrentHashMap<>();
    private final Map<String, TxStreamPlannedRecord> planned = new ConcurrentHashMap<>();
    /**
     * Additional planned records injected directly, bypassing executionId
     * keying, so a re-attach can be presented two records that collide on the
     * same execution id (the BUG-3 latent-hang probe).
     */
    private final List<TxStreamPlannedRecord> extraPlanned = new CopyOnWriteArrayList<>();
    private final Map<String, TxStreamBatchResult> batches = new ConcurrentHashMap<>();
    private final Map<String, String> bootstrapFingerprints = new ConcurrentHashMap<>();
    /**
     * When set, {@link #confirmBinding} validates the binding but does not
     * record the outcome — modelling a crash between {@code start} and
     * confirm, i.e. a binding left in the {@code DISPATCHING} state.
     */
    volatile boolean suppressConfirmOutcome;

    /** Ownership-lease state, shared across the "restarted" instances. */
    private final Map<String, StreamOwnershipLease> ownershipLeases = new HashMap<>();
    private final Map<String, Long> ownershipEpochHighWater = new HashMap<>();
    private final Object ownershipLock = new Object();

    /**
     * Whether this durable store advertises ownership support (Finding D). A
     * durable store that implements the SPI trio must report {@code true}; a
     * durable store that does NOT must report {@code false} so the stream builder
     * rejects ownership at {@code build()} rather than wedging every instance in
     * STANDBY. Defaults to true (this double implements the trio); a test flips it
     * false to exercise the build-time guard.
     */
    volatile boolean supportsOwnership = true;

    @Override
    public boolean isDurable() {
        return true;
    }

    @Override
    public boolean supportsOwnership() {
        return supportsOwnership;
    }

    @Override
    public Optional<StreamOwnershipLease> tryAcquireOwnership(String streamId, String ownerToken,
                                                             Instant now, Duration duration) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(ownerToken, "ownerToken");
        validateLeaseRequest(now, duration);
        synchronized (ownershipLock) {
            StreamOwnershipLease current = ownershipLeases.get(streamId);
            if (current != null && current.expiresAt().isAfter(now)
                    && !current.ownerToken().equals(ownerToken)) {
                return Optional.empty();
            }
            long epoch = ownershipEpochHighWater.getOrDefault(streamId, 0L) + 1L;
            StreamOwnershipLease acquired =
                    new StreamOwnershipLease(streamId, ownerToken, epoch, now.plus(duration));
            ownershipLeases.put(streamId, acquired);
            ownershipEpochHighWater.put(streamId, epoch);
            return Optional.of(acquired);
        }
    }

    @Override
    public StreamOwnershipLease renewOwnership(StreamOwnershipLease lease, Instant now,
                                               Duration duration) {
        Objects.requireNonNull(lease, "lease");
        validateLeaseRequest(now, duration);
        synchronized (ownershipLock) {
            StreamOwnershipLease current = ownershipLeases.get(lease.streamId());
            if (current == null || current.epoch() != lease.epoch()
                    || !current.ownerToken().equals(lease.ownerToken())) {
                throw new TxStreamException("TXSTREAM_OWNERSHIP_FENCED",
                        "Ownership lease for stream '" + lease.streamId()
                                + "' is no longer current (epoch " + lease.epoch() + ")");
            }
            StreamOwnershipLease renewed = new StreamOwnershipLease(current.streamId(),
                    current.ownerToken(), current.epoch(), now.plus(duration));
            ownershipLeases.put(current.streamId(), renewed);
            return renewed;
        }
    }

    @Override
    public void releaseOwnership(StreamOwnershipLease lease) {
        Objects.requireNonNull(lease, "lease");
        synchronized (ownershipLock) {
            StreamOwnershipLease current = ownershipLeases.get(lease.streamId());
            if (current != null && current.epoch() == lease.epoch()
                    && current.ownerToken().equals(lease.ownerToken())) {
                ownershipLeases.remove(lease.streamId());
            }
        }
    }

    private static void validateLeaseRequest(Instant now, Duration duration) {
        if (now == null || duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("lease time and positive duration are required");
        }
    }

    @Override
    public void persistBootstrapFingerprint(String streamId, String fingerprint) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        bootstrapFingerprints.put(streamId, fingerprint);
    }

    @Override
    public Optional<String> getBootstrapFingerprint(String streamId) {
        Objects.requireNonNull(streamId, "streamId");
        return Optional.ofNullable(bootstrapFingerprints.get(streamId));
    }

    @Override
    public void registerItem(TxStreamItemRecord record) {
        Objects.requireNonNull(record, "record");
        if (records.putIfAbsent(record.itemId(), record) != null) {
            throw new TxStreamDuplicateItemException(record.itemId(),
                    "Item is already registered: " + record.itemId());
        }
    }

    @Override
    public void bind(String itemId, TxStreamBinding binding) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(binding, "binding");
        if (!records.containsKey(itemId)) {
            throw new TxStreamException("TXSTREAM_ITEM_UNKNOWN",
                    "Cannot bind unregistered item: " + itemId);
        }
        bindings.put(itemId, binding);
        outcomes.remove(itemId);
    }

    @Override
    public void confirmBinding(String itemId, BindingOutcome outcome) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(outcome, "outcome");
        if (!bindings.containsKey(itemId)) {
            throw new TxStreamException("TXSTREAM_BINDING_MISSING",
                    "No binding recorded for item: " + itemId);
        }
        if (suppressConfirmOutcome) {
            return; // model a crash before the outcome was durably recorded
        }
        outcomes.put(itemId, outcome);
    }

    @Override
    public void persistPlanned(TxStreamPlannedRecord record) {
        Objects.requireNonNull(record, "record");
        // A re-dispatch re-persists an equivalent record under the same
        // execution id (accepted idempotently); a record for a DIFFERENT claim
        // or member set must never overwrite the existing one (SPI contract).
        planned.merge(record.executionId(), record,
                (existing, incoming) -> sameClaimAndMembers(existing, incoming)
                        ? incoming : existing);
    }

    private static boolean sameClaimAndMembers(TxStreamPlannedRecord existing,
                                               TxStreamPlannedRecord incoming) {
        if (!existing.idempotencyKey().equals(incoming.idempotencyKey())) {
            return false;
        }
        Set<String> existingItems = new HashSet<>();
        existing.members().forEach(member -> existingItems.add(member.itemId()));
        Set<String> incomingItems = new HashSet<>();
        incoming.members().forEach(member -> incomingItems.add(member.itemId()));
        return existingItems.equals(incomingItems);
    }

    /**
     * Test hook: injects an additional planned record without executionId
     * keying, so {@link #listPlanned} can return two records that collide on the
     * same execution id.
     *
     * @param record planned record to add verbatim
     */
    void injectPlanned(TxStreamPlannedRecord record) {
        extraPlanned.add(record);
    }

    @Override
    public List<TxStreamPlannedRecord> listPlanned(String streamId) {
        List<TxStreamPlannedRecord> result = new ArrayList<>();
        for (TxStreamPlannedRecord record : planned.values()) {
            if (record.streamId().equals(streamId)) {
                result.add(record);
            }
        }
        for (TxStreamPlannedRecord record : extraPlanned) {
            if (record.streamId().equals(streamId)) {
                result.add(record);
            }
        }
        return result;
    }

    @Override
    public List<String> listNonTerminalItemIds(String streamId) {
        List<String> result = new ArrayList<>();
        for (ProjectionEntry entry : projections.values()) {
            TxStreamItemResult projection = entry.result();
            if (projection.getStreamId().equals(streamId) && !isFinal(projection.getStatus())) {
                result.add(projection.getItemId());
            }
        }
        return result;
    }

    private static boolean isFinal(TxStreamItemStatus status) {
        return status == TxStreamItemStatus.CONFIRMED
                || status == TxStreamItemStatus.FAILED
                || status == TxStreamItemStatus.CANCELLED;
    }

    @Override
    public boolean projectItem(TxStreamItemResult result, long sourceSequence) {
        Objects.requireNonNull(result, "result");
        boolean[] applied = new boolean[1];
        projections.compute(result.getItemId(), (ignored, existing) -> {
            if (existing != null && sourceSequence <= existing.sequence()) {
                return existing;
            }
            applied[0] = true;
            return new ProjectionEntry(result, sourceSequence);
        });
        return applied[0];
    }

    @Override
    public Optional<TxStreamItemResult> getItem(String streamId, String itemId) {
        ProjectionEntry entry = projections.get(itemId);
        if (entry == null || !entry.result().getStreamId().equals(streamId)) {
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    @Override
    public Optional<Long> lastProjectionSequence(String streamId, String itemId) {
        ProjectionEntry entry = projections.get(itemId);
        if (entry == null || !entry.result().getStreamId().equals(streamId)) {
            return Optional.empty();
        }
        return Optional.of(entry.sequence());
    }

    @Override
    public void evictItem(String itemId) {
        // Durable: retain settled items indefinitely (retention-cap lift).
    }

    @Override
    public void recordBatch(TxStreamBatchResult batch) {
        Objects.requireNonNull(batch, "batch");
        batches.put(batch.batchId(), batch);
    }

    @Override
    public Optional<TxStreamBatchResult> getBatch(String streamId, String batchId) {
        TxStreamBatchResult batch = batches.get(batchId);
        if (batch == null || !batch.streamId().equals(streamId)) {
            return Optional.empty();
        }
        return Optional.of(batch);
    }

    @Override
    public void evictBatch(String batchId) {
        // Durable: retain terminal batches.
    }

    // ---- test inspection helpers ----

    Optional<TxStreamBinding> binding(String itemId) {
        return Optional.ofNullable(bindings.get(itemId));
    }

    Optional<BindingOutcome> outcome(String itemId) {
        return Optional.ofNullable(outcomes.get(itemId));
    }

    Optional<TxStreamPlannedRecord> plannedByExecution(String executionId) {
        return Optional.ofNullable(planned.get(executionId));
    }

    Map<String, TxStreamPlannedRecord> allPlanned() {
        return Map.copyOf(planned);
    }

    private record ProjectionEntry(TxStreamItemResult result, long sequence) {
    }
}
