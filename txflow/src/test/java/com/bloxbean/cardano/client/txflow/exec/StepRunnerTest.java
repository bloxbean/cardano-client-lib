package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepRunnerTest {
    @Test
    void retriesKnownNetworkFailureThroughSchedulerAndThenSucceeds() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        AtomicInteger attempts = new AtomicInteger();
        FlowStep step = FlowStep.builder("step").withTxContext(builder -> null).build();
        FlowStepResult result = new StepRunner(scheduler, FlowListener.NOOP).run(step,
                RetryPolicy.builder().maxAttempts(2).jitterFactor(0)
                        .initialDelay(Duration.ofSeconds(1)).build(),
                () -> attempts.incrementAndGet() == 1
                        ? FlowStepResult.failure("step", new IOException("network"))
                        : FlowStepResult.success("step", "hash", java.util.List.of(), java.util.List.of()),
                failure -> FlowStepResult.failure("step", failure), () -> false);

        assertTrue(result.isSuccessful());
        assertEquals(2, attempts.get());
        assertEquals(List.of(Duration.ofSeconds(1)), scheduler.getDelays());
    }

    @Test
    void uncertainSubmissionReconcilesEvenOnTheLastAllowedAttempt() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        FlowStep step = FlowStep.builder("step").withTxContext(builder -> null).build();
        Transaction transaction = Transaction.builder()
                        .body(TransactionBody.builder()
                                .inputs(List.of()).outputs(List.of()).fee(BigInteger.ZERO).build())
                        .witnessSet(TransactionWitnessSet.builder().build())
                        .isValid(true)
                        .build();
        UncertainSubmissionException uncertain = new UncertainSubmissionException(
                transaction, new IOException("response lost"));
        AtomicInteger reconciliations = new AtomicInteger();

        FlowStepResult result = new StepRunner(scheduler, FlowListener.NOOP).run(step,
                RetryPolicy.noRetry(),
                () -> FlowStepResult.failure("step", uncertain),
                failure -> {
                    reconciliations.incrementAndGet();
                    return FlowStepResult.success("step", failure.getTransactionHash(), List.of());
                }, () -> false);

        assertTrue(result.isSuccessful());
        assertEquals(1, reconciliations.get());
    }
}
