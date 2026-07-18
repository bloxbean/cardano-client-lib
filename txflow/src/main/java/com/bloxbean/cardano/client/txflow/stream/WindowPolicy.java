package com.bloxbean.cardano.client.txflow.stream;

import lombok.Getter;

import java.time.Duration;
import java.util.Objects;

/**
 * Count/time policy used to decide when accepted stream work should be planned.
 * <p>
 * A window is ready when either the item count reaches {@code maxItems} or the
 * oldest item in the current window has waited for {@code maxAge}.
 */
@Getter
public final class WindowPolicy {
    private final int maxItems;
    private final Duration maxAge;

    private WindowPolicy(int maxItems, Duration maxAge) {
        if (maxItems <= 0) {
            throw new IllegalArgumentException("maxItems must be positive");
        }
        this.maxItems = maxItems;
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge cannot be null");
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("maxAge must be positive");
        }
    }

    /**
     * Create a hybrid count-or-time policy.
     *
     * @param maxItems maximum items in one window
     * @param maxAge maximum age of the oldest item in a partial window
     * @return window policy
     */
    public static WindowPolicy countOrTime(int maxItems, Duration maxAge) {
        return new WindowPolicy(maxItems, maxAge);
    }

    /**
     * Create a count-only policy with a long time threshold.
     *
     * @param maxItems maximum items in one window
     * @return window policy
     */
    public static WindowPolicy count(int maxItems) {
        return new WindowPolicy(maxItems, Duration.ofDays(365));
    }

    /**
     * Create a time-only policy with no practical count limit.
     *
     * @param maxAge maximum age of the oldest item in a partial window
     * @return window policy
     */
    public static WindowPolicy time(Duration maxAge) {
        return new WindowPolicy(Integer.MAX_VALUE, maxAge);
    }

    /**
     * Return the default MVP window policy.
     *
     * @return count/time policy of 50 items or 10 seconds
     */
    public static WindowPolicy defaults() {
        return countOrTime(50, Duration.ofSeconds(10));
    }
}
