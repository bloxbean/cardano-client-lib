package com.bloxbean.cardano.client.txflow.store.rdbms;

import java.sql.SQLException;

/** Builds a credential-safe JDBC cause while retaining machine-useful classification. */
final class RdbmsSqlExceptionSanitizer {
    private RdbmsSqlExceptionSanitizer() {
    }

    static SQLException sanitize(SQLException failure) {
        String state = failure.getSQLState();
        String safeState = state != null ? state : "unknown";
        return new SQLException("JDBC failure [SQLState=" + safeState
                + ", vendorCode=" + failure.getErrorCode() + ']',
                state, failure.getErrorCode());
    }
}
