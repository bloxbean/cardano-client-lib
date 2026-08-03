package com.bloxbean.cardano.client.txflow.stream.contract;

import com.bloxbean.cardano.client.txflow.stream.BindingOutcome;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBatchResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBatchStatus;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBinding;
import com.bloxbean.cardano.client.txflow.stream.TxStreamDuplicateItemException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemRecord;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;
import com.bloxbean.cardano.client.txflow.stream.TxStreamPlannedRecord;
import com.bloxbean.cardano.client.txflow.stream.StreamOwnershipLease;
import com.bloxbean.cardano.client.txflow.stream.TxStreamStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable behavioral contract for durable {@link TxStreamStateStore} adapters.
 *
 * <p>The same tests run against the in-memory durable reference
 * ({@code InMemoryDurableTxStreamStore}) and every certified relational adapter.
 * They encode the store-level guarantees the ADR 0004 restart re-attach protocol
 * depends on — the six "must-replicate" points from the 2b-JDBC plan — plus the
 * register/bind/confirm/project/batch surface. Assertions are limited to
 * behavior every durable adapter can satisfy: a projected error is compared by
 * its stable {@link TxStreamException#getCode() code} and message (a relational
 * store cannot round-trip an arbitrary {@link Throwable} instance).</p>
 */
public abstract class TxStreamStateStoreContract {
    /** Fixed stream id used by the contract. */
    protected static final String STREAM = "payouts";
    /** Fixed whole-second instant (round-trips through microsecond precision). */
    protected static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    /** Store under test, recreated fresh for each test. */
    protected TxStreamStateStore store;

    /**
     * Creates a fresh empty durable store.
     *
     * @return new durable stream state store
     * @throws Exception when the store cannot be created
     */
    protected abstract TxStreamStateStore createStore() throws Exception;

    @BeforeEach
    void openStore() throws Exception {
        store = createStore();
    }

    @AfterEach
    void closeStore() throws Exception {
        if (store instanceof AutoCloseable closeable) closeable.close();
    }

    @Test
    void reportsDurable() {
        assertTrue(store.isDurable());
    }

    @Test
    void registerItemIsAuthoritativeAndRejectsDuplicates() {
        store.registerItem(record("item-1"));

        TxStreamDuplicateItemException duplicate = assertThrows(
                TxStreamDuplicateItemException.class, () -> store.registerItem(record("item-1")));
        assertEquals("item-1", duplicate.getItemId());
    }

    @Test
    void bindRequiresRegistrationAndConfirmRecordsOutcome() {
        assertCode("TXSTREAM_ITEM_UNKNOWN",
                () -> store.bind("item-1", binding("exec-1")));

        store.registerItem(record("item-1"));
        store.bind("item-1", binding("exec-1"));
        store.confirmBinding("item-1", BindingOutcome.CREATED);
        // Re-binding is legal (write-ahead re-dispatch) and clears the prior outcome.
        store.bind("item-1", binding("exec-1"));
        store.confirmBinding("item-1", BindingOutcome.MATCHED);
    }

    @Test
    void confirmBindingWithoutABindingFailsClosed() {
        store.registerItem(record("item-1"));
        assertCode("TXSTREAM_BINDING_MISSING",
                () -> store.confirmBinding("item-1", BindingOutcome.CREATED));
    }

    /** Must-replicate #1 + #2: strict CAS and getItem/lastProjectionSequence consistency. */
    @Test
    void projectItemUsesStrictCasAndKeepsGetItemAndSequenceConsistent() {
        store.registerItem(record("item-1"));
        assertTrue(store.lastProjectionSequence(STREAM, "item-1").isEmpty());
        assertTrue(store.getItem(STREAM, "item-1").isEmpty());

        assertTrue(store.projectItem(projection("item-1", TxStreamItemStatus.PLANNED), 5));
        assertEquals(5L, store.lastProjectionSequence(STREAM, "item-1").orElseThrow());
        assertEquals(TxStreamItemStatus.PLANNED,
                store.getItem(STREAM, "item-1").orElseThrow().getStatus());

        // A stale (<) and an equal (<=) source sequence are both rejected.
        assertFalse(store.projectItem(projection("item-1", TxStreamItemStatus.SUBMITTED), 4));
        assertFalse(store.projectItem(projection("item-1", TxStreamItemStatus.SUBMITTED), 5));
        assertEquals(TxStreamItemStatus.PLANNED,
                store.getItem(STREAM, "item-1").orElseThrow().getStatus());
        assertEquals(5L, store.lastProjectionSequence(STREAM, "item-1").orElseThrow());

        // A strictly greater source sequence dominates.
        assertTrue(store.projectItem(projection("item-1", TxStreamItemStatus.SUBMITTED), 6));
        assertEquals(6L, store.lastProjectionSequence(STREAM, "item-1").orElseThrow());
        assertEquals(TxStreamItemStatus.SUBMITTED,
                store.getItem(STREAM, "item-1").orElseThrow().getStatus());
    }

