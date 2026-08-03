package com.bloxbean.cardano.client.txflow.store.contract;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe controllable UTC clock for store conformance tests. */
public final class AdjustableClock extends Clock {
    private final AtomicReference<Instant> current;

    /**
     * Creates a clock at the supplied instant.
     *
     * @param initial initial UTC instant
     */
    public AdjustableClock(Instant initial) {
        current = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    /** Advances the clock by a non-null duration. */
    public void advance(Duration duration) {
        current.updateAndGet(value -> value.plus(Objects.requireNonNull(duration, "duration")));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) {
            throw new IllegalArgumentException("AdjustableClock supports UTC only");
        }
        return this;
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
