package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the transitive set of attempts invalidated by a producer rollback.
 *
 * <p>Live results are linked by actual spent transaction inputs. Durable
 * attempts additionally use compiler-provided explicit output-reference edges,
 * which preserve dependencies even when an attempt has not yet produced a
 * result. The coordinator is a stateless calculation helper: it neither changes
 * attempt state nor decides whether policy permits a rebuild.</p>
 */
final class RollbackCoordinator {
    boolean hasActualConsumer(String producerTransactionHash,
                              Iterable<FlowStepResult> results) {
        if (producerTransactionHash == null || results == null) return false;
        for (FlowStepResult result : results) {
            if (result != null && result.getSpentInputs().stream().anyMatch(input ->
                    producerTransactionHash.equals(input.getTransactionId()))) {
                return true;
            }
        }
        return false;
    }

    Set<String> invalidatedLiveClosure(String rolledBackStep,
                                       List<FlowStepResult> results) {
        Set<String> invalidatedSteps = new LinkedHashSet<>();
        Set<String> invalidatedHashes = new LinkedHashSet<>();
        invalidatedSteps.add(rolledBackStep);
        results.stream().filter(result -> rolledBackStep.equals(result.getStepId()))
                .map(FlowStepResult::getTransactionHash)
                .filter(java.util.Objects::nonNull)
                .forEach(invalidatedHashes::add);

        boolean changed;
        do {
            changed = false;
            for (FlowStepResult result : results) {
                if (invalidatedSteps.contains(result.getStepId())) continue;
                boolean spendsInvalidated = result.getSpentInputs().stream().anyMatch(input ->
                        invalidatedHashes.contains(input.getTransactionId()));
                if (spendsInvalidated) changed |= invalidatedSteps.add(result.getStepId());
            }
            for (FlowStepResult result : results) {
                if (invalidatedSteps.contains(result.getStepId())
                        && result.getTransactionHash() != null) {
                    changed |= invalidatedHashes.add(result.getTransactionHash());
                }
            }
        } while (changed);
        return Set.copyOf(invalidatedSteps);
    }

    Set<String> invalidatedClosure(String rolledBackStep,
                                   List<FlowAttemptSnapshot> attempts,
                                   Map<String, Set<String>> explicitConsumers) {
        Set<String> invalidatedSteps = new LinkedHashSet<>();
        Set<String> invalidatedHashes = new LinkedHashSet<>();
        invalidatedSteps.add(rolledBackStep);
        attempts.stream().filter(attempt -> attempt.stepId().equals(rolledBackStep))
                .map(FlowAttemptSnapshot::signedPayload).filter(java.util.Objects::nonNull)
                .forEach(payload -> invalidatedHashes.add(payload.transactionHash()));

        boolean changed;
        do {
            changed = false;
            for (String producer : List.copyOf(invalidatedSteps)) {
                for (String consumer : explicitConsumers.getOrDefault(producer, Set.of())) {
                    changed |= invalidatedSteps.add(consumer);
                }
            }
            for (FlowAttemptSnapshot attempt : attempts) {
                if (invalidatedSteps.contains(attempt.stepId())) continue;
                boolean spendsInvalidated = attempt.spentInputs().stream().anyMatch(input ->
                        invalidatedHashes.stream().anyMatch(hash -> input.startsWith(hash + "#")));
                if (spendsInvalidated) changed |= invalidatedSteps.add(attempt.stepId());
            }
            for (FlowAttemptSnapshot attempt : attempts) {
                if (invalidatedSteps.contains(attempt.stepId()) && attempt.signedPayload() != null) {
                    changed |= invalidatedHashes.add(attempt.signedPayload().transactionHash());
                }
            }
        } while (changed);
        return Set.copyOf(invalidatedSteps);
    }
}