    @Test
    void projectionRoundTripsEveryProjectedField() {
        store.registerItem(record("item-1"));
        TxStreamException error = new TxStreamException("TXSTREAM_TEST_FAILURE", "boom");
        TxStreamItemResult projected = TxStreamItemResult.builder(
                        STREAM, "item-1", TxStreamItemStatus.FAILED)
                .executionId("exec-1").stepId("step-1").laneName("lane-a")
                .transactionHash("abcd1234").error(error).updatedAt(NOW).build();

        assertTrue(store.projectItem(projected, 1));

        TxStreamItemResult stored = store.getItem(STREAM, "item-1").orElseThrow();
        assertEquals(STREAM, stored.getStreamId());
        assertEquals("item-1", stored.getItemId());
        assertEquals(TxStreamItemStatus.FAILED, stored.getStatus());
        assertEquals("exec-1", stored.getExecutionId());
        assertEquals("step-1", stored.getStepId());
        assertEquals("lane-a", stored.getLaneName());
        assertEquals("abcd1234", stored.getTransactionHash());
        assertEquals(NOW, stored.getUpdatedAt());
        assertTrue(stored.getError() instanceof TxStreamException);
        assertEquals("TXSTREAM_TEST_FAILURE", ((TxStreamException) stored.getError()).getCode());
        assertEquals("boom", stored.getError().getMessage());
    }

    @Test
    void getItemIsScopedToItsStreamAndReturnsEmptyForUnknowns() {
        assertTrue(store.getItem(STREAM, "missing").isEmpty());
        store.registerItem(record("item-1"));
        store.projectItem(projection("item-1", TxStreamItemStatus.PLANNED), 1);
        assertTrue(store.getItem("other-stream", "item-1").isEmpty());
        assertTrue(store.lastProjectionSequence("other-stream", "item-1").isEmpty());
    }

    /** Must-replicate #3 + #4: one record per execution id, members carry the per-item key. */
    @Test
    void persistPlannedIsOnePerExecutionAndMembersCarryPerItemIdempotencyKey() {
        TxStreamPlannedRecord planned = plannedRecord("exec-1", "flow-key",
                member("item-1", "key-1", "step-1"),
                member("item-2", "key-2", "step-2"));
        store.persistPlanned(planned);
        // A re-dispatch re-persists an equivalent record; still exactly one row.
        store.persistPlanned(plannedRecord("exec-1", "flow-key",
                member("item-1", "key-1", "step-1"),
                member("item-2", "key-2", "step-2")));

        List<TxStreamPlannedRecord> stored = store.listPlanned(STREAM);
        assertEquals(1, stored.size());
        TxStreamPlannedRecord record = stored.get(0);
        assertEquals("exec-1", record.executionId());
        assertEquals("flow-key", record.idempotencyKey());
        assertEquals(Set.of("key-1", "key-2"), keySet(record));
        assertEquals(Map.of("item-1", "key-1", "item-2", "key-2"), itemKeyMap(record));
        assertEquals("step-1", stepFor(record, "item-1"));
    }

    /** Must-replicate #3: a record for a different claim/member set never overwrites. */
    @Test
    void persistPlannedRefusesCrossClaimOverwrite() {
        store.persistPlanned(plannedRecord("exec-1", "flow-key-a", member("item-1", "key-1", "s1")));
        // Same execution id, different claim + member set: must keep the existing record.
        store.persistPlanned(plannedRecord("exec-1", "flow-key-b", member("item-9", "key-9", "s9")));

        List<TxStreamPlannedRecord> stored = store.listPlanned(STREAM);
        assertEquals(1, stored.size());
        assertEquals("flow-key-a", stored.get(0).idempotencyKey());
        assertEquals(Set.of("item-1"), itemIdSet(stored.get(0)));
    }

