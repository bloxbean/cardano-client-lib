package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.InclusionRecord;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Run-scoped persistence adapter for signed transaction attempts.
 *
 * <p>{@link #onPrepared(FlowStep, Transaction)} serializes the final signed
 * transaction, records its hash and inputs, and durably appends the
 * {@code SIGNED} attempt before submission may begin. Subsequent callbacks
 * advance that same attempt through submitting, inclusion, confirmation, or
 * rollback states. A persistence failure is propagated so the caller cannot
 * continue with unjournaled network I/O.</p>
 *
 * <p>Callbacks are synchronized because listener and confirmation activity may
 * arrive from different caller-managed tasks. This class schedules no work and
 * owns no executor.</p>
 */
final class DurableExecutionPersistence implements PersistencePort {
    static final String ATTEMPTS_KEY = "attempts";

    private final ExecutionJournalSession journal;
    private final Map<String, Integer> attemptNumbers = new LinkedHashMap<>();
    private final Map<String, FlowAttemptSnapshot> attempts = new LinkedHashMap<>();
    private final Map<String, String> activeAttemptKeys = new LinkedHashMap<>();
    private final Map<String, Set<String>> explicitConsumers;

    DurableExecutionPersistence(ExecutionJournalSession journal) {
        this(journal, Map.of());
    }

    DurableExecutionPersistence(ExecutionJournalSession journal,
                               Map<String, Set<String>> explicitConsumers) {
        this.journal = java.util.Objects.requireNonNull(journal, "journal");
        this.explicitConsumers = Map.copyOf(explicitConsumers);
    }

    @Override
    public synchronized void onPrepared(FlowStep step, Transaction transaction) {
        try {
            byte[] cbor = transaction.serialize();
            String transactionHash = TransactionUtil.getTxHash(transaction);
            int attemptNumber = attemptNumbers.merge(step.getId(), 1, Integer::sum);
            Long validFrom = transaction.getBody().getValidityStartInterval() == 0
                    ? null : transaction.getBody().getValidityStartInterval();
            Long validTo = transaction.getBody().getTtl() == 0
                    ? null : transaction.getBody().getTtl();
            List<String> spentInputs = transaction.getBody().getInputs().stream()
                    .map(this::inputIdentity).toList();
            FlowAttemptSnapshot attempt = new FlowAttemptSnapshot(step.getId(), attemptNumber,
                    AttemptState.SIGNED,
                    new SignedPayload.InlineCbor(cbor, SignedPayloadVerifier.sha256(cbor), transactionHash),
                    validFrom, validTo, spentInputs, List.of(), journal.now(), null);
            String attemptKey = step.getId() + "#" + attemptNumber;
            attempts.put(attemptKey, attempt);
            activeAttemptKeys.put(step.getId(), attemptKey);
            emitAndPersist(FlowEventType.TRANSACTION_PREPARED, step.getId(), transactionHash,
                    Map.of("attempt", attemptNumber));
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new com.bloxbean.cardano.client.txflow.store.FlowStoreException(
                    "TXFLOW_PREPARED_PERSISTENCE_FAILED", failure.getMessage());
        }
    }

    @Override
    public synchronized void onSubmitting(FlowStep step, Transaction transaction) {
        FlowAttemptSnapshot current = requireAttempt(step);
        putAttempt(step, withState(current, AttemptState.SUBMITTING, current.inclusions(), null));
        emitAndPersist(FlowEventType.TRANSACTION_SUBMITTING, step.getId(),
                current.signedPayload().transactionHash(),
                Map.of("attempt", current.attemptNumber()));
    }

    @Override
    public synchronized void onSubmitted(FlowStep step, String transactionHash) {
        transition(step, AttemptState.SUBMITTED, FlowEventType.TRANSACTION_SUBMITTED,
                transactionHash, Map.of());
    }

    @Override
    public synchronized void onInBlock(FlowStep step, String transactionHash, long blockHeight) {
        FlowAttemptSnapshot current = requireAttempt(step);
        List<InclusionRecord> inclusions = new ArrayList<>(current.inclusions());
        inclusions.add(new InclusionRecord(blockHeight, null, 0, journal.now(), false));
        putAttempt(step, withState(current, AttemptState.IN_BLOCK, inclusions, null));
        emitAndPersist(FlowEventType.TRANSACTION_IN_BLOCK, step.getId(), transactionHash,
                Map.of("block_height", blockHeight));
    }

    @Override
    public synchronized void onConfirmationDepth(FlowStep step, String transactionHash, int depth) {
        emitAndPersist(FlowEventType.CONFIRMATION_DEPTH_CHANGED, step.getId(), transactionHash,
                Map.of("depth", depth));
    }

    @Override
    public synchronized void onConfirmed(FlowStep step, String transactionHash) {
        transition(step, AttemptState.CONFIRMED, FlowEventType.TRANSACTION_CONFIRMED,
                transactionHash, Map.of());
    }

    @Override
    public synchronized void onRolledBack(FlowStep step, String transactionHash,
                                          long previousBlockHeight) {
        FlowAttemptSnapshot current = requireAttempt(step);
        if (current.state() == AttemptState.ROLLED_BACK) return;
        List<InclusionRecord> inclusions = current.inclusions().stream()
                .map(inclusion -> inclusion.blockHeight() == previousBlockHeight
                        ? new InclusionRecord(inclusion.blockHeight(), inclusion.blockHash(),
                        inclusion.slot(), inclusion.observedAt(), true) : inclusion)
                .toList();
        putAttempt(step, withState(current, AttemptState.ROLLED_BACK,
                inclusions, "TXFLOW_ROLLBACK"));
        emit(FlowEventType.TRANSACTION_ROLLED_BACK, step.getId(), transactionHash,
                Map.of("previous_block_height", previousBlockHeight));
        Set<String> invalidated = new RollbackCoordinator().invalidatedClosure(
                step.getId(), List.copyOf(attempts.values()), explicitConsumers);
        for (String invalidatedStep : invalidated) {
            if (invalidatedStep.equals(step.getId())) continue;
            String attemptKey = activeAttemptKeys.get(invalidatedStep);
            FlowAttemptSnapshot dependent = attemptKey != null ? attempts.get(attemptKey) : null;
            if (dependent == null || dependent.state() == AttemptState.RECOVERY_REQUIRED) continue;
            attempts.put(attemptKey, withState(dependent, AttemptState.RECOVERY_REQUIRED,
                    dependent.inclusions(), "TXFLOW_ROLLBACK_INVALIDATED"));
            emit(FlowEventType.RECOVERY_REQUIRED, invalidatedStep,
                    dependent.signedPayload() != null
                            ? dependent.signedPayload().transactionHash() : null,
                    Map.of("rolled_back_producer", step.getId()));
        }
        persist();
    }

    private void transition(FlowStep step, AttemptState state, FlowEventType eventType,
                            String transactionHash, Map<String, Object> details) {
        FlowAttemptSnapshot current = requireAttempt(step);
        putAttempt(step, withState(current, state, current.inclusions(), null));
        emitAndPersist(eventType, step.getId(), transactionHash, details);
    }

    private FlowAttemptSnapshot withState(FlowAttemptSnapshot current, AttemptState state,
                                          List<InclusionRecord> inclusions, String errorCode) {
        return new FlowAttemptSnapshot(current.stepId(), current.attemptNumber(), state,
                current.signedPayload(), current.validFromSlot(), current.validToSlot(),
                current.spentInputs(), inclusions, journal.now(), errorCode);
    }

    private FlowAttemptSnapshot requireAttempt(FlowStep step) {
        String key = activeAttemptKeys.get(step.getId());
        FlowAttemptSnapshot attempt = key != null ? attempts.get(key) : null;
        if (attempt == null) {
            throw new com.bloxbean.cardano.client.txflow.store.FlowStoreException(
                    "TXFLOW_PREPARED_ATTEMPT_MISSING",
                    "No prepared attempt exists for step " + step.getId());
        }
        return attempt;
    }

    private void putAttempt(FlowStep step, FlowAttemptSnapshot attempt) {
        attempts.put(activeAttemptKeys.get(step.getId()), attempt);
    }

    private void persist() {
        journal.persist(FlowExecutionState.RUNNING,
                data -> data.put(ATTEMPTS_KEY, Map.copyOf(attempts)));
    }

    private void emit(FlowEventType type, String stepId, String transactionHash,
                      Map<String, Object> details) {
        journal.record(type, stepId, transactionHash, details);
    }

    private void emitAndPersist(FlowEventType type, String stepId, String transactionHash,
                                Map<String, Object> details) {
        journal.recordAndPersist(type, stepId, transactionHash, details,
                FlowExecutionState.RUNNING,
                data -> data.put(ATTEMPTS_KEY, Map.copyOf(attempts)));
    }

    private String inputIdentity(TransactionInput input) {
        return input.getTransactionId() + "#" + input.getIndex();
    }

}
