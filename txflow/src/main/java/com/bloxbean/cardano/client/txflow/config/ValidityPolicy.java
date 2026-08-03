package com.bloxbean.cardano.client.txflow.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Portable preferences for the validity interval assigned to transaction attempts.
 *
 * <p>The duration is a requested window, not an absolute chain position.
 * Absolute slot checks require the current chain tip and remain a preflight or
 * runtime concern. The safety margin is expressed in slots and keeps recovery
 * from resubmitting signed bytes too close to their recorded upper bound.</p>
 *
 * @param window requested transaction validity-window duration
 * @param resubmitSafetyMargin number of slots before {@code validToSlot} at
 *                             which identical-payload resubmission stops being safe
 */
public record ValidityPolicy(Duration window, long resubmitSafetyMargin) {
    /**
     * Creates validated portable validity preferences.
     *
     * @param window positive requested validity-window duration
     * @param resubmitSafetyMargin non-negative resubmission margin in slots
     */
    public ValidityPolicy {
        Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("validity window must be positive");
        }
        if (resubmitSafetyMargin < 0) {
            throw new IllegalArgumentException("resubmitSafetyMargin cannot be negative");
        }
    }
}