    @Test
    void listPlannedIsScopedToItsStream() {
        store.persistPlanned(plannedRecord(STREAM, "exec-1", "k1", member("item-1", "key-1", "s1")));
        store.persistPlanned(plannedRecord("other", "exec-2", "k2", member("item-2", "key-2", "s2")));
        assertEquals(1, store.listPlanned(STREAM).size());
        assertEquals("exec-1", store.listPlanned(STREAM).get(0).executionId());
    }

    @Test
    void plannedRecordRoundTripsBindingsReferencesAndFingerprints() {
        TxStreamPlannedRecord planned = new TxStreamPlannedRecord(STREAM, "exec-1", "flow-key",
                "lane-a", "addr:sender", "{\"apiVersion\":\"v1alpha1\"}",
                Map.of("amount", 100L, "memo", "hello", "flag", Boolean.TRUE),
                Map.of("signingKey", "vault://payouts/key"),
                Map.of("signingKey", "fp-9f8e"),
                List.of(member("item-1", "key-1", "step-1")));
        store.persistPlanned(planned);

        TxStreamPlannedRecord stored = store.listPlanned(STREAM).get(0);
        assertEquals("addr:sender", stored.canonicalSpendingIdentity());
        assertEquals("{\"apiVersion\":\"v1alpha1\"}", stored.portableFlow());
        assertEquals(Map.of("amount", 100L, "memo", "hello", "flag", Boolean.TRUE),
                stored.bindings());
        assertEquals(Map.of("signingKey", "vault://payouts/key"),
                stored.secureBindingReferences());
        assertEquals(Map.of("signingKey", "fp-9f8e"), stored.secureBindingFingerprints());
        assertNull(stored.templateId());
    }

    /**
     * ADR 0004 iteration 3: a template-invocation record round-trips its
     * template reference AND (DEV-T1) the template-definition fingerprint used
     * for re-attach config-drift detection.
     */
    @Test
    void plannedRecordRoundTripsTemplateReference() {
        TxStreamPlannedRecord planned = new TxStreamPlannedRecord(STREAM, "exec-t", "flow-key",
                "lane-a", "addr:sender", "{\"apiVersion\":\"v1alpha1\"}",
                Map.of("amount", 42L), Map.of(), Map.of(),
                List.of(member("item-t", "key-t", "step-t")), "payout-template",
                "tmpl-fp-abc123");
        store.persistPlanned(planned);

        TxStreamPlannedRecord stored = store.listPlanned(STREAM).stream()
                .filter(record -> "exec-t".equals(record.executionId()))
                .findFirst().orElseThrow();
        assertEquals("payout-template", stored.templateId());
        assertEquals("tmpl-fp-abc123", stored.templateFingerprint());
        assertEquals(Map.of("amount", 42L), stored.bindings());
    }

    /** Must-replicate #5: excludes final, includes RECOVERY_REQUIRED, shrinks on terminal drive. */
    @Test
    void listNonTerminalItemIdsExcludesFinalIncludesRecoveryRequiredAndShrinks() {
        projectFresh("planned", TxStreamItemStatus.PLANNED, 1);
        projectFresh("submitted", TxStreamItemStatus.SUBMITTED, 1);
        projectFresh("recovery", TxStreamItemStatus.RECOVERY_REQUIRED, 1);
        projectFresh("confirmed", TxStreamItemStatus.CONFIRMED, 1);
        projectFresh("failed", TxStreamItemStatus.FAILED, 1);
        projectFresh("cancelled", TxStreamItemStatus.CANCELLED, 1);

        assertEquals(Set.of("planned", "submitted", "recovery"),
                Set.copyOf(store.listNonTerminalItemIds(STREAM)));

        // Driving a non-terminal row terminal shrinks the set (BUG-1 repair / BUG-4 reap).
        assertTrue(store.projectItem(projection("recovery", TxStreamItemStatus.CONFIRMED), 2));
        assertEquals(Set.of("planned", "submitted"),
                Set.copyOf(store.listNonTerminalItemIds(STREAM)));
    }

    @Test
    void listNonTerminalItemIdsIsScopedToItsStream() {
        store.projectItem(projection(STREAM, "here", TxStreamItemStatus.PLANNED), 1);
        store.projectItem(projection("elsewhere", "there", TxStreamItemStatus.PLANNED), 1);
        assertEquals(List.of("here"), store.listNonTerminalItemIds(STREAM));
    }

