package com.bloxbean.cardano.vds.jmt.store;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fail-fast access coordinator for one logical JMT namespace.
 *
 * <p>Reads may overlap copy-on-write updates. Only one update may be active, and maintenance is
 * exclusive. Same-mode leases are reentrant for their owning thread so a tree-level operation can
 * safely invoke a guarded store operation. Cross-mode nesting is rejected to avoid lock upgrades
 * and exhausting external-lock connection pools.</p>
 */
public final class JmtAccessCoordinator {

    private final ReentrantLock stateLock = new ReentrantLock();
    private final Map<Thread, ReadHold> readers = new IdentityHashMap<>();
    private final JmtAccessLockProvider lockProvider;

    private Thread updateOwner;
    private int updateDepth;
    private ActiveOperation updateOperation;
    private JmtAccessLockProvider.LockHandle updateLock;

    private Thread maintenanceOwner;
    private int maintenanceDepth;
    private ActiveOperation maintenanceOperation;
    private JmtAccessLockProvider.LockHandle maintenanceLock;

    public JmtAccessCoordinator() {
        this((mode, operation, version) -> NoOpLockHandle.INSTANCE);
    }

    public JmtAccessCoordinator(JmtAccessLockProvider lockProvider) {
        this.lockProvider = Objects.requireNonNull(lockProvider, "lockProvider");
    }

    public JmtAccessLease tryAcquireRead(String operation) {
        return tryAcquire(JmtAccessMode.READ, operation, null);
    }

    public JmtAccessLease tryAcquireRead(String operation, long version) {
        return tryAcquire(JmtAccessMode.READ, operation, version);
    }

    public JmtAccessLease tryAcquireUpdate(String operation, long version) {
        return tryAcquire(JmtAccessMode.UPDATE, operation, version);
    }

    public JmtAccessLease tryAcquireMaintenance(String operation) {
        return tryAcquire(JmtAccessMode.MAINTENANCE, operation, null);
    }

    public JmtAccessLease tryAcquireMaintenance(String operation, long version) {
        return tryAcquire(JmtAccessMode.MAINTENANCE, operation, version);
    }

