package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.absent;
import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.included;
import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.failure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConfirmationTrackerDeterministicTest {

    @Test
    void timesOutUsingVirtualTime() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(absent(100), absent(100), absent(101));
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(2)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(3))
                .build();

        ConfirmationResult result = new ConfirmationTracker(backend, config, scheduler)
                .waitForConfirmation("tx-1", ConfirmationStatus.CONFIRMED);

        assertInstanceOf(ConfirmationTimeoutException.class, result.getError());
        assertEquals(3, scheduler.getDelays().size());
        assertEquals(Duration.ofSeconds(3), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void reportsExactlyRollbackForIncludedThenAbsentScript() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102));
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(2)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(10))
                .build();

        ConfirmationResult result = new ConfirmationTracker(backend, config, scheduler)
                .waitForConfirmation("tx-1", ConfirmationStatus.CONFIRMED);

        assertEquals(ConfirmationStatus.ROLLED_BACK, result.getStatus());
        assertEquals(2, scheduler.getDelays().size());
    }

    @Test
    void honorsConfiguredAuthoritativeAbsenceThreshold() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102),
                        absent(103));
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(5)
                .requiredAuthoritativeAbsences(3)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(10))
                .build();

        ConfirmationResult result = new ConfirmationTracker(backend, config, scheduler)
                .waitForConfirmation("tx-threshold", ConfirmationStatus.CONFIRMED);

        assertEquals(ConfirmationStatus.ROLLED_BACK, result.getStatus());
        assertEquals(3, scheduler.getDelays().size());
    }

    @Test
    void reachesConfirmationAtScriptedDepth() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), included(102, 100, "block-a"));
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(2)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(10))
                .build();

        ConfirmationResult result = new ConfirmationTracker(backend, config, scheduler)
                .waitForConfirmation("tx-1", ConfirmationStatus.CONFIRMED);

        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
        assertEquals(2, result.getConfirmationDepth());
        assertEquals(1, scheduler.getDelays().size());
    }

    @Test
    void laggingAbsenceBehindInclusionNeverCountsAsRollback() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(99), absent(99),
                        included(102, 100, "block-a"));
        ConfirmationResult result = new ConfirmationTracker(backend, config(2, 10), scheduler)
                .waitForConfirmation("tx-lag", ConfirmationStatus.CONFIRMED);
        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void backendErrorsAfterInclusionRemainUncertainUntilARealObservation() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), failure("offline"),
                        included(102, 100, "block-a"));
        ConfirmationResult result = new ConfirmationTracker(backend, config(2, 10), scheduler)
                .waitForConfirmation("tx-error", ConfirmationStatus.CONFIRMED);
        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void undeclaredAbsenceAuthorityEndsAsRecoveryUncertainNotRollback() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend(false)
                .then(included(100, 100, "block-a"), absent(101), absent(102));
        ConfirmationResult result = new ConfirmationTracker(backend, config(5, 3), scheduler)
                .waitForConfirmation("tx-unknown", ConfirmationStatus.CONFIRMED);
        assertInstanceOf(ReconciliationUncertainException.class, result.getError());
        assertEquals(ConfirmationStatus.SUBMITTED, result.getStatus());
    }

    @Test
    void sameHashReincludedInNewBlockContinuesSameTrackingAttempt() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), included(101, 101, "block-b"),
                        included(103, 101, "block-b"));
        ConfirmationResult result = new ConfirmationTracker(backend, config(2, 10), scheduler)
                .waitForConfirmation("same-hash", ConfirmationStatus.CONFIRMED);
        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
        assertEquals("block-b", result.getBlockHash());
    }

    @Test
    void callerDeadlineContinuesSameAttemptThroughRollbackAndReinclusion() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102),
                        absent(103), included(104, 103, "block-b"));
        ConfirmationTracker tracker = new ConfirmationTracker(backend, config(1, 20), scheduler);

        ConfirmationResult rollback = tracker.waitForConfirmation(
                "same-attempt", ConfirmationStatus.CONFIRMED);
        Instant deadline = scheduler.now().plus(Duration.ofSeconds(3));
        ConfirmationResult reincluded = tracker.waitForConfirmation(
                "same-attempt", ConfirmationStatus.CONFIRMED, null, () -> false,
                deadline, true);

        assertEquals(ConfirmationStatus.ROLLED_BACK, rollback.getStatus());
        assertEquals(ConfirmationStatus.CONFIRMED, reincluded.getStatus());
        assertEquals("block-b", reincluded.getBlockHash());
        assertEquals(1, tracker.getTrackedCount());
        assertEquals(Duration.ofSeconds(3), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void callerDeadlineBoundsPostRollbackWaitWithoutRetryRepolls() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend backend = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102),
                        absent(103), absent(104));
        ConfirmationTracker tracker = new ConfirmationTracker(backend, config(1, 20), scheduler);

        ConfirmationResult rollback = tracker.waitForConfirmation(
                "bounded-wait", ConfirmationStatus.CONFIRMED);
        Instant waitStarted = scheduler.now();
        ConfirmationResult exhausted = tracker.waitForConfirmation(
                "bounded-wait", ConfirmationStatus.CONFIRMED, null, () -> false,
                waitStarted.plus(Duration.ofSeconds(2)), true);

        assertEquals(ConfirmationStatus.ROLLED_BACK, rollback.getStatus());
        assertEquals(ConfirmationStatus.ROLLED_BACK, exhausted.getStatus());
        assertEquals(Duration.ofSeconds(2), Duration.between(waitStarted, scheduler.now()));
        assertEquals(1, tracker.getTrackedCount());
    }

    private ConfirmationConfig config(int confirmations, int timeoutSeconds) {
        return ConfirmationConfig.builder().minConfirmations(confirmations)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(timeoutSeconds)).build();
    }
}
