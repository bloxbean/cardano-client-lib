package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdbmsJdbcTransactionTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    @Test
    void storeReportsUncertainWhenCommitSucceedsBeforeDriverThrows() {
        JdbcDataSource delegate = h2DataSource();
        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.MIGRATE);
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);

        try (RdbmsFlowExecutionStore store = store(faults.dataSource())) {
            faults.throwAfterNextCommit();

            FlowStoreException failure = assertThrows(FlowStoreException.class,
                    () -> store.createOrGet("tenant", "commit-uncertain",
                            snapshot("commit-uncertain")));

            assertEquals("TXFLOW_STORE_COMMIT_UNCERTAIN", failure.getCode());
            assertNotNull(failure.getCause());
            assertEquals(0, faults.rollbackCalls());
            assertTrue(store.get("commit-uncertain").isPresent(),
                    "the delegate commit completed before the injected driver failure");
        }
    }

    @Test
    void storeReturnsCommittedResultWhenConnectionCloseThrows() {
        JdbcDataSource delegate = h2DataSource();
        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.MIGRATE);
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);

        try (RdbmsFlowExecutionStore store = store(faults.dataSource())) {
            faults.throwAfterNextClose();

            IdempotencyClaimResult result = store.createOrGet(
                    "tenant", "close-after-commit", snapshot("close-after-commit"));

            assertTrue(result.created());
            assertTrue(store.get("close-after-commit").isPresent());
        }
    }

    @Test
    void storeReturnsCommittedResultWhenAutoCommitRestoreThrows() {
        JdbcDataSource delegate = h2DataSource();
        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.MIGRATE);
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);

        try (RdbmsFlowExecutionStore store = store(faults.dataSource())) {
            faults.throwAfterNextAutoCommitRestore();

            IdempotencyClaimResult result = store.createOrGet(
                    "tenant", "restore-after-commit", snapshot("restore-after-commit"));

            assertTrue(result.created());
            assertTrue(store.get("restore-after-commit").isPresent());
        }
    }

    @Test
    void storeRollsBackWorkFailureWithoutReplacingItsTypedCode() {
        JdbcDataSource delegate = h2DataSource();
        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.MIGRATE);
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);

        try (RdbmsFlowExecutionStore store = store(faults.dataSource())) {
            FlowStoreException failure = assertThrows(FlowStoreException.class,
                    () -> store.readEvents("missing", 0, 1));

            assertEquals("TXFLOW_EXECUTION_NOT_FOUND", failure.getCode());
            assertEquals(1, faults.rollbackCalls());
        }
    }

    @Test
    void storeClosesWithoutRestoringAutoCommitWhenRollbackOutcomeIsUncertain() {
        JdbcDataSource delegate = h2DataSource();
        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.MIGRATE);
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);

        try (RdbmsFlowExecutionStore store = store(faults.dataSource())) {
            faults.throwBeforeNextPrepareStatement();
            faults.throwAfterNextRollback();
            faults.throwAfterNextClose();

            FlowStoreException failure = assertThrows(FlowStoreException.class,
                    () -> store.get("rollback-uncertain"));

            assertRollbackUncertain(failure, faults);
            assertEquals(2, failure.getSuppressed().length);
            SQLException workFailure = assertInstanceOf(
                    SQLException.class, failure.getSuppressed()[0]);
            assertEquals("42000", workFailure.getSQLState());
            assertEquals(700, workFailure.getErrorCode());
            assertSanitized(failure);
        }
    }

    @Test
    void schemaReportsUncertainWhenCommitSucceedsBeforeDriverThrows() {
        JdbcDataSource delegate = h2DataSource();
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);
        faults.throwAfterNextCommit();

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> new RdbmsSchemaManager(faults.dataSource(), H2Dialect.INSTANCE,
                        fixedClock()).initialize(SchemaManagement.MIGRATE));

        assertEquals("TXFLOW_STORE_COMMIT_UNCERTAIN", failure.getCode());
        assertNotNull(failure.getCause());
        assertEquals(0, faults.rollbackCalls());
        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.VALIDATE);
    }

    @Test
    void schemaReturnsNormallyWhenConnectionCloseThrowsAfterCommit() {
        JdbcDataSource delegate = h2DataSource();
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);
        faults.throwAfterNextClose();

        new RdbmsSchemaManager(faults.dataSource(), H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.MIGRATE);

        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.VALIDATE);
    }

    @Test
    void schemaReturnsNormallyWhenAutoCommitRestoreThrowsAfterCommit() {
        JdbcDataSource delegate = h2DataSource();
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);
        faults.throwAfterNextAutoCommitRestore();

        new RdbmsSchemaManager(faults.dataSource(), H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.MIGRATE);

        new RdbmsSchemaManager(delegate, H2Dialect.INSTANCE, fixedClock())
                .initialize(SchemaManagement.VALIDATE);
    }

    @Test
    void schemaRollsBackWorkFailureWithoutReplacingItsTypedCode() {
        JdbcDataSource delegate = h2DataSource();
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> new RdbmsSchemaManager(faults.dataSource(), H2Dialect.INSTANCE,
                        fixedClock()).initialize(SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_MISSING", failure.getCode());
        assertEquals(1, faults.rollbackCalls());
    }

    @Test
    void schemaClosesWithoutRestoringAutoCommitWhenRollbackOutcomeIsUncertain() {
        JdbcDataSource delegate = h2DataSource();
        FaultInjectingDataSource faults = new FaultInjectingDataSource(delegate);
        faults.throwAfterNextRollback();
        faults.throwAfterNextClose();

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> new RdbmsSchemaManager(faults.dataSource(), H2Dialect.INSTANCE,
                        fixedClock()).initialize(SchemaManagement.VALIDATE));

        assertRollbackUncertain(failure, faults);
        assertEquals(2, failure.getSuppressed().length);
        FlowStoreException workFailure = assertInstanceOf(
                FlowStoreException.class, failure.getSuppressed()[0]);
        assertEquals("TXFLOW_SCHEMA_MISSING", workFailure.getCode());
        assertSanitized(failure);
    }

    private void assertRollbackUncertain(FlowStoreException failure,
                                         FaultInjectingDataSource faults) {
        assertEquals("TXFLOW_STORE_ROLLBACK_UNCERTAIN", failure.getCode());
        SQLException rollbackFailure = assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("08007", rollbackFailure.getSQLState());
        assertEquals(701, rollbackFailure.getErrorCode());
        assertEquals(1, faults.rollbackCalls());
        assertEquals(0, faults.autoCommitRestoresAfterRollbackFailure());
        assertEquals(1, faults.closesAfterRollbackFailure());
        SQLException closeFailure = assertInstanceOf(
                SQLException.class, failure.getSuppressed()[1]);
        assertEquals("08006", closeFailure.getSQLState());
        assertEquals(702, closeFailure.getErrorCode());
    }

    private void assertSanitized(Throwable failure) {
        if (failure.getMessage() != null) {
            assertFalse(failure.getMessage().contains("secret"),
                    () -> "credential leaked in " + failure.getClass().getName());
        }
        if (failure.getCause() != null) assertSanitized(failure.getCause());
        for (Throwable suppressed : failure.getSuppressed()) assertSanitized(suppressed);
    }

    private RdbmsFlowExecutionStore store(DataSource dataSource) {
        return RdbmsFlowExecutionStore.builder()
                .dataSource(dataSource)
                .dialect(H2Dialect.INSTANCE)
                .schemaManagement(SchemaManagement.VALIDATE)
                .clock(fixedClock())
                .build();
    }

    private JdbcDataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:transaction-phase-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private FlowExecutionSnapshot snapshot(String executionId) {
        return new FlowExecutionSnapshot(executionId, "definition", "request",
                FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of());
    }

    private static final class FaultInjectingDataSource {
        private final DataSource delegate;
        private final AtomicBoolean throwAfterCommit = new AtomicBoolean();
        private final AtomicBoolean throwAfterClose = new AtomicBoolean();
        private final AtomicBoolean throwAfterAutoCommitRestore = new AtomicBoolean();
        private final AtomicBoolean throwBeforePrepareStatement = new AtomicBoolean();
        private final AtomicBoolean throwAfterRollback = new AtomicBoolean();
        private final AtomicBoolean rollbackFailureObserved = new AtomicBoolean();
        private final AtomicInteger rollbackCalls = new AtomicInteger();
        private final AtomicInteger autoCommitRestoresAfterRollbackFailure =
                new AtomicInteger();
        private final AtomicInteger closesAfterRollbackFailure = new AtomicInteger();

        private FaultInjectingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        private DataSource dataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    RdbmsJdbcTransactionTest.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(method, delegate, arguments);
                        if ("getConnection".equals(method.getName())) {
                            return connection((Connection) result);
                        }
                        return result;
                    });
        }

        private Connection connection(Connection delegateConnection) {
            return (Connection) Proxy.newProxyInstance(
                    RdbmsJdbcTransactionTest.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("rollback".equals(method.getName())) {
                            rollbackCalls.incrementAndGet();
                        }
                        if ("prepareStatement".equals(method.getName())
                                && throwBeforePrepareStatement.compareAndSet(true, false)) {
                            throw new SQLException(
                                    "injected work failure; password=work-secret",
                                    "42000", 700);
                        }
                        if ("setAutoCommit".equals(method.getName())
                                && Boolean.TRUE.equals(arguments[0])
                                && rollbackFailureObserved.get()) {
                            autoCommitRestoresAfterRollbackFailure.incrementAndGet();
                        }
                        if ("close".equals(method.getName())
                                && rollbackFailureObserved.get()) {
                            closesAfterRollbackFailure.incrementAndGet();
                        }
                        Object result = invoke(method, delegateConnection, arguments);
                        if ("commit".equals(method.getName())
                                && throwAfterCommit.compareAndSet(true, false)) {
                            throw new SQLException("injected failure after commit", "08006");
                        }
                        if ("rollback".equals(method.getName())
                                && throwAfterRollback.compareAndSet(true, false)) {
                            rollbackFailureObserved.set(true);
                            throw new SQLException(
                                    "injected rollback failure; password=rollback-secret",
                                    "08007", 701);
                        }
                        if ("setAutoCommit".equals(method.getName())
                                && Boolean.TRUE.equals(arguments[0])
                                && throwAfterAutoCommitRestore.compareAndSet(true, false)) {
                            throw new SQLException(
                                    "injected failure after auto-commit restore", "08006");
                        }
                        if ("close".equals(method.getName())
                                && throwAfterClose.compareAndSet(true, false)) {
                            throw new SQLException(
                                    "injected close failure; password=close-secret",
                                    "08006", 702);
                        }
                        return result;
                    });
        }

        private Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        private void throwAfterNextCommit() {
            assertTrue(throwAfterCommit.compareAndSet(false, true));
        }

        private void throwAfterNextClose() {
            assertTrue(throwAfterClose.compareAndSet(false, true));
        }

        private void throwAfterNextAutoCommitRestore() {
            assertTrue(throwAfterAutoCommitRestore.compareAndSet(false, true));
        }

        private void throwBeforeNextPrepareStatement() {
            assertTrue(throwBeforePrepareStatement.compareAndSet(false, true));
        }

        private void throwAfterNextRollback() {
            assertTrue(throwAfterRollback.compareAndSet(false, true));
        }

        private int rollbackCalls() {
            return rollbackCalls.get();
        }

        private int autoCommitRestoresAfterRollbackFailure() {
            return autoCommitRestoresAfterRollbackFailure.get();
        }

        private int closesAfterRollbackFailure() {
            return closesAfterRollbackFailure.get();
        }
    }
}
