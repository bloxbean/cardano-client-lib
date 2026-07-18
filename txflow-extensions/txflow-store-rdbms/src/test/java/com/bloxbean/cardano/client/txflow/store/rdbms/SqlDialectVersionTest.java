package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlDialectVersionTest {
    @Test
    void h2DialectAcceptsMajorVersionTwoAndLater() throws Exception {
        Connection versionTwo = h2Connection(2);
        Connection laterVersion = h2Connection(3);

        assertDoesNotThrow(() -> H2Dialect.INSTANCE.validateDatabase(versionTwo));
        assertDoesNotThrow(() -> H2Dialect.INSTANCE.validateDatabase(laterVersion));
    }

    @Test
    void h2DialectRejectsLegacyMajorVersion() throws Exception {
        Connection legacy = h2Connection(1);

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> H2Dialect.INSTANCE.validateDatabase(legacy));

        assertEquals("TXFLOW_DIALECT_VERSION_UNSUPPORTED", failure.getCode());
    }

    @Test
    void h2DialectClassifiesLockTimeoutStateAndVendorCodeAsRetryable() {
        assertTrue(H2Dialect.INSTANCE.isRetryableTransactionFailure(
                new SQLException("lock timeout", "HYT00", 0)));
        assertTrue(H2Dialect.INSTANCE.isRetryableTransactionFailure(
                new SQLException("lock timeout", "HY000", 50200)));
        assertTrue(H2Dialect.INSTANCE.isRetryableTransactionFailure(
                new SQLException("deadlock", "40001", 40001)));
        assertFalse(H2Dialect.INSTANCE.isRetryableTransactionFailure(
                new SQLException("syntax", "42000", 42000)));
    }

    private Connection h2Connection(int majorVersion) throws Exception {
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        when(metadata.getDatabaseMajorVersion()).thenReturn(majorVersion);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metadata);
        return connection;
    }
}