    public boolean isHeldByCurrentThread(JmtAccessMode mode) {
        Objects.requireNonNull(mode, "mode");
        Thread current = Thread.currentThread();
        stateLock.lock();
        try {
            switch (mode) {
                case READ:
                    return readers.containsKey(current);
                case UPDATE:
                    return updateOwner == current;
                case MAINTENANCE:
                    return maintenanceOwner == current;
                default:
                    throw new IllegalStateException("Unhandled JMT access mode: " + mode);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private JmtAccessLease tryAcquire(JmtAccessMode mode, String operation, Long version) {
        Objects.requireNonNull(mode, "mode");
        String normalizedOperation = normalizeOperation(operation);
        Thread current = Thread.currentThread();

        stateLock.lock();
        try {
            switch (mode) {
                case READ:
                    acquireRead(current, normalizedOperation, version);
                    break;
                case UPDATE:
                    acquireUpdate(current, normalizedOperation, version);
                    break;
                case MAINTENANCE:
                    acquireMaintenance(current, normalizedOperation, version);
                    break;
                default:
                    throw new IllegalStateException("Unhandled JMT access mode: " + mode);
            }
            return new CoordinatorLease(this, current, mode, normalizedOperation);
        } finally {
            stateLock.unlock();
        }
    }

    private void acquireRead(Thread current, String operation, Long version) {
        if (maintenanceOwner != null) {
            throw conflict(JmtAccessMode.READ, operation, describe(maintenanceOperation));
        }
        if (updateOwner == current) {
            throw conflict(JmtAccessMode.READ, operation, describe(updateOperation));
        }
        ReadHold hold = readers.get(current);
        if (hold == null) {
            JmtAccessLockProvider.LockHandle external = acquireExternal(
                    JmtAccessMode.READ, operation, version);
            readers.put(current, new ReadHold(
                    new ActiveOperation(operation, version, current), external));
        } else {
            hold.depth++;
        }
    }

    private void acquireUpdate(Thread current, String operation, Long version) {
        if (maintenanceOwner != null) {
            throw conflict(JmtAccessMode.UPDATE, operation, describe(maintenanceOperation));
        }
        ReadHold currentRead = readers.get(current);
        if (currentRead != null) {
            throw conflict(JmtAccessMode.UPDATE, operation, describe(currentRead.operation));
        }
        if (updateOwner != null && updateOwner != current) {
            throw conflict(JmtAccessMode.UPDATE, operation, describe(updateOperation));
        }
        if (updateOwner == current) {
            updateDepth++;
        } else {
            JmtAccessLockProvider.LockHandle external = acquireExternal(
                    JmtAccessMode.UPDATE, operation, version);
            updateOwner = current;
            updateDepth = 1;
            updateOperation = new ActiveOperation(operation, version, current);
            updateLock = external;
        }
    }

    private void acquireMaintenance(Thread current, String operation, Long version) {
        if (maintenanceOwner == current) {
            maintenanceDepth++;
            return;
        }
        if (maintenanceOwner != null) {
            throw conflict(JmtAccessMode.MAINTENANCE, operation, describe(maintenanceOperation));
        }
        if (updateOwner != null) {
            throw conflict(JmtAccessMode.MAINTENANCE, operation, describe(updateOperation));
        }
        ActiveOperation otherReader = firstReader();
        if (otherReader != null) {
            throw conflict(JmtAccessMode.MAINTENANCE, operation, describe(otherReader));
        }
        JmtAccessLockProvider.LockHandle external = acquireExternal(
                JmtAccessMode.MAINTENANCE, operation, version);
        maintenanceOwner = current;
        maintenanceDepth = 1;
        maintenanceOperation = new ActiveOperation(operation, version, current);
        maintenanceLock = external;
    }

    private ActiveOperation firstReader() {
        for (Map.Entry<Thread, ReadHold> entry : readers.entrySet()) {
            return entry.getValue().operation;
        }
        return null;
    }

    private JmtAccessLockProvider.LockHandle acquireExternal(JmtAccessMode mode,
                                                             String operation,
                                                             Long version) {
        JmtAccessLockProvider.LockHandle handle = lockProvider.tryAcquire(mode, operation, version);
        if (handle == null) {
            throw conflict(mode, operation, "external namespace lock");
        }
        return handle;
    }

    private void release(Thread owner, JmtAccessMode mode) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("JMT access lease must be closed by its acquiring thread");
        }
        stateLock.lock();
        try {
            switch (mode) {
                case READ:
                    releaseRead(owner);
                    break;
                case UPDATE:
                    releaseUpdate(owner);
                    break;
                case MAINTENANCE:
                    releaseMaintenance(owner);
                    break;
                default:
                    throw new IllegalStateException("Unhandled JMT access mode: " + mode);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void releaseRead(Thread owner) {
        ReadHold hold = readers.get(owner);
        if (hold == null) {
            throw new IllegalStateException("Current thread does not hold a JMT READ lease");
        }
        if (--hold.depth == 0) {
            readers.remove(owner);
            hold.lockHandle.close();
        }
    }

    private void releaseUpdate(Thread owner) {
        if (updateOwner != owner) {
            throw new IllegalStateException("Current thread does not hold a JMT UPDATE lease");
        }
        if (--updateDepth == 0) {
            JmtAccessLockProvider.LockHandle handle = updateLock;
            updateOwner = null;
            updateOperation = null;
            updateLock = null;
            handle.close();
        }
    }

    private void releaseMaintenance(Thread owner) {
        if (maintenanceOwner != owner) {
            throw new IllegalStateException("Current thread does not hold a JMT MAINTENANCE lease");
        }
        if (--maintenanceDepth == 0) {
            JmtAccessLockProvider.LockHandle handle = maintenanceLock;
            maintenanceOwner = null;
            maintenanceOperation = null;
            maintenanceLock = null;
            handle.close();
        }
    }

    private static JmtConcurrentMutationException conflict(JmtAccessMode mode,
                                                            String operation,
                                                            String activeOperation) {
        return new JmtConcurrentMutationException(mode, operation, activeOperation);
    }

    private static String normalizeOperation(String operation) {
        Objects.requireNonNull(operation, "operation");
        String normalized = operation.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        return normalized;
    }

    private static String describe(ActiveOperation operation) {
        if (operation == null) {
            return "unknown operation";
        }
        String version = operation.version == null ? "" : ", version=" + operation.version;
        return operation.operation + version + ", thread=" + operation.threadName;
    }

    private static final class ActiveOperation {
        private final String operation;
        private final Long version;
        private final String threadName;

        private ActiveOperation(String operation, Long version, Thread thread) {
            this.operation = operation;
            this.version = version;
            this.threadName = thread.getName();
        }
    }

    private static final class ReadHold {
        private final ActiveOperation operation;
        private final JmtAccessLockProvider.LockHandle lockHandle;
        private int depth = 1;

        private ReadHold(ActiveOperation operation,
                         JmtAccessLockProvider.LockHandle lockHandle) {
            this.operation = operation;
            this.lockHandle = lockHandle;
        }
    }

    private enum NoOpLockHandle implements JmtAccessLockProvider.LockHandle {
        INSTANCE;

        @Override
        public void close() {
        }
    }

    private static final class CoordinatorLease implements JmtAccessLease {
        private final JmtAccessCoordinator coordinator;
        private final Thread owner;
        private final JmtAccessMode mode;
        private final String operation;
        private boolean closed;

        private CoordinatorLease(JmtAccessCoordinator coordinator,
                                 Thread owner,
                                 JmtAccessMode mode,
                                 String operation) {
            this.coordinator = coordinator;
            this.owner = owner;
            this.mode = mode;
            this.operation = operation;
        }

        @Override
        public JmtAccessMode mode() {
            return mode;
        }

        @Override
        public String operation() {
            return operation;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            coordinator.release(owner, mode);
            closed = true;
        }
    }
}
