package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.stream.TxStreamException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemRecord;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamPlannedRecord;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H2-specific behaviours: builder validation, no-secret persistence, lifecycle, and coexistence. */
class RdbmsTxStreamStateStoreTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private final List<RdbmsTxStreamStateStore> stores = new ArrayList<>();

    @AfterEach
    void closeStores() {
        stores.forEach(RdbmsTxStreamStateStore::close);
    }

    @Test
    void urlBuilderDetectsH2MigratesAndReportsDurable() {
        RdbmsTxStreamStateStore store = store();
        assertEquals(H2Dialect.INSTANCE, store.dialect());
        assertTrue(store.isDurable());
        store.registerItem(new TxStreamItemRecord("item-1", "key-1", "lane", "fp", NOW));
    }

    @Test
    void builderRequiresExactlyOneOfDataSourceOrJdbcUrl() {
        assertThrows(IllegalStateException.class,
                () -> RdbmsTxStreamStateStore.builder().build());
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> RdbmsTxStreamStateStore.builder()
                .dataSource(dataSource).jdbcUrl("jdbc:h2:mem:x").build());
    }

    @Test
    void builderRejectsPasswordWithoutUsername() {
        assertThrows(IllegalStateException.class, () -> RdbmsTxStreamStateStore.builder()
                .jdbcUrl("jdbc:h2:mem:" + UUID.randomUUID()).password("secret").build());
    }

    @Test
    void closeRejectsFurtherOperations() {
        RdbmsTxStreamStateStore store = store();
        store.close();
        assertTrue(store.isClosed());
        TxStreamException failure = assertThrows(TxStreamException.class,
                () -> store.registerItem(new TxStreamItemRecord("item", "k", "l", "f", NOW)));
        assertEquals("TXSTREAM_STORE_CLOSED", failure.getCode());
    }

    @Test
    void closingTheStoreNeverClosesAnApplicationDataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:app-ds-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        RdbmsTxStreamStateStore store = RdbmsTxStreamStateStore.builder()
                .dataSource(dataSource).schemaManagement(SchemaManagement.MIGRATE).build();
        store.close();
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void persistedPlannedRecordNeverContainsAResolvedSecret() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:no-secret-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        RdbmsTxStreamStateStore store = RdbmsTxStreamStateStore.builder()
                .dataSource(dataSource).schemaManagement(SchemaManagement.MIGRATE).build();
        stores.add(store);

        // The record only ever carries a secure-binding REFERENCE and a FINGERPRINT — never the
        // resolved secret. This sentinel value is deliberately never handed to the store.
        String secretValue = "SUPER_SECRET_SIGNING_KEY_VALUE";
        store.persistPlanned(new TxStreamPlannedRecord("payouts", "exec-1", "flow-key", "lane",
                "addr:sender", "{\"apiVersion\":\"v1alpha1\"}",
                Map.of("amount", 100L),
                Map.of("signingKey", "vault://payouts/key"),
                Map.of("signingKey", "fp-9f8e7d"),
                List.of(new TxStreamPlannedRecord.Member("item-1", "key-1", "step-1", "fp-1"))));

        // The reference and fingerprint round-trip; the resolved secret is nowhere in the row.
        TxStreamPlannedRecord stored = store.listPlanned("payouts").get(0);
        assertEquals("vault://payouts/key", stored.secureBindingReferences().get("signingKey"));
        assertEquals("fp-9f8e7d", stored.secureBindingFingerprints().get("signingKey"));

        String raw = dumpTable(dataSource, "txstream_planned");
        assertFalse(raw.contains(secretValue), "the resolved secret must never be persisted");
        assertTrue(raw.contains("vault://payouts/key"), "the opaque reference is persisted");
        assertTrue(raw.contains("fp-9f8e7d"), "the fingerprint is persisted");
    }

    @Test
    void streamStoreAndEngineStoreCoexistInOneDatabase() {
        String url = "jdbc:h2:mem:coexist-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);

        // Stream store migrates FIRST — the disjoint txstream_/txflow_ prefixes must let the
        // engine store migrate afterwards without seeing the stream tables (and vice versa).
        RdbmsTxStreamStateStore streamStore = RdbmsTxStreamStateStore.builder()
                .dataSource(dataSource).schemaManagement(SchemaManagement.MIGRATE).build();
        stores.add(streamStore);
        try (RdbmsFlowExecutionStore engineStore = RdbmsFlowExecutionStore.builder()
                .dataSource(dataSource).schemaManagement(SchemaManagement.MIGRATE).build()) {
            engineStore.createOrGet("tenant", "op",
                    new FlowExecutionSnapshot("exec-1", "def", "req",
                            FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of()));
            assertTrue(engineStore.get("exec-1").isPresent());

            streamStore.registerItem(new TxStreamItemRecord("item-1", "key-1", "lane", "fp", NOW));
            streamStore.projectItem(TxStreamItemResult.builder(
                    "payouts", "item-1", TxStreamItemStatus.PLANNED).updatedAt(NOW).build(), 1);
            assertTrue(streamStore.getItem("payouts", "item-1").isPresent());
        }
    }

    private RdbmsTxStreamStateStore store() {
        RdbmsTxStreamStateStore store = RdbmsTxStreamStateStore.builder()
                .jdbcUrl("jdbc:h2:mem:stream-" + UUID.randomUUID())
                .build();
        stores.add(store);
        return store;
    }

    private String dumpTable(JdbcDataSource dataSource, String table) throws Exception {
        StringBuilder dump = new StringBuilder();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM " + table)) {
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                for (int index = 1; index <= columns; index++) {
                    dump.append(rows.getString(index)).append('|');
                }
            }
        }
        return dump.toString();
    }
}
