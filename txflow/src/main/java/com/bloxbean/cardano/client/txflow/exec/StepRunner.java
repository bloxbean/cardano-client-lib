package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.config.FlowErrorPhase;
import com.bloxbean.cardano.client.txflow.config.RetryAction;
import com.bloxbean.cardano.client.txflow.config.RetryContext;
import com.bloxbean.cardano.client.txflow.config.RetryDecision;
import com.bloxbean.cardano.client.txflow.config.SubmissionOutcome;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Executes and conditionally retries one flow step on the calling task.
 *
 * <p>Retry delays use the supplied {@link FlowScheduler}; the runner creates no
 * task and owns no executor. Ordinary pre-submission failures are evaluated by
 * the step's retry policy. An unknown submission outcome follows a separate
 * path: the default safety behavior reconciles it, and an explicit policy may
 * allow or reject that action. A possibly accepted transaction is never sent
 * through the ordinary fresh-build retry path.</p>
 */
@Slf4j
final class StepRunner {
    /** One isolated build/sign/submit attempt. */
    @FunctionalInterface interface Attempt { FlowStepResult execute(); }
    /** Reconciles a signed transaction whose submission result is unknown. */
    @FunctionalInterface interface UncertainReconciler {
        FlowStepResult reconcile(UncertainSubmissionException failure);
    }

    private final FlowScheduler scheduler;
    private final FlowListener listener;

    StepRunner(FlowScheduler scheduler, FlowListener listener) {
        this.scheduler = scheduler;
        this.listener = listener;
    }

    FlowStepResult run(FlowStep step, RetryPolicy policy, Attempt attempt,
                       UncertainReconciler reconciler, BooleanSupplier cancelled) {
        int maxAttempts = policy != null ? policy.getMaxAttempts() : 1;
        Throwable lastError = null;
        for (int number = 1; number <= maxAttempts; number++) {
            if (cancelled.getAsBoolean()) {
                return cancelled(step);
            }
            FlowStepResult result = attempt.execute();
            if (result.isSuccessful()) return result;
            if (result.getStatus() == FlowStatus.CANCELLED
                    || result.getStatus() == FlowStatus.IN_PROGRESS) {
                return result;
            }
            lastError = result.getError();
            if (lastError instanceof UncertainSubmissionException) {
                UncertainSubmissionException uncertain = (UncertainSubmissionException) lastError;
                if (policy != null) {
                    RetryDecision decision = policy.evaluate(RetryContext.builder()
                            .phase(FlowErrorPhase.SUBMIT)
                            .category(FlowErrorCategory.SUBMISSION)
                            .attempt(number)
                            .transactionHash(uncertain.getTransactionHash())
                            .submissionOutcome(SubmissionOutcome.UNKNOWN)
                            .build());
                    if (decision.action() != RetryAction.RECONCILE_THEN_RETRY) {
                        return uncertainFailure(step, uncertain);
                    }
                }
                return reconciler.reconcile(uncertain);
            }
            if (policy == null || !policy.isRetryable(lastError) || number >= maxAttempts) {
                if (number > 1) listener.onStepRetryExhausted(step, number, lastError);
                return result;
            }
            listener.onStepRetry(step, number, maxAttempts, lastError);
            log.info("Retrying step '{}' (attempt {}/{}): {}", step.getId(), number + 1,
                    maxAttempts, lastError != null ? lastError.getMessage() : "unknown error");
            if (cancelled.getAsBoolean()) {
                return cancelled(step);
            }
            try {
                RetryDecision decision = policy.evaluate(RetryContext.builder()
                        .phase(FlowErrorPhase.BUILD)
                        .category(retryCategory(lastError))
                        .attempt(number)
                        .submissionOutcome(SubmissionOutcome.NOT_ATTEMPTED)
                        .build());
                if (decision.action() == RetryAction.FAIL) return result;
                Duration delay = decision.delay();
                if (!scheduler.sleep(delay, cancelled)) {
                    return cancelled(step);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CancellationException cancellation = new CancellationException(
                        "Flow interrupted while waiting to retry");
                cancellation.initCause(e);
                return FlowStepResult.cancelledAt(
                        step.getId(), cancellation, scheduler.now());
            }
        }
        return FlowStepResult.failure(step.getId(), lastError);
    }

    private FlowStepResult uncertainFailure(
            FlowStep step, UncertainSubmissionException uncertain) {
        Transaction transaction = uncertain.getTransaction();
        List<TransactionInput> spentInputs = transaction.getBody() != null
                && transaction.getBody().getInputs() != null
                ? transaction.getBody().getInputs() : List.of();
        // Uncertain disposition: the submission may have been accepted, so the step
        // settles submission-pending (IN_PROGRESS, hash retained), never FAILED.
        return FlowStepResult.submissionPendingAt(
                step.getId(), uncertain.getTransactionHash(), List.of(), spentInputs,
                new ReconciliationUncertainException(
                        uncertain.getTransactionHash(), uncertain),
                scheduler.now());
    }

    private FlowStepResult cancelled(FlowStep step) {
        return FlowStepResult.cancelledAt(step.getId(),
                new CancellationException("Flow cancelled"), scheduler.now());
    }

    private FlowErrorCategory retryCategory(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.io.IOException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return FlowErrorCategory.NETWORK;
            }
            current = current.getCause();
        }
        // isRetryable() already admitted any message-based legacy fallback.
        return FlowErrorCategory.NETWORK;
    }
}