    /** Must-replicate #6: durable eviction is a no-op — settled items are retained. */
    @Test
    void evictItemIsANoOpUnderDurableRetention() {
        store.registerItem(record("item-1"));
        store.bind("item-1", binding("exec-1"));
        store.projectItem(projection("item-1", TxStreamItemStatus.CONFIRMED), 1);
        store.persistPlanned(plannedRecord("exec-1", "flow-key", member("item-1", "key-1", "s1")));

        store.evictItem("item-1");

        assertTrue(store.getItem(STREAM, "item-1").isPresent());
        assertEquals(1, store.listPlanned(STREAM).size());
        assertEquals(1L, store.lastProjectionSequence(STREAM, "item-1").orElseThrow());
    }

    /** FINDING-2: the fan-out bootstrap fingerprint round-trips, scopes per stream, and overwrites. */
    @Test
    void bootstrapFingerprintRoundTripsScopesPerStreamAndOverwrites() {
        assertTrue(store.getBootstrapFingerprint(STREAM).isEmpty());

        store.persistBootstrapFingerprint(STREAM, "bootstrap:2:abc123");
        assertEquals("bootstrap:2:abc123", store.getBootstrapFingerprint(STREAM).orElseThrow());
        // Scoped per stream: another stream's fingerprint is independent.
        assertTrue(store.getBootstrapFingerprint("other-stream").isEmpty());

        // A re-run persists the same (or, after a drift the stream catches, a new)
        // value for the same stream id — one row, last write wins.
        store.persistBootstrapFingerprint(STREAM, "bootstrap:2:def456");
        assertEquals("bootstrap:2:def456", store.getBootstrapFingerprint(STREAM).orElseThrow());
    }

    // ---- ownership lease (ADR 0004 iteration 3d — active/standby failover) ----

    private static final Duration LEASE = Duration.ofSeconds(30);

    @Test
    void ownershipAcquireExcludesADifferentOwnerWhileTheLeaseIsUnexpired() {
        StreamOwnershipLease a = store.tryAcquireOwnership(STREAM, "owner-a", NOW, LEASE)
                .orElseThrow();
        assertEquals("owner-a", a.ownerToken());
        assertTrue(a.epoch() >= 1);

        // A different owner cannot acquire while A's lease is unexpired.
        assertTrue(store.tryAcquireOwnership(STREAM, "owner-b", NOW.plusSeconds(1), LEASE).isEmpty());
        // The same owner may re-acquire (mints a strictly higher epoch).
        StreamOwnershipLease aAgain = store.tryAcquireOwnership(STREAM, "owner-a",
                NOW.plusSeconds(1), LEASE).orElseThrow();
        assertTrue(aAgain.epoch() > a.epoch());
    }

    @Test
    void ownershipExpiredLeaseIsReacquirableByAnotherOwnerWithAHigherEpoch() {
        StreamOwnershipLease a = store.tryAcquireOwnership(STREAM, "owner-a", NOW, LEASE)
                .orElseThrow();
        // After A's lease expires, B takes over with a strictly higher epoch.
        Instant afterExpiry = a.expiresAt().plusSeconds(1);
        StreamOwnershipLease b = store.tryAcquireOwnership(STREAM, "owner-b", afterExpiry, LEASE)
                .orElseThrow();
        assertEquals("owner-b", b.ownerToken());
        assertTrue(b.epoch() > a.epoch(), "a new owner's epoch must strictly exceed any prior");
    }

    @Test
    void ownershipRenewByTheCurrentEpochHolderSucceedsAndKeepsTheEpoch() {
        StreamOwnershipLease a = store.tryAcquireOwnership(STREAM, "owner-a", NOW, LEASE)
                .orElseThrow();
        StreamOwnershipLease renewed = store.renewOwnership(a, NOW.plusSeconds(10), LEASE);
        assertEquals(a.epoch(), renewed.epoch(), "renewal preserves the fencing epoch");
        assertTrue(renewed.expiresAt().isAfter(a.expiresAt()), "renewal extends the expiry");
    }

    @Test
    void ownershipRenewBySupersededEpochHolderIsFenced() {
        StreamOwnershipLease a = store.tryAcquireOwnership(STREAM, "owner-a", NOW, LEASE)
                .orElseThrow();
        // B takes over after A's lease expires (mints a higher epoch).
        Instant afterExpiry = a.expiresAt().plusSeconds(1);
        store.tryAcquireOwnership(STREAM, "owner-b", afterExpiry, LEASE).orElseThrow();

        // A's stale lease can no longer renew — the FENCE.
        assertCode("TXSTREAM_OWNERSHIP_FENCED",
                () -> store.renewOwnership(a, afterExpiry.plusSeconds(1), LEASE));
    }

