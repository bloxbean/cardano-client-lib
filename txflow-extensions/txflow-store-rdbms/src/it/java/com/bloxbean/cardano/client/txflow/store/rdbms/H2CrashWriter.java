package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.InclusionRecord;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import com.bloxbean.cardano.client.txflow.store.ResourceLease;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Child-process writer used to verify recovery after an external forcible JVM termination. */
final class H2CrashWriter {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    private H2CrashWriter() {
    }

    public static void main(String[] arguments) throws Exception {
        String jdbcUrl = arguments[0];
        AttemptState attemptState = AttemptState.valueOf(arguments[1]);
        Path readyMarker = Path.of(arguments[2]);
        String executionId = "crash-" + attemptState.name().toLowerCase();
        RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
                .jdbcUrl(jdbcUrl)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC))
                .build();
        store.createOrGet("crash-test", attemptState.name(), new FlowExecutionSnapshot(
                executionId, "definition", "request", FlowExecutionState.CREATED,
                0, 0, 0, NOW, Map.of()));
        ExecutionLease executionLease = store.acquireExecutionLease(
                executionId, "crash-writer", NOW, Duration.ofSeconds(30));
        ResourceLease resourceLease = store.acquireResourceLease(
                "wallet", executionId, "crash-writer", NOW, Duration.ofSeconds(30));
        FlowAttemptSnapshot attempt = attempt(attemptState);
        FlowExecutionState executionState = attemptState == AttemptState.CONFIRMED
                ? FlowExecutionState.COMPLETED : FlowExecutionState.RUNNING;
        FlowEvent event = new FlowEvent(1, executionId, eventType(attemptState),
                NOW.plusSeconds(1), "step", "transaction-hash",
                Map.of("attempt_state", attemptState.name()));
        store.append(executionId, 0,
                new MutationFence(executionLease, List.of(resourceLease)), List.of(event),
                current -> current.withState(executionState, NOW.plusSeconds(1), Map.of(
                        "attempts", Map.of("step#1", attempt),
                        "execution_lease_epoch", executionLease.epoch(),
                        "resource_lease_epoch", resourceLease.epoch())));

        Files.writeString(readyMarker, "committed");
        // The parent destroys this JVM forcibly. Deliberately do not close the store or its H2
        // anchor connection, so recovery depends only on committed database transactions.
        Thread.sleep(Duration.ofMinutes(5).toMillis());
    }

    private static FlowAttemptSnapshot attempt(AttemptState state) {
        SignedPayload payload = new SignedPayload.InlineCbor(
                new byte[]{1, 2, 3, 4}, "payload-sha256", "transaction-hash");
        List<InclusionRecord> inclusions = state == AttemptState.IN_BLOCK
                || state == AttemptState.CONFIRMED
                ? List.of(new InclusionRecord(42, "block-hash", 100, NOW, false))
                : List.of();
        return new FlowAttemptSnapshot("step", 1, state, payload, 10L, 200L,
                List.of("input#0"), inclusions, NOW, null);
    }

    private static FlowEventType eventType(AttemptState state) {
        return switch (state) {
            case SIGNED -> FlowEventType.TRANSACTION_PREPARED;
            case SUBMITTING -> FlowEventType.TRANSACTION_SUBMITTING;
            case SUBMITTED -> FlowEventType.TRANSACTION_SUBMITTED;
            case IN_BLOCK -> FlowEventType.TRANSACTION_IN_BLOCK;
            case CONFIRMED -> FlowEventType.TRANSACTION_CONFIRMED;
            default -> throw new IllegalArgumentException("Unsupported crash boundary: " + state);
        };
    }
}
