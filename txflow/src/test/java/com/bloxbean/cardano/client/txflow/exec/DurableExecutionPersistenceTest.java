package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DurableExecutionPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Test
    void signedBytesAndHashAreFencedAndPersistedBeforeSubmittingState() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(clock);
        store.createOrGet("test", "execution", new FlowExecutionSnapshot(
                "execution", "definition", "request", FlowExecutionState.RUNNING,
                0, 0, 0, NOW, Map.of()));
        DurableLeaseGuard leases = new DurableLeaseGuard(
                store, clock, Duration.ofMinutes(1), Runnable::run);
        leases.acquireExecution("execution", "owner");
        ExecutionJournalSession journal = new ExecutionJournalSession(store, "execution", clock);
        journal.attach(leases);
        DurableExecutionPersistence persistence = new DurableExecutionPersistence(journal);
        FlowStep step = FlowStep.builder("pay").withTxContext(builder -> null).build();
        Transaction transaction = transaction();

        persistence.onPrepared(step, transaction);
        FlowAttemptSnapshot prepared = attempt(store);
        assertEquals(AttemptState.SIGNED, prepared.state());
        assertNotNull(prepared.signedPayload());
        assertArrayEquals(transaction.serialize(),
                ((com.bloxbean.cardano.client.txflow.store.SignedPayload.InlineCbor)
                        prepared.signedPayload()).cbor());

        persistence.onSubmitting(step, transaction);
        assertEquals(AttemptState.SUBMITTING, attempt(store).state());
        assertEquals(List.of(FlowEventType.TRANSACTION_PREPARED,
                        FlowEventType.TRANSACTION_SUBMITTING),
                store.readEvents("execution", 0, 10).events().stream()
                        .map(FlowEvent::type).toList());
        leases.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollbackMarksOnlyTheActualConsumerClosureForRecovery() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(clock);
        store.createOrGet("test", "closure", new FlowExecutionSnapshot(
                "closure", "definition", "request", FlowExecutionState.RUNNING,
                0, 0, 0, NOW, Map.of()));
        DurableLeaseGuard leases = new DurableLeaseGuard(
                store, clock, Duration.ofMinutes(1), Runnable::run);
        leases.acquireExecution("closure", "owner");
        ExecutionJournalSession journal = new ExecutionJournalSession(store, "closure", clock);
        journal.attach(leases);
        DurableExecutionPersistence persistence = new DurableExecutionPersistence(journal);
        FlowStep producer = FlowStep.builder("producer").withTxContext(builder -> null).build();
        FlowStep consumer = FlowStep.builder("consumer").withTxContext(builder -> null).build();
        FlowStep independent = FlowStep.builder("independent").withTxContext(builder -> null).build();
        Transaction producerTx = transaction();
        String producerHash = TransactionUtil.getTxHash(producerTx);

        persistence.onPrepared(producer, producerTx);
        persistence.onPrepared(consumer, transaction(producerHash));
        persistence.onPrepared(independent, transaction("11".repeat(32)));
        persistence.onRolledBack(producer, producerHash, 10);

        Map<String, FlowAttemptSnapshot> attempts = (Map<String, FlowAttemptSnapshot>) store.get("closure")
                .orElseThrow().data().get(DurableExecutionPersistence.ATTEMPTS_KEY);
        assertEquals(AttemptState.ROLLED_BACK, attempts.get("producer#1").state());
        assertEquals(AttemptState.RECOVERY_REQUIRED, attempts.get("consumer#1").state());
        assertEquals(AttemptState.SIGNED, attempts.get("independent#1").state());
        assertEquals(List.of(FlowEventType.TRANSACTION_PREPARED,
                        FlowEventType.TRANSACTION_PREPARED, FlowEventType.TRANSACTION_PREPARED,
                        FlowEventType.TRANSACTION_ROLLED_BACK, FlowEventType.RECOVERY_REQUIRED),
                store.readEvents("closure", 0, 10).events().stream()
                        .map(FlowEvent::type).toList());
        leases.close();
    }

    @Test
    void everySubmissionAndConfirmationBoundaryIsJournaledInOrder() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(clock);
        store.createOrGet("test", "boundaries", new FlowExecutionSnapshot(
                "boundaries", "definition", "request", FlowExecutionState.RUNNING,
                0, 0, 0, NOW, Map.of()));
        DurableLeaseGuard leases = new DurableLeaseGuard(
                store, clock, Duration.ofMinutes(1), Runnable::run);
        leases.acquireExecution("boundaries", "owner");
        ExecutionJournalSession journal = new ExecutionJournalSession(store, "boundaries", clock);
        journal.attach(leases);
        DurableExecutionPersistence persistence = new DurableExecutionPersistence(journal);
        FlowStep step = FlowStep.builder("pay").withTxContext(builder -> null).build();
        Transaction transaction = transaction();
        String hash = TransactionUtil.getTxHash(transaction);

        persistence.onPrepared(step, transaction);
        persistence.onSubmitting(step, transaction);
        persistence.onSubmitted(step, hash);
        persistence.onInBlock(step, hash, 42);
        persistence.onConfirmationDepth(step, hash, 2);
        persistence.onConfirmed(step, hash);

        assertEquals(AttemptState.CONFIRMED, attempt(store, "boundaries").state());
        assertEquals(List.of(FlowEventType.TRANSACTION_PREPARED,
                        FlowEventType.TRANSACTION_SUBMITTING, FlowEventType.TRANSACTION_SUBMITTED,
                        FlowEventType.TRANSACTION_IN_BLOCK, FlowEventType.CONFIRMATION_DEPTH_CHANGED,
                        FlowEventType.TRANSACTION_CONFIRMED),
                store.readEvents("boundaries", 0, 20).events().stream()
                        .map(FlowEvent::type).toList());
        leases.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void delayedRollbackUpdatesMatchingHashInsteadOfNewerStepAttempt() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(clock);
        store.createOrGet("test", "attempt-hash", new FlowExecutionSnapshot(
                "attempt-hash", "definition", "request", FlowExecutionState.RUNNING,
                0, 0, 0, NOW, Map.of()));
        DurableLeaseGuard leases = new DurableLeaseGuard(
                store, clock, Duration.ofMinutes(1), Runnable::run);
        leases.acquireExecution("attempt-hash", "owner");
        ExecutionJournalSession journal = new ExecutionJournalSession(
                store, "attempt-hash", clock);
        journal.attach(leases);
        DurableExecutionPersistence persistence = new DurableExecutionPersistence(journal);
        FlowStep step = FlowStep.builder("pay").withTxContext(builder -> null).build();
        Transaction first = transaction("00".repeat(32));
        Transaction newer = transaction("11".repeat(32));
        String firstHash = TransactionUtil.getTxHash(first);

        persistence.onPrepared(step, first);
        persistence.onPrepared(step, newer);
        persistence.onRolledBack(step, firstHash, 42);

        Map<String, FlowAttemptSnapshot> attempts =
                (Map<String, FlowAttemptSnapshot>) store.get("attempt-hash")
                        .orElseThrow().data().get(DurableExecutionPersistence.ATTEMPTS_KEY);
        assertEquals(AttemptState.ROLLED_BACK, attempts.get("pay#1").state());
        assertEquals(AttemptState.SIGNED, attempts.get("pay#2").state());
        assertEquals(firstHash, store.readEvents("attempt-hash", 0, 10).events().stream()
                .filter(event -> event.type() == FlowEventType.TRANSACTION_ROLLED_BACK)
                .findFirst().orElseThrow().transactionHash());
        leases.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void delayedRollbackInvalidatesOnlyDependentsOfTheMatchingProducerAttempt() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(clock);
        store.createOrGet("test", "attempt-closure", new FlowExecutionSnapshot(
                "attempt-closure", "definition", "request", FlowExecutionState.RUNNING,
                0, 0, 0, NOW, Map.of()));
        DurableLeaseGuard leases = new DurableLeaseGuard(
                store, clock, Duration.ofMinutes(1), Runnable::run);
        leases.acquireExecution("attempt-closure", "owner");
        ExecutionJournalSession journal = new ExecutionJournalSession(
                store, "attempt-closure", clock);
        journal.attach(leases);
        DurableExecutionPersistence persistence = new DurableExecutionPersistence(
                journal, Map.of("producer", Set.of("consumer")));
        FlowStep producer = FlowStep.builder("producer").withTxContext(builder -> null).build();
        FlowStep consumer = FlowStep.builder("consumer").withTxContext(builder -> null).build();
        Transaction firstProducer = transaction("00".repeat(32));
        String firstHash = TransactionUtil.getTxHash(firstProducer);
        Transaction secondProducer = transaction("11".repeat(32));
        String secondHash = TransactionUtil.getTxHash(secondProducer);

        persistence.onPrepared(producer, firstProducer);
        persistence.onPrepared(consumer, transaction(firstHash));
        persistence.onPrepared(producer, secondProducer);
        persistence.onPrepared(consumer, transaction(secondHash));
        persistence.onRolledBack(producer, firstHash, 42);

        Map<String, FlowAttemptSnapshot> attempts =
                (Map<String, FlowAttemptSnapshot>) store.get("attempt-closure")
                        .orElseThrow().data().get(DurableExecutionPersistence.ATTEMPTS_KEY);
        assertEquals(AttemptState.ROLLED_BACK, attempts.get("producer#1").state());
        assertEquals(AttemptState.SIGNED, attempts.get("producer#2").state());
        assertEquals(AttemptState.RECOVERY_REQUIRED, attempts.get("consumer#1").state());
        assertEquals(AttemptState.SIGNED, attempts.get("consumer#2").state());
        leases.close();
    }

    @SuppressWarnings("unchecked")
    private FlowAttemptSnapshot attempt(InMemoryFlowExecutionStore store) {
        return attempt(store, "execution");
    }

    @SuppressWarnings("unchecked")
    private FlowAttemptSnapshot attempt(InMemoryFlowExecutionStore store, String executionId) {
        Map<String, FlowAttemptSnapshot> attempts = (Map<String, FlowAttemptSnapshot>) store.get(executionId)
                .orElseThrow().data().get(DurableExecutionPersistence.ATTEMPTS_KEY);
        return attempts.get("pay#1");
    }

    private Transaction transaction() {
        return transaction("00".repeat(32));
    }

    private Transaction transaction(String inputTransactionId) {
        TransactionBody body = TransactionBody.builder()
                .inputs(List.of(new TransactionInput(inputTransactionId, 0)))
                .fee(BigInteger.ZERO)
                .ttl(1_000)
                .validityStartInterval(900)
                .build();
        return Transaction.builder()
                .body(body)
                .witnessSet(TransactionWitnessSet.builder().build())
                .isValid(true)
                .build();
    }
}