    @Test
    void ownershipReleaseOnlyByTheCurrentEpochHolderThenReacquireWithHigherEpoch() {
        StreamOwnershipLease a = store.tryAcquireOwnership(STREAM, "owner-a", NOW, LEASE)
                .orElseThrow();
        // A superseded/foreign lease cannot release the current one.
        StreamOwnershipLease stale = new StreamOwnershipLease(STREAM, "owner-a", a.epoch() + 100,
                a.expiresAt());
        store.releaseOwnership(stale); // no-op (not current)
        // A still holds it: a different owner is still excluded.
        assertTrue(store.tryAcquireOwnership(STREAM, "owner-b", NOW.plusSeconds(1), LEASE).isEmpty());

        // The current holder releases; a standby can immediately acquire, still
        // with a strictly higher epoch (the epoch high-water survives release).
        store.releaseOwnership(a);
        StreamOwnershipLease b = store.tryAcquireOwnership(STREAM, "owner-b", NOW.plusSeconds(2),
                LEASE).orElseThrow();
        assertEquals("owner-b", b.ownerToken());
        assertTrue(b.epoch() > a.epoch());
        // A double release (already superseded) is a no-op, not an error.
        store.releaseOwnership(a);
    }

    @Test
    void ownershipEpochIsMonotonicAcrossRepeatedTakeovers() {
        long previous = 0L;
        Instant when = NOW;
        String[] owners = {"a", "b", "a", "c", "b"};
        for (String owner : owners) {
            Optional<StreamOwnershipLease> acquired =
                    store.tryAcquireOwnership(STREAM, "owner-" + owner, when, LEASE);
            StreamOwnershipLease lease = acquired.orElseThrow();
            assertTrue(lease.epoch() > previous,
                    "epoch must strictly increase across takeovers: " + lease.epoch()
                            + " !> " + previous);
            previous = lease.epoch();
            // move past this lease's expiry so the next owner can take over
            when = lease.expiresAt().plusSeconds(1);
        }
    }

    @Test
    void durableStoreAdvertisesOwnershipSupport() {
        // A durable store that implements the epoch-fenced ownership trio must
        // advertise supportsOwnership() so the stream builder does not reject it
        // (Finding D). The default is false; these adapters override it to true.
        assertTrue(store.supportsOwnership(),
                "a durable store implementing the ownership trio must return"
                        + " supportsOwnership() == true");
    }

    @Test
    void concurrentAcquireStormElectsExactlyOneWinnerWithStrictlyIncreasingEpochs() throws Exception {
        // Pin the acquire atomicity that is otherwise only reasoned: N threads
        // race tryAcquireOwnership at one instant → exactly one wins, epochs are
        // strictly increasing, never two owners. Pre-seed the ownership row so a
        // relational store's first contention serializes on SELECT ... FOR UPDATE
        // rather than racing an INSERT into a non-existent row (the in-memory
        // store is unaffected; release retains the row + epoch high-water).
        StreamOwnershipLease seed = store.tryAcquireOwnership(STREAM, "seed", NOW, LEASE)
                .orElseThrow();
        store.releaseOwnership(seed);

        long previousEpoch = seed.epoch();
        for (int round = 0; round < 3; round++) {
            StreamOwnershipLease winner = raceForOwnership(8, NOW.plusSeconds(round));
            assertTrue(winner.epoch() > previousEpoch,
                    "each concurrent takeover mints a strictly higher epoch: "
                            + winner.epoch() + " !> " + previousEpoch);
            previousEpoch = winner.epoch();
            store.releaseOwnership(winner); // free the row for the next race
        }
    }

