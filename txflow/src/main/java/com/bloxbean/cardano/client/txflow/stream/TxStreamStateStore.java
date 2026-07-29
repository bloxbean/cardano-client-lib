package com.bloxbean.cardano.client.txflow.stream;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stream-owned store for planning metadata and denormalized item projections.
 * <p>
 * Authority is split: the engine's execution store owns execution truth, while
 * this store owns the planning metadata the engine never sees — the item
 * registry and the item-to-execution binding. Those writes are authoritative
 * and <em>fail closed</em>: a failed {@link #registerItem(TxStreamItemRecord)}
 * rejects the submit and a failed {@link #bind(String, TxStreamBinding)} fails
 * the item before the engine is invoked. Item projections, by contrast, are
 * denormalized views of engine truth; {@link #projectItem} writes are
 * best-effort and guarded by a per-item sequence so a stale write can never
 * overwrite a newer one.
 * <p>
 * Iteration 1A ships the in-memory implementation only; a durable
 * implementation arrives with iteration 2 and keeps this contract.
 */
public interface TxStreamStateStore {
    /**
     * Registers one accepted item. Authoritative; fails closed.
     *
     * @param record item registration record
     * @throws TxStreamDuplicateItemException when the item id is already registered
     * @throws TxStreamException when the record cannot be stored
     */
    void registerItem(TxStreamItemRecord record);

    /**
     * Writes the item's write-ahead execution binding with state
     * {@code DISPATCHING}. Authoritative; fails closed — the engine must never
     * be invoked without a stored binding.
     *
     * @param itemId registered item identity
     * @param binding execution binding derived from the item's claim
     * @throws TxStreamException when the binding cannot be stored
     */
    void bind(String itemId, TxStreamBinding binding);

    /**
     * Records the engine start outcome on a previously written binding.
     *
     * @param itemId registered item identity
     * @param outcome start disposition
     * @throws TxStreamException when no binding exists for the item
     */
    void confirmBinding(String itemId, BindingOutcome outcome);

    /**
     * Stores a denormalized item projection when it is newer than the stored one.
     * <p>
     * The stream enforces the status transition table before calling this
     * method; the store enforces only compare-and-swap ordering on
     * {@code sourceSequence}.
     *
     * @param result projected item snapshot
     * @param sourceSequence monotonically increasing per-item projection sequence
     * @return {@code true} when stored; {@code false} when rejected as stale
     */
    boolean projectItem(TxStreamItemResult result, long sourceSequence);

    /**
     * Returns the latest stored projection for an item.
     *
     * @param streamId stream id
     * @param itemId item id
     * @return stored projection when present
     */
    Optional<TxStreamItemResult> getItem(String streamId, String itemId);

    /**
     * Returns the per-item projection sequence of the latest stored projection —
     * the compare-and-swap watermark {@link #projectItem} last accepted for this
     * item. Restart re-attach seeds a re-attached item's live projection counter
     * from this value so its first authoritative write lands at
     * {@code storedSequence + 1} and <b>dominates</b> the pre-crash durable
     * projection's CAS; without domination an authoritative fast-forward
     * (a re-attached item's terminal repair) is silently rejected as stale, the
     * durable projection stays non-terminal forever, and the item is re-attached
     * on every subsequent restart.
     * <p>
     * Durable stores that persist projections <b>must</b> override this and
     * return the real stored sequence; a durable store that leaves it empty
     * forces re-attach onto a high sequence floor (still correct, but it forfeits
     * exact CAS ordering). The default returns empty, which is correct for a
     * non-durable store (re-attach never runs against it).
     *
     * @param streamId stream id
     * @param itemId item id
     * @return the stored per-item projection sequence, or empty when the item has
     *         no stored projection (or the store does not track it)
     */
    default Optional<Long> lastProjectionSequence(String streamId, String itemId) {
        return Optional.empty();
    }

    /**
     * Evicts one <em>settled</em> item's registration, binding, and projection
     * under the stream's retention policy
     * ({@code Builder.maxRetainedSettledItems}).
     * <p>
     * In-memory implementations must remove the item's state so a long-lived
     * stream stays bounded; the engine's execution store remains authoritative
     * for the execution itself, so eviction never loses execution truth. A
     * durable stream store (iteration 2) may ignore eviction and retain
     * settled items indefinitely — that is the documented lift for the
     * retention cap. The stream only ever evicts items whose promise has
     * settled with a final status.
     *
     * @param itemId item to evict
     */
    default void evictItem(String itemId) {
    }

    /**
     * Records a denormalized batch projection (ADR 0004 batch model). Batch
     * writes are projection-side and <em>isolated</em>: a failing write is
     * logged and never fails items, planning, or dispatch — batch status is
     * observability metadata derived from member items, never engine
     * identity.
     *
     * @param batch batch snapshot to store
     */
    default void recordBatch(TxStreamBatchResult batch) {
    }

    /**
     * Returns the latest stored batch projection.
     *
     * @param streamId stream id
     * @param batchId stream-scoped batch id ({@code "batch-N"})
     * @return stored batch snapshot when present
     */
    default Optional<TxStreamBatchResult> getBatch(String streamId, String batchId) {
        return Optional.empty();
    }

    /**
     * Evicts one terminal batch projection under the stream's retention
     * policy; the same lifecycle as {@link #evictItem(String)}.
     *
     * @param batchId batch to evict
     */
    default void evictBatch(String batchId) {
    }

    /**
     * Reports whether this store durably retains planning metadata across a
     * stream restart, so a new stream instance handed the same store can
     * re-attach to executions the crashed instance planned (ADR 0004
     * Decision 5).
     * <p>
     * The default in-memory store is <b>not</b> durable and returns
     * {@code false}: a restarted stream loses its planning metadata and relies
     * on idempotent source redelivery. A durable store returns {@code true},
     * and the stream builder then <b>requires</b> a durable engine store as
     * well — pairing durable stream state with an in-memory engine would
     * re-dispatch executions that already ran (a transaction duplicator), so it
     * is rejected at {@code build()}.
     *
     * @return {@code true} when planning metadata survives a stream restart
     */
    default boolean isDurable() {
        return false;
    }

    /**
     * Persists the planned request alongside its write-ahead binding so a
     * crash before {@code FlowEngine.start(...)} can be re-dispatched without
     * source redelivery (ADR 0004 Decision 5). Authoritative and part of the
     * fail-closed bind phase; called only in durable mode
     * ({@link #isDurable()}), before the engine is invoked. Non-durable stores
     * ignore it.
     * <p>
     * The record contains only the portable-encoded flow, non-sensitive
     * bindings, and secure-binding references plus fingerprints — never secret
     * values. Enforcing the no-secrets rule at the caller boundary is the
     * stream's responsibility; a request whose sensitive values are not
     * expressible as secure references fails the item typed
     * {@code TXSTREAM_NON_PERSISTABLE_SECRET} at bind time before this method
     * is reached.
     * <p>
     * Records are keyed by their deterministic {@code executionId}. A restart
     * re-dispatch re-persists an <em>equivalent</em> record under the same
     * execution id (the same claim, members, and portable flow), which the store
     * may accept idempotently; but the store <b>must not</b> let a record for a
     * <em>different</em> claim or member set overwrite an existing record for
     * that execution id — that would be a lost-update corrupting the planning
     * history the re-attach protocol depends on.
     *
     * @param record planned execution record to persist
     * @throws TxStreamException when the record cannot be stored (fails the
     *         item closed, before the engine is invoked)
     */
    default void persistPlanned(TxStreamPlannedRecord record) {
    }

    /**
     * Lists the persisted planned records for one stream, for restart
     * re-attach. Durable stores override; non-durable stores return an empty
     * list.
     *
     * @param streamId stream id
     * @return persisted planned records, or an empty list
     */
    default List<TxStreamPlannedRecord> listPlanned(String streamId) {
        return List.of();
    }

    /**
     * Lists the ids of items whose latest stored projection is <em>not</em> a
     * final status (confirmed, failed, or cancelled) — the candidates a
     * restart re-attach must resolve against engine truth. A
     * {@code RECOVERY_REQUIRED} item is non-final and is included. Durable
     * stores override; non-durable stores return an empty list.
     *
     * @param streamId stream id
     * @return non-terminal item ids, or an empty list
     */
    default List<String> listNonTerminalItemIds(String streamId) {
        return List.of();
    }

    /**
     * Persists the fan-out bootstrap fingerprint for a partitioned durable
     * stream — the byte-stable identity of its funding source, seed, lane count,
     * and lane-address list <em>in order</em> (ADR 0004 Decision 2; Open
     * Question 3). On the next {@link TxFlowStream#start() start}, the stream
     * compares the current fingerprint against the persisted one and fails fast
     * ({@code TXSTREAM_BOOTSTRAP_CONFIG_DRIFT}) on any difference, rather than
     * minting a new split that re-drains the funding wallet.
     * <p>
     * Durable stores <b>must</b> override this and {@link #getBootstrapFingerprint}
     * as a consistent pair for drift detection to work; a durable store that
     * leaves them defaulted simply forfeits the loud early-warning (the engine
     * idempotency claim is still the real exactly-once guard). Non-durable stores
     * ignore it. Called only in durable mode, after a successful bootstrap.
     *
     * @param streamId stream id
     * @param fingerprint byte-stable bootstrap fingerprint (never a secret)
     */
    default void persistBootstrapFingerprint(String streamId, String fingerprint) {
    }

    /**
     * Returns the persisted fan-out bootstrap fingerprint for a partitioned
     * durable stream, when one was recorded by a prior successful bootstrap
     * (see {@link #persistBootstrapFingerprint}). The default returns empty,
     * which disables drift detection — correct for a non-durable store.
     *
     * @param streamId stream id
     * @return the persisted bootstrap fingerprint, or empty when none is recorded
     */
    default Optional<String> getBootstrapFingerprint(String streamId) {
        return Optional.empty();
    }

    /**
     * Reports whether this store implements the ownership-lease trio
     * ({@link #tryAcquireOwnership}, {@link #renewOwnership},
     * {@link #releaseOwnership}) with real epoch-fenced semantics, so a
     * {@link TxFlowStream} may safely enable single-owner active/standby
     * ownership against it (ADR 0004 iteration 3d).
     * <p>
     * The default is {@code false}: the defaulted SPI trio hands out no lease
     * ({@link #tryAcquireOwnership} returns empty), so an ownership stream on such
     * a store would leave <em>every</em> instance in {@code STANDBY} forever —
     * accepting nothing, dispatching nothing, with no error to explain it. The
     * stream builder therefore <b>requires</b> {@code supportsOwnership() == true}
     * when ownership is enabled and fails {@code build()} otherwise, converting
     * that silent wedge into a typed configuration error. A store that genuinely
     * implements the trio (the shipped durable in-memory store and the relational
     * store) must override this to return {@code true}.
     *
     * @return {@code true} when this store implements the epoch-fenced ownership
     *         lease trio
     */
    default boolean supportsOwnership() {
        return false;
    }

    /**
     * Atomically acquires single-owner dispatch ownership of a stream, minting an
     * epoch-fenced lease (ADR 0004 iteration 3 — multi-instance active/standby).
     * <p>
     * Grants iff no <em>currently-valid</em> lease is held by a <em>different</em>
     * owner: an expired lease, or a lease already held by {@code ownerToken}, may
     * be (re-)acquired; a different owner's unexpired lease returns empty (the
     * caller stands by). Every successful acquire mints a
     * <b>strictly increasing</b> {@code epoch} — a new owner's epoch always
     * exceeds any prior owner's for that stream, even across a release — so a
     * superseded owner's later {@link #renewOwnership renewal} is fenced. This
     * mirrors the engine's {@code acquireExecutionLease}; only the current
     * epoch-holder is permitted to dispatch.
     * <p>
     * Defaulted so non-durable / ownership-unaware stores are unaffected: the
     * default returns empty, which leaves every instance in standby (ownership is
     * only enabled against a store that implements this trio).
     *
     * @param streamId stream to own
     * @param ownerToken opaque, caller-supplied identity of the acquiring instance
     * @param now time against which an existing lease's expiry is evaluated
     * @param duration positive lease duration
     * @return the acquired lease, or empty when a different owner holds an
     *         unexpired lease
     */
    default Optional<StreamOwnershipLease> tryAcquireOwnership(String streamId, String ownerToken,
                                                              Instant now, Duration duration) {
        return Optional.empty();
    }

    /**
     * Extends the current ownership lease without changing its fencing epoch —
     * the periodic renewal an ACTIVE owner runs to keep dispatching.
     * <p>
     * <b>The fence:</b> renews iff the caller's epoch is still the current epoch
     * for the stream. If the lease was superseded (a different owner acquired a
     * higher epoch) or expired-and-taken, this throws a typed
     * {@code TXSTREAM_OWNERSHIP_FENCED} {@link TxStreamException}; the caller must
     * immediately stop dispatching and step down. This mirrors the engine's
     * {@code renewExecutionLease} and is what makes two instances safe — a stale
     * owner that resumes after its lease was taken over cannot renew, so it can
     * never resume dispatch.
     * <p>
     * The default throws {@code TXSTREAM_OWNERSHIP_FENCED}: an ownership-unaware
     * store holds no lease to renew, so the safe direction is to treat the caller
     * as fenced. It is never reached in practice — the default
     * {@link #tryAcquireOwnership} returns empty, so no such lease is ever handed
     * out.
     *
     * @param lease current lease previously acquired from this store
     * @param now start of the renewed interval
     * @param duration positive lease duration
     * @return the renewed lease (same epoch, extended expiry)
     * @throws TxStreamException {@code TXSTREAM_OWNERSHIP_FENCED} when the lease is
     *         no longer current (superseded or expired-and-taken)
     */
    default StreamOwnershipLease renewOwnership(StreamOwnershipLease lease, Instant now,
                                                Duration duration) {
        throw new TxStreamException("TXSTREAM_OWNERSHIP_FENCED",
                "Ownership lease is no longer current (store does not track ownership leases)");
    }

    /**
     * Releases the ownership lease iff the caller still holds the current epoch;
     * a no-op when the lease has already been superseded or released. Retains the
     * stream's epoch high-water so a subsequent acquire still mints a strictly
     * higher epoch (monotonicity survives release). The default is a no-op.
     *
     * @param lease current lease previously acquired from this store
     */
    default void releaseOwnership(StreamOwnershipLease lease) {
    }

    /**
     * Creates a thread-safe in-memory store.
     *
     * @return in-memory stream state store
     */
    static TxStreamStateStore inMemory() {
        return new InMemoryTxStreamStore();
    }

    /**
     * Creates a thread-safe, durable-mode in-memory store that retains planning
     * metadata across {@link TxFlowStream} restarts within one running process
     * (so a second stream instance handed the same store re-attaches). It is
     * durable in the SPI sense but not crash-durable — use the relational
     * {@code RdbmsTxStreamStateStore} for durability across process termination.
     *
     * @return durable-mode in-memory stream state store
     */
    static TxStreamStateStore inMemoryDurable() {
        return new InMemoryDurableTxStreamStore();
    }
}
