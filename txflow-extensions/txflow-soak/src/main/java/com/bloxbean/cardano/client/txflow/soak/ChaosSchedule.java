package com.bloxbean.cardano.client.txflow.soak;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled fault injection.
 *
 * <p>A soak that only applies load answers "does it work when nothing goes wrong", which is the
 * least interesting question. These are the faults TxStream specifically claims to survive, so
 * they are the ones worth firing repeatedly while it is busy.
 *
 * <ul>
 *   <li><b>crash</b> — {@code Runtime.halt()} mid-flight. No shutdown hooks, no drain: whatever
 *       survives, survives because it reached the store. Requires the run to be resumable, which
 *       is what {@link SoakJournal} is for. Drive restarts with a shell loop or systemd.</li>
 *   <li><b>rollback</b> — rewind the devnet under the stream and see whether its projection
 *       survives a reorg. DevKit only.</li>
 *   <li><b>failover</b> — abort the active instance so a standby takes the epoch-fenced
 *       ownership lease over and continues. Requires {@code --instances=2}.</li>
 * </ul>
 *
 * <p>Each fault has its own independent interval, so they interleave rather than colliding on a
 * single schedule.
 */
public final class ChaosSchedule {

    public enum Fault { CRASH, ROLLBACK, FAILOVER }

    private final Duration crashInterval;
    private final Duration rollbackInterval;
    private final Duration failoverInterval;

    private Instant nextCrash;
    private Instant nextRollback;
    private Instant nextFailover;

    private final AtomicInteger crashes = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();
    private final AtomicInteger failovers = new AtomicInteger();

    public ChaosSchedule(Duration crashInterval, Duration rollbackInterval,
                         Duration failoverInterval) {
        this.crashInterval = crashInterval;
        this.rollbackInterval = rollbackInterval;
        this.failoverInterval = failoverInterval;
        Instant now = Instant.now();
        // Offset the first firing so faults do not all land at once at the start.
        this.nextCrash = crashInterval == null ? null : now.plus(crashInterval);
        this.nextRollback = rollbackInterval == null ? null
                : now.plus(rollbackInterval.dividedBy(2)).plus(rollbackInterval);
        this.nextFailover = failoverInterval == null ? null : now.plus(failoverInterval);
    }

    public boolean isEnabled() {
        return crashInterval != null || rollbackInterval != null || failoverInterval != null;
    }

    /** @return the fault that is now due, or null. Rearms the timer as a side effect. */
    public Fault due() {
        Instant now = Instant.now();
        if (nextRollback != null && now.isAfter(nextRollback)) {
            nextRollback = now.plus(rollbackInterval);
            rollbacks.incrementAndGet();
            return Fault.ROLLBACK;
        }
        if (nextFailover != null && now.isAfter(nextFailover)) {
            nextFailover = now.plus(failoverInterval);
            failovers.incrementAndGet();
            return Fault.FAILOVER;
        }
        // Crash last: it ends the process, so let the others fire first when co-scheduled.
        if (nextCrash != null && now.isAfter(nextCrash)) {
            nextCrash = now.plus(crashInterval);
            crashes.incrementAndGet();
            return Fault.CRASH;
        }
        return null;
    }

    public String describe() {
        if (!isEnabled()) return "disabled";
        StringBuilder sb = new StringBuilder();
        if (crashInterval != null) sb.append("crash every ").append(crashInterval.toSeconds()).append("s  ");
        if (rollbackInterval != null) sb.append("rollback every ").append(rollbackInterval.toSeconds()).append("s  ");
        if (failoverInterval != null) sb.append("failover every ").append(failoverInterval.toSeconds()).append("s");
        return sb.toString().trim();
    }

    public int rollbacksFired() {
        return rollbacks.get();
    }

    public int failoversFired() {
        return failovers.get();
    }
}