    /**
     * Runs {@code threads} instances racing {@link TxStreamStateStore#tryAcquireOwnership}
     * at the same instant with distinct owner tokens; asserts EXACTLY ONE wins
     * (atomicity — never two concurrent owners) and returns the winning lease.
     */
    private StreamOwnershipLease raceForOwnership(int threads, Instant now) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CyclicBarrier startLine = new CyclicBarrier(threads);
            List<Future<Optional<StreamOwnershipLease>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String owner = "storm-owner-" + i;
                futures.add(pool.submit(() -> {
                    startLine.await();
                    return store.tryAcquireOwnership(STREAM, owner, now, LEASE);
                }));
            }
            List<StreamOwnershipLease> winners = new ArrayList<>();
            for (Future<Optional<StreamOwnershipLease>> future : futures) {
                future.get(30, TimeUnit.SECONDS).ifPresent(winners::add);
            }
            assertEquals(1, winners.size(),
                    "exactly one instance may win ownership in a concurrent acquire storm, got: "
                            + winners);
            return winners.get(0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void batchProjectionRoundTripsAndEvictIsANoOp() {
        TxStreamBatchResult batch = new TxStreamBatchResult(STREAM, "batch-1",
                TxStreamBatchStatus.PARTIALLY_COMPLETED,
                List.of("item-1", "item-2"), List.of("exec-1", "exec-2"), null);
        store.recordBatch(batch);

        TxStreamBatchResult stored = store.getBatch(STREAM, "batch-1").orElseThrow();
        assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED, stored.status());
        assertEquals(List.of("item-1", "item-2"), stored.itemIds());
        assertEquals(List.of("exec-1", "exec-2"), stored.executionIds());

        store.evictBatch("batch-1");
        assertTrue(store.getBatch(STREAM, "batch-1").isPresent());
        assertTrue(store.getBatch("other", "batch-1").isEmpty());
    }

    // ---- helpers ----

    private void projectFresh(String itemId, TxStreamItemStatus status, long sequence) {
        store.registerItem(record(itemId));
        store.projectItem(projection(itemId, status), sequence);
    }

    private TxStreamItemRecord record(String itemId) {
        return new TxStreamItemRecord(itemId, "key-" + itemId, "lane-a",
                "fp-" + itemId, NOW);
    }

    private TxStreamBinding binding(String executionId) {
        return new TxStreamBinding(executionId, "flow-1", "step-1", "lane-a");
    }

    private TxStreamItemResult projection(String itemId, TxStreamItemStatus status) {
        return projection(STREAM, itemId, status);
    }

    private TxStreamItemResult projection(String streamId, String itemId,
                                          TxStreamItemStatus status) {
        return TxStreamItemResult.builder(streamId, itemId, status)
                .executionId("exec-" + itemId).stepId("step-1").laneName("lane-a")
                .updatedAt(NOW).build();
    }

    private TxStreamPlannedRecord.Member member(String itemId, String key, String stepId) {
        return new TxStreamPlannedRecord.Member(itemId, key, stepId, "fp-" + itemId);
    }

    private TxStreamPlannedRecord plannedRecord(String executionId, String idempotencyKey,
                                                TxStreamPlannedRecord.Member... members) {
        return plannedRecord(STREAM, executionId, idempotencyKey, members);
    }

    private TxStreamPlannedRecord plannedRecord(String streamId, String executionId,
                                                String idempotencyKey,
                                                TxStreamPlannedRecord.Member... members) {
        return new TxStreamPlannedRecord(streamId, executionId, idempotencyKey, "lane-a",
                "addr:sender", "{\"apiVersion\":\"v1alpha1\"}", Map.of(), Map.of(), Map.of(),
                List.of(members));
    }

    private Set<String> keySet(TxStreamPlannedRecord record) {
        return record.members().stream()
                .map(TxStreamPlannedRecord.Member::idempotencyKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<String> itemIdSet(TxStreamPlannedRecord record) {
        return record.members().stream()
                .map(TxStreamPlannedRecord.Member::itemId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Map<String, String> itemKeyMap(TxStreamPlannedRecord record) {
        return record.members().stream().collect(java.util.stream.Collectors.toMap(
                TxStreamPlannedRecord.Member::itemId,
                TxStreamPlannedRecord.Member::idempotencyKey));
    }

    private String stepFor(TxStreamPlannedRecord record, String itemId) {
        return record.members().stream()
                .filter(member -> member.itemId().equals(itemId))
                .map(TxStreamPlannedRecord.Member::stepId)
                .findFirst().orElseThrow();
    }

    /**
     * Asserts that a store operation fails with a typed stream error code.
     *
     * @param code expected {@code TXSTREAM_*} code
     * @param operation store operation
     */
    protected void assertCode(String code, Runnable operation) {
        TxStreamException failure = assertThrows(TxStreamException.class, operation::run);
        assertEquals(code, failure.getCode());
    }
}
