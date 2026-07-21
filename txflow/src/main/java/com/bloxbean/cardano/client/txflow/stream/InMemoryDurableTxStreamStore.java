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

/**
 * Thread-safe, durable-mode in-memory {@link TxStreamStateStore}.
 *
 * <p>Unlike {@link InMemoryTxStreamStore}, this store reports
 * {@link #isDurable()} {@code true}: it retains item registrations,
 * write-ahead bindings, projections, and planned records across
 * {@link TxFlowStream} instances, so handing the <em>same</em> instance to a
 * second stream re-attaches to the executions the first instance planned
 * (ADR 0004 Decision 5). Eviction is a no-op — a durable store retains settled
 * items so re-attach always sees the full planning history (the retention-cap
 * lift). {@link #projectItem} enforces the strict per-item CAS
 * ({@code sourceSequence > stored}) and {@link #lastProjectionSequence} exposes
 * the stored watermark, both required by the restart re-attach protocol.</p>
 *
 * <p><b>Durability scope.</b> This store is durable in the SPI sense — planning
 * metadata survives a stream <em>restart</em> within one running process — but
 * it is <b>not</b> crash-durable: all state is lost when the JVM exits. Use the
 * relational {@code RdbmsTxStreamStateStore} for restart durability across
 * process termination. Pairing this store with a durable stream still requires a
 * durable engine store, exactly as the relational store does.</p>
 */
public final class InMemoryDurableTxStreamStore implements TxStreamStateStore {
    private final Map<String, TxStreamItemRecord> records = new ConcurrentHashMap<>();
    private final Map<String, TxStreamBinding> bindings = new ConcurrentHashMap<>();
    private final Map<String, BindingOutcome> outcomes = new ConcurrentHashMap<>();
    private final Map<String, ProjectionEntry> projections = new ConcurrentHashMap<>();
    private final Map<String, TxStreamPlannedRecord> planned = new ConcurrentHashMap<>();
    private final Map<String, TxStreamBatchResult> batches = new ConcurrentHashMap<>();
    private final Map<String, String> bootstrapFingerprints = new ConcurrentHashMap<>();

    /** Current active ownership lease per stream; guarded by {@link #ownershipLock}. */
    private final Map<String, StreamOwnershipLease> ownershipLeases = new HashMap<>();
    /**
     * Per-stream epoch high-water, retained across a release so a later acquire
     * still mints a strictly higher epoch. Guarded by {@link #ownershipLock}.
     */
    private final Map<String, Long> ownershipEpochHighWater = new HashMap<>();
    private final Object ownershipLock = new Object();

    @Override
    public boolean isDurable() {
        return true;
    }

    @Override
    public boolean supportsOwnership() {
        return true;
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
                return Optional.empty(); // held by a different, unexpired owner
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
                                + "' is no longer current (epoch " + lease.epoch()
                                + " superseded or released)");
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
                // Drop the active lease; the epoch high-water is retained so a
                // later acquire mints a strictly higher epoch (monotonicity).
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
        outcomes.put(itemId, outcome);
    }

    @Override
    public void persistPlanned(TxStreamPlannedRecord record) {
        Objects.requireNonNull(record, "record");
        // A re-dispatch re-persists an equivalent record under the same execution
        // id (accepted idempotently); a record for a DIFFERENT claim or member set
        // must never overwrite the existing one (SPI contract).
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

    @Override
    public List<TxStreamPlannedRecord> listPlanned(String streamId) {
        List<TxStreamPlannedRecord> result = new ArrayList<>();
        for (TxStreamPlannedRecord record : planned.values()) {
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

    private record ProjectionEntry(TxStreamItemResult result, long sequence) {
    }
}
