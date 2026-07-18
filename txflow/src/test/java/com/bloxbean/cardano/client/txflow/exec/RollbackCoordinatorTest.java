package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollbackCoordinatorTest {
    @Test
    void liveConsumerDetectionUsesActualSpentInputs() {
        FlowStepResult orderingOnly = FlowStepResult.success("ordering", "hash-ordering", List.of());
        FlowStepResult actualConsumer = FlowStepResult.success("consumer", "hash-consumer", List.of(),
                List.of(new TransactionInput("hash-producer", 1)));

        RollbackCoordinator coordinator = new RollbackCoordinator();
        assertEquals(false, coordinator.hasActualConsumer("hash-producer", List.of(orderingOnly)));
        assertEquals(true, coordinator.hasActualConsumer(
                "hash-producer", List.of(orderingOnly, actualConsumer)));
    }

    @Test
    void liveClosureFollowsSpentInputsAndIgnoresOrderingOnlySteps() {
        FlowStepResult producer = result("producer", "hash-a", List.of());
        FlowStepResult consumer = result("consumer", "hash-b", List.of("hash-a"));
        FlowStepResult descendant = result("descendant", "hash-c", List.of("hash-b"));
        FlowStepResult orderingOnly = result("ordering", "hash-d", List.of("independent"));

        assertEquals(Set.of("producer", "consumer", "descendant"),
                new RollbackCoordinator().invalidatedLiveClosure(
                        "producer", List.of(producer, consumer, descendant, orderingOnly)));
    }

    @Test
    void actualInputsAndExplicitRefsInvalidateClosureButOrderingOnlyNeedsDoNot() {
        FlowAttemptSnapshot a = attempt("a", "hash-a", List.of());
        FlowAttemptSnapshot b = attempt("b", "hash-b", List.of("hash-a#0"));
        FlowAttemptSnapshot c = attempt("c", "hash-c", List.of());
        FlowAttemptSnapshot d = attempt("d", "hash-d", List.of("hash-b#1"));

        Set<String> invalidated = new RollbackCoordinator().invalidatedClosure(
                "a", "hash-a", List.of(a, b, c, d), Map.of("a", Set.of("c")));

        assertEquals(Set.of("a", "b", "c", "d"), invalidated);
        assertEquals(Set.of("a"), new RollbackCoordinator().invalidatedClosure(
                "a", "hash-a", List.of(a, c), Map.of()));
    }

    private FlowAttemptSnapshot attempt(String step, String hash, List<String> inputs) {
        return new FlowAttemptSnapshot(step, 1, AttemptState.CONFIRMED,
                new SignedPayload.ExternalCbor("test://" + step, "sha", hash),
                null, null, inputs, List.of(), Instant.EPOCH, null);
    }

    private FlowStepResult result(String step, String hash, List<String> inputHashes) {
        return FlowStepResult.success(step, hash, List.of(), inputHashes.stream()
                .map(input -> new TransactionInput(input, 0)).toList());
    }
}
