package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowFormat;
import com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion;
import com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.stream.EmitResult;
import com.bloxbean.cardano.client.txflow.stream.TxFlowStream;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBinding;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemRecord;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;
import com.bloxbean.cardano.client.txflow.stream.TxStreamPlannedRecord;
import com.bloxbean.cardano.client.txflow.stream.WindowPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RdbmsTxStreamRedeliveryTest {
    @TempDir
    Path directory;

    @Test
    void terminalRedeliveryAttachesAcrossLiveEvictionAndDatabaseReopen() {
        TransactionProcessor processor = mock(TransactionProcessor.class);
        for (int pass = 0; pass < 2; pass++) {
            try (RdbmsFlowExecutionStore executions = RdbmsFlowExecutionStore.builder()
                    .jdbcUrl(url("engine")).build();
                 RdbmsTxStreamStateStore items = RdbmsTxStreamStateStore.builder()
                         .jdbcUrl(url("items")).build();
                 TxFlowStream stream = TxFlowStream.builder("payouts", engine(executions, processor))
                         .stateStore(items).window(WindowPolicy.count(100))
                         .maxRetainedSettledItems(1).open()) {
                if (pass == 0) {
                    stream.submit("one", plan());
                    assertTrue(stream.cancel("one", "cancel before dispatch"));
                    stream.submit("two", plan());
                    assertTrue(stream.cancel("two", "cancel before dispatch"));
                }
                EmitResult duplicate = stream.trySubmit("one", plan());
                assertEquals(EmitResult.Status.DUPLICATE_ATTACHED, duplicate.getStatus());
                assertEquals(TxStreamItemStatus.CANCELLED,
                        duplicate.getReceipt().awaitSettled(Duration.ofSeconds(1)).getStatus());
                assertEquals(pass == 0 ? 2 : 0, stream.getStats().acceptedItemCount());
            }
        }
        verifyNoInteractions(processor);
    }

    @Test
    void storeOnlyReconciliationUsesRealEngineSnapshotAndPersistsANewerProjection() {
        TransactionProcessor processor = mock(TransactionProcessor.class);
        try (RdbmsFlowExecutionStore executions = RdbmsFlowExecutionStore.builder()
                .jdbcUrl(url("engine")).build();
             RdbmsTxStreamStateStore items = RdbmsTxStreamStateStore.builder()
                     .jdbcUrl(url("items")).build();
             TxFlowStream reader = TxFlowStream.builder("payouts", engine(executions, processor))
                     .stateStore(items).open()) {
            Instant now = Instant.now();
            TxFlow flow = TxFlow.builder("flow").addStep(FlowStep.builder("step").withTxPlan(plan()).build()).build();
            String portable = TxFlowCodec.standard().write(flow,
                    FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
            items.registerItem(new TxStreamItemRecord("one", "one", "lane", "fp", now));
            items.bind("one", new TxStreamBinding("execution", "flow", "step", "lane"));
            items.persistPlanned(new TxStreamPlannedRecord("payouts", "execution", "one", "lane",
                    "addr:addr_test1vpqsender", portable, Map.of(), Map.of(), Map.of(),
                    List.of(new TxStreamPlannedRecord.Member("one", "one", "step", "fp"))));
            items.projectItem(TxStreamItemResult.builder("payouts", "one", TxStreamItemStatus.RECOVERY_REQUIRED)
                    .executionId("execution").stepId("step").laneName("lane")
                    .transactionHash("known-hash").updatedAt(now).build(), 100);
            executions.createOrGet("namespace", "claim", new FlowExecutionSnapshot("execution", "def", "req",
                    FlowExecutionState.COMPLETED, 0, 0, 0, now, Map.of()));

            assertEquals(TxStreamItemStatus.CONFIRMED, reader.awaitResolution("one",
                    Duration.ofSeconds(1), Duration.ofMillis(1)).getStatus());
            assertEquals("known-hash", items.getItem("payouts", "one").orElseThrow().getTransactionHash());
            assertEquals(TxStreamItemStatus.CONFIRMED, items.getItem("payouts", "one").orElseThrow().getStatus());
            assertEquals(101, items.getStoredProjection("payouts", "one").orElseThrow().sourceSequence());
            verifyNoInteractions(processor);
        }
    }

    private FlowEngine engine(RdbmsFlowExecutionStore store, TransactionProcessor processor) {
        return FlowEngine.builder(mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class),
                        processor, mock(ChainDataSupplier.class))
                .executor(Runnable::run).maintenanceExecutor(Runnable::run).store(store).build();
    }

    private TxPlan plan() {
        return TxPlan.from(new Tx().payToAddress("addr_test1vpqreceiver", Amount.ada(2))
                .from("addr_test1vpqsender"));
    }

    private String url(String name) {
        return "jdbc:h2:file:" + directory.resolve(name).toAbsolutePath();
    }
}
