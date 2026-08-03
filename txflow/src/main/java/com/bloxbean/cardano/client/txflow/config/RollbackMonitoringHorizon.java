package com.bloxbean.cardano.client.txflow.config;

/** Defines how long an included transaction remains subject to rollback monitoring. */
public enum RollbackMonitoringHorizon {
    /** Stop monitoring after the transaction reaches the step's required depth. */
    UNTIL_STEP_CONFIRMED,
    /** Retain and monitor included attempts until the whole flow becomes terminal. */
    UNTIL_FLOW_TERMINAL
}
