package com.bloxbean.cardano.client.txflow.store.rdbms;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RdbmsSqlExceptionSanitizerTest {

    @Test
    void removesDriverMessagesWhileRetainingSqlClassification() {
        SQLException original = new SQLException(
                "connection failed: jdbc:postgresql://host/db?password=secret-value",
                "08006", 9001);

        SQLException sanitized = RdbmsSqlExceptionSanitizer.sanitize(original);

        assertNotSame(original, sanitized);
        assertEquals("08006", sanitized.getSQLState());
        assertEquals(9001, sanitized.getErrorCode());
        assertFalse(sanitized.toString().contains("secret-value"));
        assertFalse(sanitized.toString().contains("jdbc:postgresql"));
    }
}
