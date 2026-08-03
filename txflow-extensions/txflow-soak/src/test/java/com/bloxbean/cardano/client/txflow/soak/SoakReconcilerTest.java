package com.bloxbean.cardano.client.txflow.soak;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SoakReconcilerTest {

    @Test
    void storeRowCountsIgnoresUnsafeMetadataIdentifiers() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE txflow_event (id INTEGER)");
            statement.execute("INSERT INTO txflow_event VALUES (1), (2)");
            statement.execute("CREATE TABLE \"TXFLOW_A\"\"BROKEN\" (id INTEGER)");
        }

        Map<String, Long> counts = new SoakReconciler(null, jdbcUrl, 0).storeRowCounts();

        assertEquals(2L, counts.get("txflow_event"));
        assertFalse(counts.containsKey("txflow_a\"broken"));
    }
}
