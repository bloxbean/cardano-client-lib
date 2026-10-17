package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.testsupport.FakeChain;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.RECEIVER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.SENDER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeExtension.pay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0008 M3: TxFlowStream items that use QuickTx extensions, with the registrations of the
 * engine the stream runs on.
 */
class TxFlowStreamExtensionTest {
    private final FakeExtension extension = new FakeExtension();

    private TxWorkItem extensionItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId, extension.plan(SENDER, pay(RECEIVER, 3_000_000)));
    }

    @Test
    void anExtensionItemRunsOnAnEngineThatRegistersTheExtension() {
        FakeChain chain = new FakeChain();
        chain.confirmOnSubmit = true;
        FlowEngine engine = FlowEngine.builder(chain.utxoSupplier, chain.protocolParamsSupplier,
                        chain.transactionProcessor, chain.chainDataSupplier)
                .executor(Runnable::run)
                .signerRegistry(chain.signers)
                .extension(extension)
                .build();

        try (TxFlowStream stream = TxFlowStream.open("extension-items", engine)) {
            TxStreamItemResult outcome = stream.submit(extensionItem("item-1"))
                    .completion().toCompletableFuture().join();

            assertThat(outcome.getStatus()).as(String.valueOf(outcome)).isEqualTo(TxStreamItemStatus.CONFIRMED);
            assertThat(chain.submitted).containsExactly(outcome.getTransactionHash());
        }
    }

    /** I1 (stream entry): no registration means the item is refused before anything is stored. */
    @Test
    void anEngineWithoutTheExtensionRefusesTheItemBeforeRegistration() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = builder(gateway).stateStore(store).build()) {
            stream.start();
            assertThatThrownBy(() -> stream.submit(extensionItem("item-1")))
                    .isInstanceOfSatisfying(TxStreamException.class, error ->
                            assertThat(error.getCode()).isEqualTo("TXSTREAM_NON_PORTABLE_ITEM"))
                    .hasMessageContaining("TXFLOW_EXTENSION_UNREGISTERED");
            assertThat(store.calls).isEmpty();
            assertThat(gateway.started).isEmpty();
        }
    }

    /**
     * An item without {@code from} gets its lane's funding source on a literal copy of the plan,
     * which keeps the extension binding and intent.
     */
    @Test
    void laneFundingScopeIsAppliedToALiteralCopyOfTheExtensionPlan() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.extensions = List.of(extension);
        TxPlan plan = TxPlan.from(new Tx()).withExtension(FakeExtension.NAMESPACE, extension.metadata());
        plan.getTxs().get(0).addIntention(pay(RECEIVER, 3_000_000).note("literal ${other} stays"));
        try (TxFlowStream stream = builder(gateway).build()) {
            stream.start();
            stream.submit(TxWorkItem.fromTxPlan("item-1", plan));

            FlowStep step = gateway.started.get(0).getDefinition().getSteps().get(0);
            TxPlan started = step.getTxPlan();
            assertThat(((Tx) started.getTxs().get(0)).getSender()).isEqualTo(SENDER);
            assertThat(started.getExtensions()).isEqualTo(plan.getExtensions());
            assertThat(((FakeExtension.PayIntent) started.getTxs().get(0).getIntentions().get(0)).getNote())
                    .isEqualTo("literal ${other} stays");
            assertThat(((Tx) plan.getTxs().get(0)).getSender()).as("the submitted plan is not mutated").isNull();
            completeAll(gateway);
        }
    }

    /** I9: the batching planner never merges an item holding an extension intent. */
    @Test
    void theBatchingPlannerNeverMergesExtensionItems() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.extensions = List.of(extension);
        try (TxFlowStream stream = builder(gateway).planner(TxStreamPlanner.batching())
                .window(WindowPolicy.count(3)).build()) {
            stream.start();
            stream.submit(TxWorkItem.fromTxPlan("pay-1",
                    TxPlan.from(new Tx().payToAddress("recv-1", Amount.ada(1.5)))));
            stream.submit(TxWorkItem.fromTxPlan("pay-2",
                    TxPlan.from(new Tx().payToAddress("recv-2", Amount.ada(1.5)))));
            stream.submit(extensionItem("ext-1"));

            completeAll(gateway);
            List<FlowExecutionRequest> started = gateway.started;
            assertThat(started).hasSize(2);
            assertThat(started).filteredOn(request -> request.getDefinition().getSteps().get(0)
                            .getTxPlan().hasExtensions())
                    .singleElement()
                    .satisfies(request -> assertThat(request.getDefinition().getSteps().get(0)
                            .getTxPlan().getTxs().get(0).getIntentions()).hasSize(1));
        }
    }

    /**
     * A durable item whose start was lost is re-dispatched after a restart from its persisted
     * portable payload, which therefore carries the extension binding and intent.
     */
    @Test
    void aPersistedExtensionItemIsRedispatchedFromItsPayloadAfterRestart() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = new StubEngineGateway();
        engine.durable = true;
        engine.extensions = List.of(extension);

        TxFlowStream a = builder(engine).stateStore(store).build();
        a.start();
        String executionId = a.submit(extensionItem("ext-1")).executionId().orElseThrow();
        assertThat(store.plannedByExecution(executionId)).isPresent();
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = builder(engine).stateStore(store).build()) {
            b.start();
            assertThat(b.reattach().redispatched()).isEqualTo(1);
            assertThat(engine.started).hasSize(1);
            var definition = engine.started.get(0).getDefinition();
            assertThat(definition.getExtensions()).containsOnlyKeys(FakeExtension.NAMESPACE);
            assertThat(definition.getSteps().get(0).getTransactionTemplate().toJsonNode()
                    .at("/tx/intents/0/type").asText()).isEqualTo("fk:pay");
            completeAll(engine);
        }
    }

    private static void completeAll(StubEngineGateway gateway) {
        for (int i = 0; i < gateway.started.size(); i++) {
            gateway.handles.get(i).completeConfirmed(
                    gateway.started.get(i).getDefinition().getSteps().get(0).getId(), "tx-" + i);
        }
    }

    private static TxFlowStream.Builder builder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }
}
