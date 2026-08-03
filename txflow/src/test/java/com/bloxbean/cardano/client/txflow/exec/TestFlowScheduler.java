package com.bloxbean.cardano.client.txflow.exec;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class TestFlowScheduler implements FlowScheduler {
    private Instant currentTime;
    private final List<Duration> delays = new ArrayList<>();

    TestFlowScheduler() {
        this(Instant.parse("2026-01-01T00:00:00Z"));
    }

    TestFlowScheduler(Instant initialTime) {
        this.currentTime = initialTime;
    }

    @Override
    public Instant now() {
        return currentTime;
    }

    @Override
    public long monotonicNanos() {
        return Duration.between(Instant.EPOCH, currentTime).toNanos();
    }

    @Override
    public void sleep(Duration delay) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay cannot be negative");
        }
        delays.add(delay);
        currentTime = currentTime.plus(delay);
    }

    List<Duration> getDelays() {
        return List.copyOf(delays);
    }
}
