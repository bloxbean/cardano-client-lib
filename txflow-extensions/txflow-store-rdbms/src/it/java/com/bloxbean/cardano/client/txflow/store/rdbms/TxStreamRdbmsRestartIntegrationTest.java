package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.stream.BindingOutcome;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBinding;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemRecord;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;
import com.bloxbean.cardano.client.txflow.stream.TxStreamPlannedRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Genuine cross-instance restart test: persists a stream's planning metadata (and a paired durable
 * engine execution) into a file-backed H2 database, drops the store instances, reopens fresh
 * instances against the same committed file, and drives the exact store-level reads and writes the
 * ADR 0004 restart re-attach protocol performs.
 *
 * <p>This exercises the six must-replicate guarantees across a real instance drop: the projection
 * sequence survives so a re-attach fast-forward at {@code storedSeq + 1} dominates the CAS
 * (#1/#2), the planned record is keyed by execution id and carries per-member idempotency keys
 * (#3/#4), {@code listNonTerminalItemIds} shrinks once a row is driven terminal (#5), and nothing
 * is evicted (#6) — so a third restart no longer re-attaches the completed item.</p>
 */
class TxStreamRdbmsRestartIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private static final String STREAM = "payouts";
    private static final String ITEM = "pay-0042";
    private static final String EXECUTION = "txs-0042";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    void streamPlanningMetadataSurvivesAGenuineInstanceDropAndDrivesReattach() {
        String streamUrl = "jdbc:h2:file:"
                + temporaryDirectory.resolve("txstream").toAbsolutePath();
        String engineUrl = "jdbc:h2:file:"
                + temporaryDirectory.resolve("txengine").toAbsolutePath();

        // ---- Instance 1: plan an item the way the durable stream does, then drop the process ----
        try (RdbmsTxStreamStateStore streamStore = migrate(streamUrl);
             RdbmsFlowExecutionStore engineStore = engine(engineUrl)) {
            // Engine truth: the execution the stream dispatched (present at re-attach).
            engineStore.createOrGet("stream:" + STREAM, EXECUTION,
                    new FlowExecutionSnapshot(EXECUTION, "def", "req",
                            FlowExecutionState.RUNNING, 0, 0, 0, NOW, Map.of()));

            // Stream planning metadata: register -> bind (DISPATCHING) -> persist plan -> project
            // PLANNED at sequence 2 (leaving the DISPATCHING binding, as a crash before confirm).
            streamStore.registerItem(new TxStreamItemRecord(ITEM, "order-0042", "lane-a",
                    "fp-0042", NOW));
            streamStore.bind(ITEM, new TxStreamBinding(EXECUTION, "flow-0042", "step", "lane-a"));
            streamStore.persistPlanned(new TxStreamPlannedRecord(STREAM, EXECUTION, "order-0042",
                    "lane-a", "addr:sender", "{\"apiVersion\":\"v1alpha1\"}", Map.of("amount", 10L),
                    Map.of("signingKey", "vault://payouts/key"), Map.of("signingKey", "fp-sig"),
                    List.of(new TxStreamPlannedRecord.Member(ITEM, "order-0042", "step",
                            "fp-0042"))));
            streamStore.projectItem(TxStreamItemResult.builder(STREAM, ITEM,
                            TxStreamItemStatus.PLANNED)
                    .executionId(EXECUTION).stepId("step").laneName("lane-a").updatedAt(NOW).build(),
                    2);
        }

        // ---- Instance 2: fresh stores against the committed files — run the re-attach reads ----
        try (RdbmsTxStreamStateStore streamStore = validate(streamUrl);
             RdbmsFlowExecutionStore engineStore = engineValidate(engineUrl)) {
            // #5: the non-terminal scan sees the item that was mid-flight at the drop.
            assertEquals(List.of(ITEM), streamStore.listNonTerminalItemIds(STREAM));

            // #3/#4: exactly one planned record per execution id, members carry the per-item key.
            List<TxStreamPlannedRecord> planned = streamStore.listPlanned(STREAM);
            assertEquals(1, planned.size());
            TxStreamPlannedRecord record = planned.get(0);
            assertEquals(EXECUTION, record.executionId());
            assertEquals("{\"apiVersion\":\"v1alpha1\"}", record.portableFlow());
            assertEquals(Map.of("amount", 10L), record.bindings());
            assertEquals("vault://payouts/key", record.secureBindingReferences().get("signingKey"));
            assertEquals(1, record.members().size());
            assertEquals("order-0042", record.members().get(0).idempotencyKey());
            assertEquals("step", record.members().get(0).stepId());

            // The engine snapshot is present -> the "present" re-attach branch (re-project, do not
            // re-dispatch). Confirm the DISPATCHING binding, exactly as reattachPresentMember does.
            assertTrue(engineStore.get(EXECUTION).isPresent());
            streamStore.confirmBinding(ITEM, BindingOutcome.MATCHED);

            // #1/#2: the stored projection sequence survived; the fast-forward writes at
            // storedSeq + 1 and DOMINATES the CAS (this is BUG-1's exact repair path).
            long storedSequence = streamStore.lastProjectionSequence(STREAM, ITEM).orElseThrow();
            assertEquals(2L, storedSequence);
            assertEquals(TxStreamItemStatus.PLANNED,
                    streamStore.getItem(STREAM, ITEM).orElseThrow().getStatus());
            boolean applied = streamStore.projectItem(TxStreamItemResult.builder(STREAM, ITEM,
                            TxStreamItemStatus.CONFIRMED)
                    .executionId(EXECUTION).stepId("step").laneName("lane-a")
                    .transactionHash("abcd").updatedAt(NOW).build(), storedSequence + 1);
            assertTrue(applied, "fast-forward at storedSeq+1 must win the CAS");

            // #5: the item is now terminal, so the non-terminal scan shrinks.
            assertTrue(streamStore.listNonTerminalItemIds(STREAM).isEmpty());
        }

        // ---- Instance 3: another restart must NOT re-attach the now-terminal item (#5/#6) ----
        try (RdbmsTxStreamStateStore streamStore = validate(streamUrl)) {
            assertTrue(streamStore.listNonTerminalItemIds(STREAM).isEmpty(),
                    "a completed item must not be re-attached on every restart");
            // #6: durable retention — the planned record and confirmed projection are retained.
            assertEquals(1, streamStore.listPlanned(STREAM).size());
            TxStreamItemResult confirmed = streamStore.getItem(STREAM, ITEM).orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, confirmed.getStatus());
            assertEquals("abcd", confirmed.getTransactionHash());
            assertEquals(3L, streamStore.lastProjectionSequence(STREAM, ITEM).orElseThrow());
            streamStore.evictItem(ITEM);
            assertFalse(streamStore.getItem(STREAM, ITEM).isEmpty(),
                    "durable eviction is a no-op");
        }
    }

    private RdbmsTxStreamStateStore migrate(String url) {
        return RdbmsTxStreamStateStore.builder().jdbcUrl(url)
                .schemaManagement(SchemaManagement.MIGRATE).build();
    }

    private RdbmsTxStreamStateStore validate(String url) {
        return RdbmsTxStreamStateStore.builder().jdbcUrl(url)
                .schemaManagement(SchemaManagement.VALIDATE).build();
    }

    private RdbmsFlowExecutionStore engine(String url) {
        return RdbmsFlowExecutionStore.builder().jdbcUrl(url)
                .schemaManagement(SchemaManagement.MIGRATE).build();
    }

    private RdbmsFlowExecutionStore engineValidate(String url) {
        return RdbmsFlowExecutionStore.builder().jdbcUrl(url)
                .schemaManagement(SchemaManagement.VALIDATE).build();
    }
}
