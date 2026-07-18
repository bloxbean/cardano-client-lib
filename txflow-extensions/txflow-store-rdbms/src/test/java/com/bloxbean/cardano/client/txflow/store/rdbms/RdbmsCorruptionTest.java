package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RdbmsCorruptionTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00.123456Z");

    @Test
    void snapshotTimestampDivergenceFailsClosed() throws Exception {
        String url = "jdbc:h2:mem:snapshot-time-" + UUID.randomUUID();
        try (RdbmsFlowExecutionStore store = store(url)) {
            store.createOrGet("tenant", "snapshot-time", snapshot("snapshot-time"));
            updateTimestamp(url,
                    "UPDATE txflow_execution SET updated_at = ? WHERE execution_id = ?",
                    NOW.plusSeconds(1), "snapshot-time");

            FlowStoreException failure = assertThrows(
                    FlowStoreException.class, () -> store.get("snapshot-time"));
            assertEquals("TXFLOW_STORE_CORRUPT", failure.getCode());
        }
    }

    @Test
    void eventTimestampDivergenceFailsClosed() throws Exception {
        String url = "jdbc:h2:mem:event-time-" + UUID.randomUUID();
        try (RdbmsFlowExecutionStore store = store(url)) {
            store.createOrGet("tenant", "event-time", snapshot("event-time"));
            ExecutionLease lease = store.acquireExecutionLease(
                    "event-time", "owner", NOW, Duration.ofMinutes(1));
            FlowEvent event = new FlowEvent(1, "event-time", FlowEventType.EXECUTION_STARTED,
                    NOW.plusNanos(789), null, null, Map.of());
            store.append("event-time", 0, MutationFence.executionOnly(lease), List.of(event),
                    current -> current.withState(
                            FlowExecutionState.RUNNING, NOW.plusNanos(789), Map.of()));
            updateTimestamp(url,
                    "UPDATE txflow_event SET event_time = ? WHERE execution_id = ?",
                    event.timestamp().plusSeconds(1), "event-time");

            FlowStoreException failure = assertThrows(FlowStoreException.class,
                    () -> store.readEvents("event-time", 0, 10));
            assertEquals("TXFLOW_STORE_CORRUPT", failure.getCode());
        }
    }

    @Test
    void relationalAndInnerPayloadVersionsMustMatch() throws Exception {
        String url = "jdbc:h2:mem:payload-version-" + UUID.randomUUID();
        try (RdbmsFlowExecutionStore store = store(url)) {
            store.createOrGet("tenant", "payload-version", snapshot("payload-version"));
            try (Connection connection = DriverManager.getConnection(url);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE txflow_execution SET data_payload = "
                        + "REPLACE(data_payload, '\"version\":1', '\"version\":2') "
                        + "WHERE execution_id = 'payload-version'");
            }

            FlowStoreException failure = assertThrows(
                    FlowStoreException.class, () -> store.get("payload-version"));

            assertEquals("TXFLOW_STORE_CORRUPT", failure.getCode());
            assertEquals("TXFLOW_STORE_CODEC_VERSION_MISMATCH",
                    ((FlowStoreException) failure.getCause()).getCode());
        }
    }

    private RdbmsFlowExecutionStore store(String url) {
        return RdbmsFlowExecutionStore.builder()
                .jdbcUrl(url)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC))
                .build();
    }

    private FlowExecutionSnapshot snapshot(String executionId) {
        return new FlowExecutionSnapshot(executionId, "definition", "request",
                FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of());
    }

    private void updateTimestamp(String url, String sql, Instant timestamp,
                                 String executionId) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(timestamp));
            statement.setString(2, executionId);
            statement.executeUpdate();
        }
    }
}
