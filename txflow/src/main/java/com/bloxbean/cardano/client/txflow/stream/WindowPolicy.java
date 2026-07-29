package com.bloxbean.cardano.client.txflow.stream;

import java.time.Duration;
import java.util.Objects;

/**
 * Count/time policy deciding when a stream's accepted items are handed to the
 * {@link TxStreamPlanner} as one window.
 * <p>
 * Accepted items accumulate in the stream's window buffer; the window closes —
 * and its items are planned — when the item count reaches {@code maxItems},
 * when the oldest buffered item's age reaches {@code maxAge}, or when
 * {@link TxFlowStream#flush()}, {@link TxFlowStream#drain()},
 * {@link TxFlowStream#close()}, or {@link TxFlowStream#abort(String)} closes
 * it explicitly (abort fails the open window's items
 * {@link TxStreamItemStatus#CANCELLED} instead of planning them).
 * <p>
 * Time-based policies ({@link #time(Duration)} and
 * {@link #countOrTime(int, Duration)}) require a caller-owned
 * {@code ScheduledExecutorService} on the stream builder
 * ({@code maintenanceExecutor(...)}): the stream never owns threads or
 * timers, so the age check is one scheduled wakeup per open window on the
 * caller's scheduler, cancelled when the window closes early. A stream with
 * no window policy configured plans every item immediately — a window of one
 * with no timer.
 */
public final class WindowPolicy {
    private final int maxItems;
    private final Duration maxAge;

    private WindowPolicy(int maxItems, Duration maxAge) {
        if (maxItems <= 0) {
            throw new IllegalArgumentException("maxItems must be positive");
        }
        if (maxAge != null && (maxAge.isNegative() || maxAge.isZero())) {
            throw new IllegalArgumentException("maxAge must be positive");
        }
        this.maxItems = maxItems;
        this.maxAge = maxAge;
    }

    /**
     * Creates a count-only policy: the window closes when it holds
     * {@code maxItems} items (or on an explicit flush/drain/close). No timer
     * is involved, so no maintenance scheduler is required.
     *
     * @param maxItems maximum items in one window
     * @return count-only window policy
     */
    public static WindowPolicy count(int maxItems) {
        return new WindowPolicy(maxItems, null);
    }

    /**
     * Creates a hybrid policy: the window closes when it reaches
     * {@code maxItems} or when the oldest buffered item has waited
     * {@code maxAge}, whichever comes first.
     *
     * @param maxItems maximum items in one window
     * @param maxAge maximum age of the oldest item in a partial window
     * @return count-or-time window policy
     */
    public static WindowPolicy countOrTime(int maxItems, Duration maxAge) {
        return new WindowPolicy(maxItems,
                Objects.requireNonNull(maxAge, "maxAge cannot be null"));
    }

    /**
     * Creates a time-only policy: the window closes when the oldest buffered
     * item has waited {@code maxAge}, with no practical count limit.
     *
     * @param maxAge maximum age of the oldest item in a partial window
     * @return time-only window policy
     */
    public static WindowPolicy time(Duration maxAge) {
        return new WindowPolicy(Integer.MAX_VALUE,
                Objects.requireNonNull(maxAge, "maxAge cannot be null"));
    }

    /**
     * Returns the maximum number of items in one window.
     *
     * @return item bound; {@link Integer#MAX_VALUE} for time-only policies
     */
    public int getMaxItems() {
        return maxItems;
    }

    /**
     * Returns the maximum age of the oldest item in a partial window.
     *
     * @return age bound, or {@code null} for count-only policies
     */
    public Duration getMaxAge() {
        return maxAge;
    }

    /** Whether this policy needs the builder's maintenance scheduler. */
    boolean isTimeBased() {
        return maxAge != null;
    }
}
