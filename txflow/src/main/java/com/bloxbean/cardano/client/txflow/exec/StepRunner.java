package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.config.FlowErrorPhase;
import com.bloxbean.cardano.client.txflow.config.RetryAction;
import com.bloxbean.cardano.client.txflow.config.RetryContext;
import com.bloxbean.cardano.client.txflow.config.RetryDecision;
import com.bloxbean.cardano.client.txflow.config.SubmissionOutcome;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
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
            FlowStepResult result = attempt.execute();
            if (result.isSuccessful()) return result;
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
                    if (decision.action() != RetryAction.RECONCILE_THEN_RETRY) return result;
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
                return FlowStepResult.failure(step.getId(), new RuntimeException("Flow cancelled"));
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
                    return FlowStepResult.failure(step.getId(), new RuntimeException("Flow cancelled"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FlowStepResult.failure(step.getId(), e);
            }
        }
        return FlowStepResult.failure(step.getId(), lastError);
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
