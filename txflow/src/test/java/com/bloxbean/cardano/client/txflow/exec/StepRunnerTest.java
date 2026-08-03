package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.config.RetryAction;
import com.bloxbean.cardano.client.txflow.config.RetryContext;
import com.bloxbean.cardano.client.txflow.config.RetryDecision;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CancellationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepRunnerTest {
    @Test
    void cancellationBeforeFirstAttemptNeverExecutesTheAttempt() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        AtomicInteger attempts = new AtomicInteger();
        FlowStep step = FlowStep.builder("step").withTxContext(builder -> null).build();

        FlowStepResult result = new StepRunner(scheduler, FlowListener.NOOP).run(
                step, RetryPolicy.noRetry(), () -> {
                    attempts.incrementAndGet();
                    return FlowStepResult.failure("step", new AssertionError("must not run"));
                }, failure -> FlowStepResult.failure("step", failure), () -> true);

        assertEquals(0, attempts.get());
        assertEquals(FlowStatus.CANCELLED, result.getStatus());
        assertInstanceOf(CancellationException.class, result.getError());
    }

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

    @Test
    void policyCannotCollapseUnknownSubmissionIntoOrdinaryFailure() {
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
        RetryPolicy policy = mock(RetryPolicy.class);
        when(policy.getMaxAttempts()).thenReturn(1);
        when(policy.evaluate(any(RetryContext.class))).thenReturn(
                new RetryDecision(RetryAction.FAIL, Duration.ZERO, "server-declined"));
        AtomicInteger reconciliations = new AtomicInteger();

        FlowStepResult result = new StepRunner(scheduler, FlowListener.NOOP).run(step,
                policy, () -> FlowStepResult.failure("step", uncertain), failure -> {
                    reconciliations.incrementAndGet();
                    return FlowStepResult.success("step", failure.getTransactionHash(), List.of());
                }, () -> false);

        assertInstanceOf(ReconciliationUncertainException.class, result.getError());
        assertEquals(uncertain.getTransactionHash(), result.getTransactionHash());
        assertEquals(0, reconciliations.get());
    }
}
