package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.jmt.store.JmtAccessLockProvider;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessMode;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

/** PostgreSQL transaction-scoped advisory locks for one JMT namespace. */
final class PostgresJmtAccessLockProvider implements JmtAccessLockProvider {

    private static final Map<DataSource, CapacityGuard> CAPACITY_GUARDS = new WeakHashMap<>();

    private final DataSource dataSource;
    private final CapacityGuard capacityGuard;
    private final long namespaceGateKey;
    private final long writerKey;

    PostgresJmtAccessLockProvider(DataSource dataSource, String namespaceIdentity) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(namespaceIdentity, "namespaceIdentity");
        this.capacityGuard = capacityGuard(dataSource);
        String scopedIdentity = databaseScope(dataSource) + ":" + namespaceIdentity;
        this.namespaceGateKey = lockKey(scopedIdentity + ":gate");
        this.writerKey = lockKey(scopedIdentity + ":writer");
    }

    @Override
    public LockHandle tryAcquire(JmtAccessMode mode, String operation, Long version) {
        if (!capacityGuard.tryAcquire()) {
            return null;
        }
        Connection connection = null;
        Boolean originalAutoCommit = null;
        try {
            connection = dataSource.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean acquired;
            switch (mode) {
                case READ:
                    acquired = tryLock(connection, "pg_try_advisory_xact_lock_shared",
                            namespaceGateKey);
                    break;
                case UPDATE:
                    acquired = tryLock(connection, "pg_try_advisory_xact_lock_shared",
                            namespaceGateKey)
                            && tryLock(connection, "pg_try_advisory_xact_lock", writerKey);
                    break;
                case MAINTENANCE:
                    acquired = tryLock(connection, "pg_try_advisory_xact_lock",
                            namespaceGateKey);
                    break;
                default:
                    throw new IllegalStateException("Unhandled JMT access mode: " + mode);
            }
            if (!acquired) {
                rollbackAndClose(connection, originalAutoCommit);
                capacityGuard.release();
                return null;
            }
            return new PostgresLockHandle(connection, originalAutoCommit, capacityGuard);
        } catch (SQLException e) {
            rollbackAndClose(connection, originalAutoCommit);
            capacityGuard.release();
            throw new RuntimeException("Failed to acquire PostgreSQL JMT advisory lock for "
                    + operation + (version == null ? "" : " at version " + version), e);
        } catch (RuntimeException e) {
            rollbackAndClose(connection, originalAutoCommit);
            capacityGuard.release();
            throw e;
        }
    }

    private static String databaseScope(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = String.valueOf(connection.getCatalog());
            String schema = String.valueOf(connection.getSchema());
            return catalog + ":" + schema;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Cannot resolve PostgreSQL database/schema for JMT locks", e);
        }
    }

    private static CapacityGuard capacityGuard(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource)) {
            return CapacityGuard.UNBOUNDED;
        }
        int poolSize = ((HikariDataSource) dataSource).getMaximumPoolSize();
        if (poolSize < 2) {
            throw new IllegalArgumentException("PostgreSQL JMT requires a JDBC pool with at least "
                    + "2 connections (one access lease and one data connection)");
        }
        synchronized (CAPACITY_GUARDS) {
            return CAPACITY_GUARDS.computeIfAbsent(dataSource,
                    ignored -> new CapacityGuard(poolSize - 1));
        }
    }

    private static boolean tryLock(Connection connection, String function, long key)
            throws SQLException {
        String sql = "SELECT " + function + "(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static long lockKey(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    private static void rollbackAndClose(Connection connection, Boolean originalAutoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
        restoreAutoCommit(connection, originalAutoCommit);
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    private static void restoreAutoCommit(Connection connection, Boolean originalAutoCommit) {
        if (originalAutoCommit == null) {
            return;
        }
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ignored) {
        }
    }

    private static final class PostgresLockHandle implements LockHandle {
        private final Connection connection;
        private final boolean originalAutoCommit;
        private final CapacityGuard capacityGuard;
        private boolean closed;

        private PostgresLockHandle(Connection connection,
                                   boolean originalAutoCommit,
                                   CapacityGuard capacityGuard) {
            this.connection = connection;
            this.originalAutoCommit = originalAutoCommit;
            this.capacityGuard = capacityGuard;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            SQLException failure = null;
            try {
                connection.rollback();
            } catch (SQLException e) {
                failure = e;
            }
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            try {
                connection.close();
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            } finally {
                capacityGuard.release();
            }
            if (failure != null) {
                throw new RuntimeException("Failed to release PostgreSQL JMT advisory lock", failure);
            }
        }
    }

    private static final class CapacityGuard {
        private static final CapacityGuard UNBOUNDED = new CapacityGuard(null);
        private final Semaphore permits;

        private CapacityGuard(int permits) {
            this(new Semaphore(permits, true));
        }

        private CapacityGuard(Semaphore permits) {
            this.permits = permits;
        }

        private boolean tryAcquire() {
            return permits == null || permits.tryAcquire();
        }

        private void release() {
            if (permits != null) {
                permits.release();
            }
        }
    }
}
