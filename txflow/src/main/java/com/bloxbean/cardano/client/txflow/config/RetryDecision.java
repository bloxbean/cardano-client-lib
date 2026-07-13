package com.bloxbean.cardano.client.txflow.config;

import java.time.Duration;

/**
 * Result of evaluating a {@link RetryContext} against retry policy.
 *
 * @param action safe next action
 * @param delay delay before that action is attempted
 * @param reasonCode stable machine-readable explanation suitable for events
 *                   and diagnostics
 */
public record RetryDecision(RetryAction action, Duration delay, String reasonCode) {
}
