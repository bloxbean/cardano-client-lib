package com.bloxbean.cardano.vds.jmt.store;

/**
 * Optional backend-level lock provider used by {@link JmtAccessCoordinator} for cross-wrapper or
 * cross-process arbitration.
 */
public interface JmtAccessLockProvider {

    /**
     * Attempts to acquire a backend lock. Returns {@code null} when it is contended.
     */
    LockHandle tryAcquire(JmtAccessMode mode, String operation, Long version);

    interface LockHandle extends AutoCloseable {
        @Override
        void close();
    }
}
