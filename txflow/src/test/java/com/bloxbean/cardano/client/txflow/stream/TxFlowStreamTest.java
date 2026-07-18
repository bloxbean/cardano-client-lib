package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class TxFlowStreamTest {

    @Test
    void countWindow_executesGeneratedFlowAndCompletesReceipts() throws Exception {
        RecordingRunner runner = new RecordingRunner(true, true);

        try (TxFlowStream stream = TxFlowStream.builder("stream-test", null)
                .withRunner(runner)
                .withWindow(WindowPolicy.countOrTime(2, Duration.ofSeconds(30)))
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.fromFlowStep("item-1", step("step-1")));
            TxStreamReceipt second = stream.submit(TxWorkItem.fromFlowStep("item-2", step("step-2")));

            TxStreamItemResult firstResult = first.await(Duration.ofSeconds(5));
            TxStreamItemResult secondResult = second.await(Duration.ofSeconds(5));

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus());
            assertNotNull(firstResult.getTransactionHash());
            assertNotNull(secondResult.getTransactionHash());
            assertEquals(firstResult.getBatchId(), secondResult.getBatchId());
            assertEquals(1, runner.executedFlows.size());

            TxStreamBatchResult batch = stream.getBatchStatus(firstResult.getBatchId()).orElseThrow();
            assertEquals(TxStreamBatchStatus.COMPLETED, batch.getStatus());
            assertEquals(List.of("item-1", "item-2"), batch.getItemIds());

            TxStreamStats stats = stream.getStats();
            assertEquals(2, stats.getAcceptedItemCount());
            assertEquals(2, stats.getConfirmedItemCount());
            assertEquals(1, stats.getBatchCount());
            assertEquals(1, stats.getGeneratedFlowCount());
        }
    }

    @Test
    void drain_flushesPartialWindowAndStopsAcceptance() throws Exception {
        RecordingRunner runner = new RecordingRunner(true);

        try (TxFlowStream stream = TxFlowStream.builder("drain-test", null)
                .withRunner(runner)
                .withWindow(WindowPolicy.countOrTime(10, Duration.ofSeconds(30)))
                .build()) {
            stream.start();

            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromFlowStep("item-1", step("step-1")));
            stream.drain();

            TxStreamItemResult result = receipt.await(Duration.ofSeconds(5));
            assertEquals(TxStreamItemStatus.CONFIRMED, result.getStatus());
            assertEquals(EmitResult.Status.CLOSED,
                    stream.trySubmit(TxWorkItem.fromFlowStep("item-2", step("step-2"))).getStatus());
        }
    }

    @Test
    void failedGeneratedFlowFailsAffectedItemAndStreamContinues() throws Exception {
        RecordingRunner runner = new RecordingRunner(false, true);

        try (TxFlowStream stream = TxFlowStream.builder("failure-test", null)
                .withRunner(runner)
                .withWindow(WindowPolicy.count(1))
                .build()) {
            stream.start();

            TxStreamReceipt failed = stream.submit(TxWorkItem.fromFlowStep("bad", step("bad-step")));
            TxStreamItemResult failedResult = failed.await(Duration.ofSeconds(5));
            assertEquals(TxStreamItemStatus.FAILED, failedResult.getStatus());
            assertNotNull(failedResult.getFailure());

            TxStreamReceipt recovered = stream.submit(TxWorkItem.fromFlowStep("good", step("good-step")));
            TxStreamItemResult recoveredResult = recovered.await(Duration.ofSeconds(5));
            assertEquals(TxStreamItemStatus.CONFIRMED, recoveredResult.getStatus());

            TxStreamStats stats = stream.getStats();
            assertEquals(1, stats.getFailedItemCount());
            assertEquals(1, stats.getConfirmedItemCount());
        }
    }

    @Test
    void txPlanWorkItemUsesGeneratedStepId() throws Exception {
        RecordingRunner runner = new RecordingRunner(true);
        TxPlan plan = TxPlan.from(new Tx().from("addr_test1vpq"));

        try (TxFlowStream stream = TxFlowStream.builder("txplan-test", null)
                .withRunner(runner)
                .withWindow(WindowPolicy.count(1))
                .build()) {
            stream.start();

            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromTxPlan("plan item", plan));
            TxStreamItemResult result = receipt.await(Duration.ofSeconds(5));

            assertEquals(TxStreamItemStatus.CONFIRMED, result.getStatus());
            assertTrue(result.getStepId().startsWith("item-0-plan-item"));
        }
    }

    private FlowStep step(String id) {
        return FlowStep.builder(id)
                .withTxContext(builder -> builder.compose(new Tx().from("addr_test1vpq")))
                .build();
    }

    private static final class RecordingRunner implements FlowExecutionRunner {
        private final Queue<Boolean> outcomes = new ConcurrentLinkedQueue<>();
        private final List<TxFlow> executedFlows = new java.util.concurrent.CopyOnWriteArrayList<>();

        private RecordingRunner(boolean... outcomes) {
            for (boolean outcome : outcomes) {
                this.outcomes.add(outcome);
            }
        }

        @Override
        public FlowResult execute(TxFlow flow) {
            executedFlows.add(flow);
            boolean success = outcomes.isEmpty() || Boolean.TRUE.equals(outcomes.poll());
            FlowResult.Builder builder = FlowResult.builder(flow.getId()).startedAt(Instant.now());
            for (FlowStep step : flow.getSteps()) {
                if (success) {
                    builder.addStepResult(FlowStepResult.success(
                            step.getId(),
                            "tx-" + step.getId(),
                            Collections.emptyList()));
                } else {
                    builder.addStepResult(FlowStepResult.failure(
                            step.getId(),
                            new IllegalStateException("simulated failure")));
                }
            }

            if (success) {
                return builder.completedAt(Instant.now()).success();
            }
            return builder.failure(new IllegalStateException("simulated flow failure"));
        }
    }
}
