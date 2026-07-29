package com.bloxbean.cardano.client.txflow.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Portable rollback request, kept separate from ordinary retry policy.
 *
 * <p>This policy is consulted only after rollback has been established. The
 * absence threshold counts consecutive observations that the backend adapter
 * declares authoritative; ambiguous or unknown observations never become proof
 * of rollback regardless of this value.</p>
 *
 * @param action response to an established rollback
 * @param monitoringHorizon how long included transactions remain monitored
 * @param rebuildScope minimum graph scope considered when rebuilding invalid work
 * @param maxRecoveryCycles maximum automated rollback-recovery cycles
 * @param reinclusionWindow maximum wait for the same hash to be included again
 * @param minimumConsistentAbsenceObservations consecutive authoritative absence
 *                                                observations required for rollback
 */
public record RollbackPolicy(RollbackAction action,
                             RollbackMonitoringHorizon monitoringHorizon,
                             RollbackRebuildScope rebuildScope,
                             int maxRecoveryCycles,
                             Duration reinclusionWindow,
                             int minimumConsistentAbsenceObservations) {
    /**
     * Creates a validated portable rollback policy.
     *
     * @param action response to an established rollback
     * @param monitoringHorizon duration of rollback monitoring
     * @param rebuildScope minimum graph scope considered for rebuild
     * @param maxRecoveryCycles automated recovery-cycle bound from 0 through 100
     * @param reinclusionWindow non-negative same-hash wait of at most seven days
     * @param minimumConsistentAbsenceObservations authoritative-absence threshold
     */
    public RollbackPolicy {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(monitoringHorizon, "monitoringHorizon");
        Objects.requireNonNull(rebuildScope, "rebuildScope");
        Objects.requireNonNull(reinclusionWindow, "reinclusionWindow");
        if (maxRecoveryCycles < 0 || maxRecoveryCycles > 100) {
            throw new IllegalArgumentException("maxRecoveryCycles must be between 0 and 100");
        }
        if (reinclusionWindow.isNegative() || reinclusionWindow.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("reinclusionWindow must be between zero and seven days");
        }
        if (minimumConsistentAbsenceObservations < 1 || minimumConsistentAbsenceObservations > 100) {
            throw new IllegalArgumentException("minimumConsistentAbsenceObservations must be between 1 and 100");
        }
    }

    /**
     * Returns the portable default: monitor until flow completion, reconcile
     * the invalidated closure, and allow three recovery cycles.
     *
     * @return default rollback policy
     */
    public static RollbackPolicy defaults() {
        return new RollbackPolicy(RollbackAction.RECONCILE_AND_REBUILD,
                RollbackMonitoringHorizon.UNTIL_FLOW_TERMINAL,
                RollbackRebuildScope.INVALIDATED_CLOSURE, 3,
                Duration.ofMinutes(2), 2);
    }
}
