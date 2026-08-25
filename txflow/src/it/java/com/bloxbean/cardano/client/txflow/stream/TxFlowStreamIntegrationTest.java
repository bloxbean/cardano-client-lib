package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.YaciDevKitUtil;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the FlowEngine-backed TxFlowStream using Yaci DevKit.
 *
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest --tests TxFlowStreamIntegrationTest -Dyaci.integration.test=true
 */
class TxFlowStreamIntegrationTest {
    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private Account sender;
    private Account sender2;
    private Account receiver;
    private ExecutorService engineExecutor;
    private ExecutorService maintenanceExecutor;
    private ExecutorService streamExecutor;
    private FlowEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");

        sender = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 700);
        sender2 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 701);
        receiver = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 702);
        assertTrue(YaciDevKitUtil.topup(sender.baseAddress(), 1000), "Failed to topup sender");
        assertTrue(YaciDevKitUtil.topup(sender2.baseAddress(), 1000), "Failed to topup sender2");
        Thread.sleep(2000);

        engineExecutor = Executors.newFixedThreadPool(4);
        maintenanceExecutor = Executors.newSingleThreadExecutor();
        // Two threads so lanes can dispatch concurrently in the 2-lane test.
        streamExecutor = Executors.newFixedThreadPool(2);
        engine = FlowEngine.builder(
                        new DefaultUtxoSupplier(backendService.getUtxoService()),
                        new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                        new DefaultTransactionProcessor(backendService.getTransactionService()),
                        new DefaultChainDataSupplier(backendService))
                .executor(engineExecutor)
                .maintenanceExecutor(maintenanceExecutor)
                .store(new InMemoryFlowExecutionStore())
                .signerRegistry(new DefaultSignerRegistry()
                        .addAccount("account://sender", sender)
                        .addAccount("account://sender2", sender2))
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        streamExecutor.shutdown();
        engineExecutor.shutdown();
        maintenanceExecutor.shutdown();
        streamExecutor.awaitTermination(10, TimeUnit.SECONDS);
        engineExecutor.awaitTermination(10, TimeUnit.SECONDS);
        maintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    void streamExecutesTxPlanItemsSeriallyOnOneLane() throws Exception {
        // The lane's declared funding source must match the plans' fromRef so
        // mechanical funding-scope enforcement passes (ADR 0004 Decision 2).
        try (TxFlowStream stream = TxFlowStream.builder("stream-it", engine)
                .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))
                .executor(streamExecutor)
                .maxBufferSize(10)
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.builder("payment-1")
                    .withTxPlan(paymentPlan(Amount.ada(1.5)))
                    .withIdempotencyKey("order-1")
                    .build());
            TxStreamReceipt second = stream.submit(TxWorkItem.builder("payment-2")
                    .withTxPlan(paymentPlan(Amount.ada(1.25)))
                    .withIdempotencyKey("order-2")
                    .build());

            TxStreamItemResult firstResult = first.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult secondResult = second.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            stream.awaitDrain(Duration.ofSeconds(60));

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus(),
                    () -> String.valueOf(firstResult.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus(),
                    () -> String.valueOf(secondResult.getError()));
            assertNotNull(firstResult.getTransactionHash());
            assertNotNull(secondResult.getTransactionHash());
            assertNotEquals(firstResult.getTransactionHash(), secondResult.getTransactionHash());
            assertEquals("payouts", firstResult.getLaneName());

            // Idempotent redelivery attaches instead of re-running.
            TxStreamReceipt redelivered = stream.submit(TxWorkItem.builder("payment-1")
                    .withTxPlan(paymentPlan(Amount.ada(1.5)))
                    .withIdempotencyKey("order-1")
                    .build());
            assertSame(first, redelivered);

            TxStreamStats stats = stream.getStats();
            assertEquals(2, stats.acceptedItemCount());
            assertEquals(2, stats.confirmedItemCount());
            assertEquals(2, stats.submittedItemCount());
            assertEquals(0, stats.failedItemCount());
            assertTrue(stream.isHealthy());
        }
    }

    @Test
    void streamExecutesItemsConcurrentlyOnTwoExplicitLanes() throws Exception {
        // Two funded senders, one lane each (mirroring the two-sender MVP IT):
        // items on different canonical identities run concurrently while each
        // lane stays serial. Lane names resolve once through the resolver and
        // the resolved funding scope is enforced against each item's plan.
        try (TxFlowStream stream = TxFlowStream.builder("stream-it-lanes", engine)
                .lanes(LanePolicy.explicit())
                .laneResolver(laneName -> {
                    switch (laneName) {
                        case "lane-1":
                            return ResolvedLane.ofFundingRef("lane-1", "account://sender");
                        case "lane-2":
                            return ResolvedLane.ofFundingRef("lane-2", "account://sender2");
                        default:
                            return null;
                    }
                })
                .maxInFlight(4)
                .executor(streamExecutor)
                .maxBufferSize(10)
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.builder("lane1-payment")
                    .withTxPlan(paymentPlan(Amount.ada(1.5), "account://sender"))
                    .withLane("lane-1")
                    .build());
            TxStreamReceipt second = stream.submit(TxWorkItem.builder("lane2-payment")
                    .withTxPlan(paymentPlan(Amount.ada(1.25), "account://sender2"))
                    .withLane("lane-2")
                    .build());
            // Same lane as the first item: must queue behind it, never overlap.
            TxStreamReceipt third = stream.submit(TxWorkItem.builder("lane1-payment-2")
                    .withTxPlan(paymentPlan(Amount.ada(1.1), "account://sender"))
                    .withLane("lane-1")
                    .build());

            TxStreamItemResult firstResult = first.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult secondResult = second.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult thirdResult = third.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            stream.awaitDrain(Duration.ofSeconds(60));

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus(),
                    () -> String.valueOf(firstResult.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus(),
                    () -> String.valueOf(secondResult.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, thirdResult.getStatus(),
                    () -> String.valueOf(thirdResult.getError()));
            assertNotNull(firstResult.getTransactionHash());
            assertNotNull(secondResult.getTransactionHash());
            assertNotNull(thirdResult.getTransactionHash());
            assertNotEquals(firstResult.getTransactionHash(), secondResult.getTransactionHash());
            assertNotEquals(firstResult.getTransactionHash(), thirdResult.getTransactionHash());
            assertEquals("lane-1", firstResult.getLaneName());
            assertEquals("lane-2", secondResult.getLaneName());
            assertEquals("lane-1", thirdResult.getLaneName());

            TxStreamStats stats = stream.getStats();
            assertEquals(3, stats.acceptedItemCount());
            assertEquals(3, stats.confirmedItemCount());
            assertEquals(0, stats.failedItemCount());
            assertEquals(0, stats.inFlightCount());
            assertTrue(stream.isHealthy());
        }
    }

    @Test
    void perWindowPlannerExecutesOneSharedFlowForAWindowOnOneLane() throws Exception {
        // Iteration 1C: two items in one count window on one lane become ONE
        // two-step engine flow (perWindow partitioning); each item projects
        // its own step's confirmation and the batch derives COMPLETED.
        try (TxFlowStream stream = TxFlowStream.builder("stream-it-window", engine)
                .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))
                .planner(TxStreamPlanner.perWindow())
                .window(WindowPolicy.count(2))
                .executor(streamExecutor)
                .maxBufferSize(10)
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.builder("window-payment-1")
                    .withTxPlan(paymentPlan(Amount.ada(1.5)))
                    .withIdempotencyKey("window-order-1")
                    .build());
            TxStreamReceipt second = stream.submit(TxWorkItem.builder("window-payment-2")
                    .withTxPlan(paymentPlan(Amount.ada(1.25)))
                    .withIdempotencyKey("window-order-2")
                    .build());

            TxStreamItemResult firstResult = first.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult secondResult = second.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            stream.awaitDrain(Duration.ofSeconds(60));

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus(),
                    () -> String.valueOf(firstResult.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus(),
                    () -> String.valueOf(secondResult.getError()));
            assertNotNull(firstResult.getTransactionHash());
            assertNotNull(secondResult.getTransactionHash());
            assertNotEquals(firstResult.getTransactionHash(),
                    secondResult.getTransactionHash(),
                    "each member rides its own transaction inside the shared flow");
            assertEquals(firstResult.getExecutionId(), secondResult.getExecutionId(),
                    "one lane group in the window means one shared engine execution");
            assertNotEquals(firstResult.getStepId(), secondResult.getStepId());
            assertEquals("payouts", firstResult.getLaneName());

            TxStreamBatchResult batch = stream.getBatchStatus("batch-1").orElseThrow();
            assertEquals(TxStreamBatchStatus.COMPLETED, batch.status());
            assertEquals(1, batch.executionIds().size());

            TxStreamStats stats = stream.getStats();
            assertEquals(2, stats.acceptedItemCount());
            assertEquals(2, stats.confirmedItemCount());
            assertEquals(0, stats.failedItemCount());
            assertTrue(stream.isHealthy());
        }
    }

    @Test
    void pipelinedPerWindowSubmitsTheWholeWindowBeforeConfirmation() throws Exception {
        try (TxFlowStream stream = TxFlowStream.builder("stream-it-window-pipelined", engine)
                .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))
                .planner(TxStreamPlanner.perWindow(ChainingMode.PIPELINED))
                .window(WindowPolicy.count(2))
                .executor(streamExecutor)
                .maxBufferSize(10)
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.builder("pipeline-payment-1")
                    .withTxPlan(paymentPlan(Amount.ada(1.5)))
                    .withIdempotencyKey("pipeline-order-1")
                    .build());
            TxStreamReceipt second = stream.submit(TxWorkItem.builder("pipeline-payment-2")
                    .withTxPlan(paymentPlan(Amount.ada(1.25)))
                    .withIdempotencyKey("pipeline-order-2")
                    .build());

            TxStreamItemResult firstResult = first.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult secondResult = second.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            stream.awaitDrain(Duration.ofSeconds(60));

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus(),
                    () -> String.valueOf(firstResult.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus(),
                    () -> String.valueOf(secondResult.getError()));
            assertEquals(firstResult.getExecutionId(), secondResult.getExecutionId());
            assertNotEquals(firstResult.getTransactionHash(), secondResult.getTransactionHash());

            // The durable engine journal proves this was pipelined rather than
            // merely a successful two-step sequential flow: both dependent
            // transactions were submitted before either confirmation callback.
            var eventView = engine.executionEvents(firstResult.getExecutionId(), 0, 100)
                    .orElseThrow();
            var submitted = eventView.events().stream()
                    .filter(event -> event.type() == FlowEventType.TRANSACTION_SUBMITTED)
                    .toList();
            var confirmed = eventView.events().stream()
                    .filter(event -> event.type() == FlowEventType.TRANSACTION_CONFIRMED)
                    .toList();
            assertEquals(2, submitted.size());
            assertEquals(2, confirmed.size());
            long lastSubmission = submitted.stream().mapToLong(FlowEvent::sequence).max()
                    .orElseThrow();
            long firstConfirmation = confirmed.stream().mapToLong(FlowEvent::sequence).min()
                    .orElseThrow();
            assertTrue(lastSubmission < firstConfirmation,
                    "PIPELINED must submit every generated step before awaiting confirmation");

            TxStreamBatchResult batch = stream.getBatchStatus("batch-1").orElseThrow();
            assertEquals(TxStreamBatchStatus.COMPLETED, batch.status());
            assertEquals(1, batch.executionIds().size());
        }
    }

    @Test
    void batchingPlannerMergesTwoPaymentsIntoOneTransactionOnOneLane() throws Exception {
        // Iteration 2d: two payment items in one count window on one lane are
        // MERGED into ONE on-chain transaction (one flow, one merged step). Both
        // items share the transaction's fate — same hash, same execution, same
        // step — so item status is transaction-granular.
        try (TxFlowStream stream = TxFlowStream.builder("stream-it-batch", engine)
                .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))
                .planner(TxStreamPlanner.batching())
                .window(WindowPolicy.count(2))
                .executor(streamExecutor)
                .maxBufferSize(10)
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.builder("batch-payment-1")
                    .withTxPlan(paymentPlanTo(receiver.baseAddress(), Amount.ada(1.5)))
                    .withIdempotencyKey("batch-order-1")
                    .build());
            TxStreamReceipt second = stream.submit(TxWorkItem.builder("batch-payment-2")
                    .withTxPlan(paymentPlanTo(sender2.baseAddress(), Amount.ada(1.25)))
                    .withIdempotencyKey("batch-order-2")
                    .build());

            TxStreamItemResult firstResult = first.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult secondResult = second.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            stream.awaitDrain(Duration.ofSeconds(60));

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus(),
                    () -> String.valueOf(firstResult.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus(),
                    () -> String.valueOf(secondResult.getError()));
            assertNotNull(firstResult.getTransactionHash());
            assertEquals(firstResult.getTransactionHash(), secondResult.getTransactionHash(),
                    "both payments were merged into ONE on-chain transaction");
            assertEquals(firstResult.getExecutionId(), secondResult.getExecutionId(),
                    "one merged engine execution for the whole batch");
            assertEquals(firstResult.getStepId(), secondResult.getStepId(),
                    "both items ride the one merged step");
            assertEquals("payouts", firstResult.getLaneName());

            TxStreamBatchResult batch = stream.getBatchStatus("batch-1").orElseThrow();
            assertEquals(TxStreamBatchStatus.COMPLETED, batch.status());
            assertEquals(1, batch.executionIds().size(),
                    "one merged execution for the whole batch");

            TxStreamStats stats = stream.getStats();
            assertEquals(2, stats.acceptedItemCount());
            assertEquals(2, stats.confirmedItemCount());
            assertTrue(stream.isHealthy());
        }
    }

    @Test
    void templateInvocationsRunTwoParameterizedPaymentsOffOneRegisteredFlow() throws Exception {
        // ADR 0004 iteration 3: register ONE parameterized portable flow and
        // stream two invocations with different bindings on the real engine —
        // each invocation is a parameterized run of the same compiled,
        // fingerprinted definition, producing two distinct on-chain payments.
        TxFlow payoutTemplate = payoutTemplate();
        try (TxFlowStream stream = TxFlowStream.builder("stream-it-template", engine)
                .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))
                .template("payout", payoutTemplate)
                .executor(streamExecutor)
                .maxBufferSize(10)
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.builder("payout-1")
                    .withTemplate("payout")
                    .withIdempotencyKey("payout-order-1")
                    .withBinding("beneficiary", receiver.baseAddress())
                    .withBinding("amount", 1_500_000L)
                    .build());
            TxStreamReceipt second = stream.submit(TxWorkItem.builder("payout-2")
                    .withTemplate("payout")
                    .withIdempotencyKey("payout-order-2")
                    .withBinding("beneficiary", receiver.baseAddress())
                    .withBinding("amount", 1_250_000L)
                    .build());

            TxStreamItemResult firstResult = first.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            TxStreamItemResult secondResult = second.completion().toCompletableFuture()
                    .get(180, TimeUnit.SECONDS);
            stream.awaitDrain(Duration.ofSeconds(60));

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus(),
                    () -> String.valueOf(firstResult.getError()));
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus(),
                    () -> String.valueOf(secondResult.getError()));
            assertNotNull(firstResult.getTransactionHash());
            assertNotNull(secondResult.getTransactionHash());
            assertNotEquals(firstResult.getTransactionHash(), secondResult.getTransactionHash());

            // Idempotent redelivery of the same (template, bindings) attaches.
            TxStreamReceipt redelivered = stream.submit(TxWorkItem.builder("payout-1")
                    .withTemplate("payout")
                    .withIdempotencyKey("payout-order-1")
                    .withBinding("beneficiary", receiver.baseAddress())
                    .withBinding("amount", 1_500_000L)
                    .build());
            assertSame(first, redelivered);

            TxStreamStats stats = stream.getStats();
            assertEquals(2, stats.acceptedItemCount());
            assertEquals(2, stats.confirmedItemCount());
            assertTrue(stream.isHealthy());
        }
    }

    /**
     * A parameterized portable payout template: one step paying a bound
     * {@code beneficiary} address a bound {@code amount}, funded and signed from
     * {@code account://sender} (registered in the engine's signer registry).
     */
    private TxFlow payoutTemplate() {
        String yaml = "api_version: txflow.cardano-client.dev/v1alpha1\n"
                + "kind: TxFlow\n"
                + "metadata: {name: payout-template}\n"
                + "spec:\n"
                + "  parameters:\n"
                + "    beneficiary: {type: address, required: true}\n"
                + "    amount: {type: integer, required: true}\n"
                + "  steps:\n"
                + "    - id: payment\n"
                + "      transaction:\n"
                + "        tx:\n"
                + "          from_ref: account://sender\n"
                + "          intents:\n"
                + "            - type: payment\n"
                + "              address: '${{ inputs.beneficiary }}'\n"
                + "              amounts:\n"
                + "                - unit: lovelace\n"
                + "                  quantity: '${{ inputs.amount }}'\n"
                + "        context:\n"
                + "          signers:\n"
                + "            - ref: account://sender\n"
                + "          fee_payer_ref: account://sender\n";
        return com.bloxbean.cardano.client.txflow.codec.TxFlowCodec.standard()
                .parse(yaml, com.bloxbean.cardano.client.txflow.codec.FlowParseOptions.serverDefaults())
                .requireFlow();
    }

    private TxPlan paymentPlan(Amount amount) {
        return paymentPlan(amount, "account://sender");
    }

    private TxPlan paymentPlanTo(String receiverAddress, Amount amount) {
        return TxPlan.from(new Tx()
                        .payToAddress(receiverAddress, amount)
                        .fromRef("account://sender"))
                .withSigner("account://sender");
    }

    private TxPlan paymentPlan(Amount amount, String senderRef) {
        return TxPlan.from(new Tx()
                        .payToAddress(receiver.baseAddress(), amount)
                        .fromRef(senderRef))
                .withSigner(senderRef);
    }
}
