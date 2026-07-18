package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.EventReadResult;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.FlowStoreTextPolicy;
import com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import com.bloxbean.cardano.client.txflow.store.ResourceLease;
import com.bloxbean.cardano.client.txflow.store.codec.FlowStoreCodec;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * Transactional JDBC implementation of {@link FlowExecutionStore}.
 *
 * <p>The implementation contains all common claim, journal, revision, lease, fencing, and
 * compaction semantics. Database-specific behavior is restricted to {@link TxFlowSqlDialect}.
 * Every compound mutation runs in one JDBC transaction and locks the rows that establish its
 * ordering. It never creates a thread, executor, scheduler, or connection pool.</p>
 *
 * <p>{@link FlowStoreCodec} is the durable payload boundary. Relational columns support locking
 * and queries, while the versioned payload retains the exact typed snapshot and event values
 * needed by recovery. Column and payload metadata are cross-checked on every read so corruption
 * fails closed. Timestamp cross-checks use microsecond precision, the common lossless precision
 * of the certified PostgreSQL profile. Top-level snapshot and event timestamps are truncated to
 * that precision before they are encoded, bound, or returned. Lease expiries are instead rounded
 * up to the next microsecond so every positive requested duration remains strictly after the
 * caller's acquisition or renewal time.</p>
 */
public final class RdbmsFlowExecutionStore implements FlowExecutionStore, AutoCloseable {
    private static final String EXECUTION_COLUMNS = "execution_id, definition_fingerprint, "
            + "request_fingerprint, execution_state, revision_no, last_sequence, "
            + "compacted_through, updated_at, data_format, data_version, data_payload";
    private static final int MAX_PAYLOAD_CHARACTERS = FlowStoreCodec.DEFAULT_MAX_PAYLOAD_BYTES;

    private final DataSource dataSource;
    private final TxFlowSqlDialect dialect;
    private final FlowStoreCodec codec;
    private final Clock clock;
    private final Connection ownedAnchorConnection;
    private final AtomicBoolean closed = new AtomicBoolean();

    private RdbmsFlowExecutionStore(DataSource dataSource, TxFlowSqlDialect dialect,
                                    FlowStoreCodec codec, Clock clock,
                                    Connection ownedAnchorConnection) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.codec = codec;
        this.clock = clock;
        this.ownedAnchorConnection = ownedAnchorConnection;
    }

    /**
     * Starts configuration of a relational store.
     *
     * @return new mutable builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the selected certified or application-supplied SQL dialect.
     *
     * @return store dialect
     */
    public TxFlowSqlDialect dialect() {
        return dialect;
    }

    /**
     * Reports whether this store has rejected further operations after close.
     *
     * @return {@code true} after {@link #close()}
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public IdempotencyClaimResult createOrGet(String namespace, String key,
                                               FlowExecutionSnapshot initialSnapshot) {
        requireText(namespace, "idempotency namespace",
                FlowStoreTextPolicy.MAX_NAMESPACE_BYTES);
        requireText(key, "idempotency key",
                FlowStoreTextPolicy.MAX_IDEMPOTENCY_KEY_BYTES);
        Objects.requireNonNull(initialSnapshot, "initialSnapshot");
        FlowExecutionSnapshot persistedInitial = normalizeSnapshotTimestamp(initialSnapshot);
        byte[] encoded = codec.encodeSnapshot(persistedInitial);
        try {
            return inTransaction("create idempotency claim", connection -> {
                String claimedExecution = findClaimedExecution(connection, namespace, key, true);
                if (claimedExecution != null) {
                    FlowExecutionSnapshot existing = requireSnapshot(connection,
                            claimedExecution, true);
                    verifyClaimFingerprints(existing, persistedInitial);
                    return new IdempotencyClaimResult(existing, false);
                }
                FlowExecutionSnapshot executionWithRequestedId = readSnapshot(
                        connection, persistedInitial.executionId(), true);
                if (executionWithRequestedId != null) {
                    // A concurrent transaction may have committed the execution and its claim
                    // after our first absent-claim read. Claims are immutable, so this second
                    // read does not need a lock (and avoids reversing the normal claim->execution
                    // lock order). If it is still absent, the execution belongs to another tuple.
                    String concurrentlyClaimedExecution = findClaimedExecution(
                            connection, namespace, key, false);
                    if (concurrentlyClaimedExecution != null) {
                        FlowExecutionSnapshot existing = concurrentlyClaimedExecution.equals(
                                executionWithRequestedId.executionId())
                                ? executionWithRequestedId
                                : requireSnapshot(connection, concurrentlyClaimedExecution, true);
                        verifyClaimFingerprints(existing, persistedInitial);
                        return new IdempotencyClaimResult(existing, false);
                    }
                    throw new FlowStoreException("TXFLOW_EXECUTION_ID_CONFLICT",
                            "Execution ID already exists");
                }
                insertSnapshot(connection, persistedInitial, encoded);
                insertClaim(connection, namespace, key, persistedInitial.executionId());
                return new IdempotencyClaimResult(persistedInitial, true);
            });
        } catch (FlowStoreException failure) {
            if (!"TXFLOW_RDBMS_UNIQUE_CONFLICT".equals(failure.getCode())) throw failure;
            // Certified schemas use immediate unique constraints. The failed transaction was
            // rolled back before this read, so resolving the winning claim is safe and bounded.
            return resolveConcurrentClaim(namespace, key, persistedInitial);
        }
    }

    @Override
    public Optional<FlowExecutionSnapshot> get(String executionId) {
        requireText(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        return inTransaction("read execution", connection ->
                Optional.ofNullable(readSnapshot(connection, executionId, false)));
    }

    @Override
    public FlowExecutionSnapshot append(String executionId, long expectedRevision,
                                        MutationFence fence, List<FlowEvent> events,
                                        UnaryOperator<FlowExecutionSnapshot> mutation) {
        requireText(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision cannot be negative");
        }
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(mutation, "mutation");
        return inTransaction("append execution journal", connection -> {
            FlowExecutionSnapshot current = requireSnapshot(connection, executionId, true);
            if (current.revision() != expectedRevision) {
                throw new FlowStoreException("TXFLOW_REVISION_CONFLICT",
                        "Snapshot revision changed");
            }
            validateFence(connection, executionId, fence);

            long nextSequence = current.lastSequence();
            List<EncodedEvent> encodedEvents = new ArrayList<>(events.size());
            for (FlowEvent event : events) {
                Objects.requireNonNull(event, "event");
                if (!executionId.equals(event.executionId())
                        || event.sequence() != nextSequence + 1) {
                    throw new FlowStoreException("TXFLOW_EVENT_SEQUENCE",
                            "Events must name the execution and remain contiguous");
                }
                FlowEvent persistedEvent = normalizeEventTimestamp(event);
                encodedEvents.add(new EncodedEvent(
                        persistedEvent, codec.encodeEvent(persistedEvent)));
                nextSequence = event.sequence();
            }

            FlowExecutionSnapshot proposed = Objects.requireNonNull(
                    mutation.apply(current), "mutation result");
            FlowExecutionSnapshot committed = new FlowExecutionSnapshot(
                    current.executionId(), current.definitionFingerprint(),
                    current.requestFingerprint(), proposed.state(), current.revision() + 1,
                    nextSequence, current.compactedThroughSequence(), proposed.updatedAt(),
                    proposed.data());
            committed = normalizeSnapshotTimestamp(committed);
            byte[] encodedSnapshot = codec.encodeSnapshot(committed);
            insertEvents(connection, encodedEvents);
            updateSnapshot(connection, committed, current.revision(), encodedSnapshot);
            return committed;
        });
    }

    @Override
    public EventReadResult readEvents(String executionId, long afterSequence, int limit) {
        requireText(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        if (afterSequence < 0 || limit < 1) {
            throw new IllegalArgumentException(
                    "event cursor must be non-negative and limit positive");
        }
        return inTransaction("read execution events", connection -> {
            FlowExecutionSnapshot snapshot = requireSnapshot(connection, executionId, true);
            if (afterSequence < snapshot.compactedThroughSequence()) {
                throw new FlowStoreException("EVENTS_COMPACTED",
                        "Requested event cursor has been compacted");
            }
            List<FlowEvent> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    dialect.eventPageSql("txflow_event"))) {
                statement.setString(1, executionId);
                statement.setLong(2, afterSequence);
                statement.setInt(3, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    long expected = afterSequence + 1;
                    while (rows.next()) {
                        FlowEvent event = decodeEventRow(rows, executionId);
                        if (event.sequence() != expected) {
                            throw corrupt("Execution journal contains a sequence gap");
                        }
                        events.add(event);
                        expected++;
                    }
                }
            }
            if (events.isEmpty() && afterSequence < snapshot.lastSequence()) {
                throw corrupt("Execution journal is missing its retained tail");
            }
            long next = events.isEmpty()
                    ? afterSequence : events.get(events.size() - 1).sequence();
            return new EventReadResult(events, next);
        });
    }

    @Override
    public ExecutionLease acquireExecutionLease(String executionId, String ownerToken,
                                                 Instant now, Duration duration) {
        requireText(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        requireText(ownerToken, "ownerToken",
                FlowStoreTextPolicy.MAX_OWNER_TOKEN_BYTES);
        validateLeaseTime(now, duration);
        return inTransaction("acquire execution lease", connection -> {
            long epoch = nextLeaseEpoch(connection);
            requireExecutionExists(connection, executionId);
            ExecutionLease current = readExecutionLease(connection, executionId, true);
            if (current != null && current.expiresAt().isAfter(now)
                    && !current.ownerToken().equals(ownerToken)) {
                throw new FlowStoreException("TXFLOW_LEASE_CONFLICT",
                        "Execution is leased by another owner");
            }
            ExecutionLease acquired = new ExecutionLease(
                    executionId, ownerToken, epoch, normalizeLeaseExpiry(now, duration));
            if (current == null) insertExecutionLease(connection, acquired);
            else updateExecutionLease(connection, acquired);
            return acquired;
        });
    }

    @Override
    public ExecutionLease renewExecutionLease(ExecutionLease lease, Instant now,
                                               Duration duration) {
        Objects.requireNonNull(lease, "lease");
        validateLeaseTime(now, duration);
        return inTransaction("renew execution lease", connection -> {
            ExecutionLease current = requireCurrentExecutionLease(connection, lease);
            if (!current.expiresAt().isAfter(now)) {
                throw new FlowStoreException("TXFLOW_LEASE_EXPIRED",
                        "Execution lease has expired");
            }
            ExecutionLease renewed = new ExecutionLease(current.executionId(),
                    current.ownerToken(), current.epoch(),
                    normalizeLeaseExpiry(now, duration));
            updateExecutionLease(connection, renewed);
            return renewed;
        });
    }

    @Override
    public void releaseExecutionLease(ExecutionLease lease) {
        Objects.requireNonNull(lease, "lease");
        inTransaction("release execution lease", connection -> {
            requireCurrentExecutionLease(connection, lease);
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM txflow_execution_lease WHERE execution_id = ?")) {
                statement.setString(1, lease.executionId());
                if (statement.executeUpdate() != 1) throw staleExecutionFence();
            }
            return null;
        });
    }

    @Override
    public ResourceLease acquireResourceLease(String resourceId, String executionId,
                                               String ownerToken, Instant now,
                                               Duration duration) {
        requireText(resourceId, "resourceId",
                FlowStoreTextPolicy.MAX_RESOURCE_ID_BYTES);
        requireText(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        requireText(ownerToken, "ownerToken",
                FlowStoreTextPolicy.MAX_OWNER_TOKEN_BYTES);
        validateLeaseTime(now, duration);
        return inTransaction("acquire resource lease", connection -> {
            // The epoch singleton serializes even the first acquisition of a previously unseen
            // resource, for which SQL row locks cannot lock an absent row at READ COMMITTED.
            long epoch = nextLeaseEpoch(connection);
            requireExecutionExists(connection, executionId);
            ResourceLease current = readResourceLease(connection, resourceId, true);
            if (current != null && current.expiresAt().isAfter(now)
                    && (!current.executionId().equals(executionId)
                    || !current.ownerToken().equals(ownerToken))) {
                throw new FlowStoreException("TXFLOW_RESOURCE_LEASE_CONFLICT",
                        "Resource is already leased by another execution owner");
            }
            ResourceLease acquired = new ResourceLease(resourceId, executionId,
                    ownerToken, epoch, normalizeLeaseExpiry(now, duration));
            if (current == null) insertResourceLease(connection, acquired);
            else updateResourceLease(connection, acquired);
            return acquired;
        });
    }

    @Override
    public ResourceLease renewResourceLease(ResourceLease lease, Instant now,
                                             Duration duration) {
        Objects.requireNonNull(lease, "lease");
        validateLeaseTime(now, duration);
        return inTransaction("renew resource lease", connection -> {
            ResourceLease current = requireCurrentResourceLease(connection, lease);
            if (!current.expiresAt().isAfter(now)) {
                throw new FlowStoreException("TXFLOW_RESOURCE_LEASE_EXPIRED",
                        "Resource lease has expired");
            }
            ResourceLease renewed = new ResourceLease(current.resourceId(),
                    current.executionId(), current.ownerToken(), current.epoch(),
                    normalizeLeaseExpiry(now, duration));
            updateResourceLease(connection, renewed);
            return renewed;
        });
    }

    @Override
    public void releaseResourceLease(ResourceLease lease) {
        Objects.requireNonNull(lease, "lease");
        inTransaction("release resource lease", connection -> {
            requireCurrentResourceLease(connection, lease);
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM txflow_resource_lease WHERE resource_id = ?")) {
                statement.setString(1, lease.resourceId());
                if (statement.executeUpdate() != 1) throw staleResourceFence();
            }
            return null;
        });
    }

    @Override
    public void compactEvents(String executionId, long throughSequence) {
        requireText(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        if (throughSequence < 0) {
            throw new IllegalArgumentException("Compaction sequence cannot be negative");
        }
        inTransaction("compact execution events", connection -> {
            FlowExecutionSnapshot current = requireSnapshot(connection, executionId, true);
            requireTerminalForCompaction(current.state());
            if (throughSequence > current.lastSequence()) {
                throw new IllegalArgumentException("Cannot compact beyond the journal tail");
            }
            if (throughSequence <= current.compactedThroughSequence()) return null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM txflow_event WHERE execution_id = ? AND sequence_no <= ?")) {
                statement.setString(1, executionId);
                statement.setLong(2, throughSequence);
                statement.executeUpdate();
            }
            FlowExecutionSnapshot compacted = new FlowExecutionSnapshot(
                    current.executionId(), current.definitionFingerprint(),
                    current.requestFingerprint(), current.state(), current.revision() + 1,
                    current.lastSequence(), throughSequence, current.updatedAt(), current.data());
            compacted = normalizeSnapshotTimestamp(compacted);
            updateSnapshot(connection, compacted, current.revision(),
                    codec.encodeSnapshot(compacted));
            return null;
        });
    }

    /**
     * Closes the store-created H2 anchor connection, when present, and rejects new calls.
     * Application-supplied data sources are never closed.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (ownedAnchorConnection != null) {
            try {
                ownedAnchorConnection.close();
            } catch (SQLException failure) {
                throw new FlowStoreException("TXFLOW_STORE_CLOSE_FAILED",
                        "Relational TxFlow store could not close its embedded resource",
                        RdbmsSqlExceptionSanitizer.sanitize(failure));
            }
        }
    }

    private IdempotencyClaimResult resolveConcurrentClaim(
            String namespace, String key, FlowExecutionSnapshot initial) {
        return inTransaction("resolve concurrent idempotency claim", connection -> {
            String claimedExecution = findClaimedExecution(connection, namespace, key, true);
            if (claimedExecution != null) {
                FlowExecutionSnapshot existing = requireSnapshot(
                        connection, claimedExecution, true);
                verifyClaimFingerprints(existing, initial);
                return new IdempotencyClaimResult(existing, false);
            }
            if (readSnapshot(connection, initial.executionId(), true) != null) {
                throw new FlowStoreException("TXFLOW_EXECUTION_ID_CONFLICT",
                        "Execution ID already exists");
            }
            throw new FlowStoreException("TXFLOW_STORE_OPERATION_FAILED",
                    "Concurrent idempotency claim could not be resolved");
        });
    }

    private String findClaimedExecution(Connection connection, String namespace, String key,
                                        boolean lock) throws SQLException {
        String sql = "SELECT execution_id FROM txflow_idempotency "
                + "WHERE namespace_id = ? AND claim_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(
                lock ? dialect.forUpdate(sql) : sql)) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private void insertClaim(Connection connection, String namespace, String key,
                             String executionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txflow_idempotency "
                        + "(namespace_id, claim_key, execution_id) VALUES (?, ?, ?)")) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.setString(3, executionId);
            statement.executeUpdate();
        }
    }

    private void verifyClaimFingerprints(FlowExecutionSnapshot existing,
                                         FlowExecutionSnapshot requested) {
        if (!existing.definitionFingerprint().equals(requested.definitionFingerprint())
                || !existing.requestFingerprint().equals(requested.requestFingerprint())) {
            throw new FlowStoreException("TXFLOW_IDEMPOTENCY_CONFLICT",
                    "Idempotency claim fingerprints do not match");
        }
    }

    private void insertSnapshot(Connection connection, FlowExecutionSnapshot snapshot,
                                byte[] encoded) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txflow_execution (execution_id, definition_fingerprint, "
                        + "request_fingerprint, execution_state, revision_no, last_sequence, "
                        + "compacted_through, updated_at, data_format, data_version, data_payload) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindSnapshot(statement, snapshot, encoded, 1);
            statement.executeUpdate();
        }
    }

    private void updateSnapshot(Connection connection, FlowExecutionSnapshot snapshot,
                                long expectedRevision, byte[] encoded) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE txflow_execution SET execution_state = ?, revision_no = ?, "
                        + "last_sequence = ?, compacted_through = ?, updated_at = ?, "
                        + "data_format = ?, data_version = ?, data_payload = ? "
                        + "WHERE execution_id = ? AND revision_no = ?")) {
            statement.setString(1, snapshot.state().name());
            statement.setLong(2, snapshot.revision());
            statement.setLong(3, snapshot.lastSequence());
            statement.setLong(4, snapshot.compactedThroughSequence());
            statement.setTimestamp(5, Timestamp.from(snapshot.updatedAt()));
            statement.setString(6, FlowStoreCodec.FORMAT_ID);
            statement.setInt(7, FlowStoreCodec.CURRENT_FORMAT_VERSION);
            statement.setString(8, utf8(encoded));
            statement.setString(9, snapshot.executionId());
            statement.setLong(10, expectedRevision);
            if (statement.executeUpdate() != 1) {
                throw new FlowStoreException("TXFLOW_REVISION_CONFLICT",
                        "Snapshot revision changed");
            }
        }
    }

    private void bindSnapshot(PreparedStatement statement, FlowExecutionSnapshot snapshot,
                              byte[] encoded, int offset) throws SQLException {
        statement.setString(offset, snapshot.executionId());
        statement.setString(offset + 1, snapshot.definitionFingerprint());
        statement.setString(offset + 2, snapshot.requestFingerprint());
        statement.setString(offset + 3, snapshot.state().name());
        statement.setLong(offset + 4, snapshot.revision());
        statement.setLong(offset + 5, snapshot.lastSequence());
        statement.setLong(offset + 6, snapshot.compactedThroughSequence());
        statement.setTimestamp(offset + 7, Timestamp.from(snapshot.updatedAt()));
        statement.setString(offset + 8, FlowStoreCodec.FORMAT_ID);
        statement.setInt(offset + 9, FlowStoreCodec.CURRENT_FORMAT_VERSION);
        statement.setString(offset + 10, utf8(encoded));
    }

    private FlowExecutionSnapshot readSnapshot(Connection connection, String executionId,
                                               boolean lock) throws SQLException {
        String sql = "SELECT " + EXECUTION_COLUMNS
                + " FROM txflow_execution WHERE execution_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(
                lock ? dialect.forUpdate(sql) : sql)) {
            statement.setString(1, executionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                String format = row.getString("data_format");
                int version = row.getInt("data_version");
                verifyPayloadEnvelope(format, version);
                FlowExecutionSnapshot snapshot;
                try {
                    snapshot = codec.decodeSnapshot(
                            readPayload(row, "data_payload"), version);
                } catch (FlowStoreException failure) {
                    throw mapEnvelopeMismatch(failure);
                }
                verifySnapshotColumns(row, snapshot);
                return snapshot;
            }
        }
    }

    private FlowExecutionSnapshot requireSnapshot(Connection connection, String executionId,
                                                  boolean lock) throws SQLException {
        FlowExecutionSnapshot snapshot = readSnapshot(connection, executionId, lock);
        if (snapshot == null) {
            throw new FlowStoreException("TXFLOW_EXECUTION_NOT_FOUND",
                    "Durable execution does not exist");
        }
        return snapshot;
    }

    private void verifySnapshotColumns(ResultSet row, FlowExecutionSnapshot snapshot)
            throws SQLException {
        if (!snapshot.executionId().equals(row.getString("execution_id"))
                || !snapshot.definitionFingerprint().equals(
                row.getString("definition_fingerprint"))
                || !snapshot.requestFingerprint().equals(row.getString("request_fingerprint"))
                || !snapshot.state().name().equals(row.getString("execution_state"))
                || snapshot.revision() != row.getLong("revision_no")
                || snapshot.lastSequence() != row.getLong("last_sequence")
                || snapshot.compactedThroughSequence() != row.getLong("compacted_through")
                || !sameDatabaseTimestamp(
                snapshot.updatedAt(), row.getTimestamp("updated_at"))) {
            throw corrupt("Execution columns do not match the durable snapshot payload");
        }
    }

    private void insertEvents(Connection connection, List<EncodedEvent> events)
            throws SQLException {
        if (events.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txflow_event (execution_id, sequence_no, event_type, event_time, "
                        + "step_id, transaction_hash, details_format, details_version, "
                        + "details_payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (EncodedEvent encoded : events) {
                FlowEvent event = encoded.event();
                statement.setString(1, event.executionId());
                statement.setLong(2, event.sequence());
                statement.setString(3, event.type().name());
                statement.setTimestamp(4, Timestamp.from(event.timestamp()));
                statement.setString(5, event.stepId());
                statement.setString(6, event.transactionHash());
                statement.setString(7, FlowStoreCodec.FORMAT_ID);
                statement.setInt(8, FlowStoreCodec.CURRENT_FORMAT_VERSION);
                statement.setString(9, utf8(encoded.payload()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private FlowEvent decodeEventRow(ResultSet row, String expectedExecutionId)
            throws SQLException {
        int version = row.getInt("details_version");
        verifyPayloadEnvelope(row.getString("details_format"), version);
        FlowEvent event;
        try {
            event = codec.decodeEvent(readPayload(row, "details_payload"), version);
        } catch (FlowStoreException failure) {
            throw mapEnvelopeMismatch(failure);
        }
        if (!expectedExecutionId.equals(event.executionId())
                || event.sequence() != row.getLong("sequence_no")
                || !event.type().name().equals(row.getString("event_type"))
                || !sameDatabaseTimestamp(
                event.timestamp(), row.getTimestamp("event_time"))
                || !Objects.equals(event.stepId(), row.getString("step_id"))
                || !Objects.equals(event.transactionHash(), row.getString("transaction_hash"))) {
            throw corrupt("Event columns do not match the durable event payload");
        }
        return event;
    }

    private void verifyPayloadEnvelope(String format, int version) {
        if (!FlowStoreCodec.FORMAT_ID.equals(format)
                || !FlowStoreCodec.supportsFormatVersion(version)) {
            throw new FlowStoreException("TXFLOW_STORE_CODEC_UNSUPPORTED_VERSION",
                    "Relational payload metadata is unsupported");
        }
    }

    private FlowStoreException mapEnvelopeMismatch(FlowStoreException failure) {
        if (!"TXFLOW_STORE_CODEC_VERSION_MISMATCH".equals(failure.getCode())) return failure;
        return new FlowStoreException("TXFLOW_STORE_CORRUPT",
                "Relational payload version does not match its inner envelope", failure);
    }

    private byte[] readPayload(ResultSet row, String column) throws SQLException {
        try (Reader reader = row.getCharacterStream(column)) {
            if (reader == null) throw corrupt("Durable payload is missing");
            StringBuilder payload = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (payload.length() + count > MAX_PAYLOAD_CHARACTERS) {
                    throw new FlowStoreException("TXFLOW_STORE_CODEC_SIZE_LIMIT",
                            "Relational store payload exceeds the configured limit");
                }
                payload.append(buffer, 0, count);
            }
            return payload.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new FlowStoreException("TXFLOW_STORE_CODEC_DECODE_FAILED",
                    "Relational store payload could not be read", failure);
        }
    }

    private void validateFence(Connection connection, String executionId,
                               MutationFence fence) throws SQLException {
        if (fence == null || fence.executionLease() == null) {
            throw new FlowStoreException("TXFLOW_FENCE_REQUIRED",
                    "Mutation requires an execution lease");
        }
        ExecutionLease supplied = fence.executionLease();
        if (!executionId.equals(supplied.executionId())) throw staleExecutionFence();
        ExecutionLease current = requireCurrentExecutionLease(connection, supplied);

        List<ResourceLease> resources = new ArrayList<>(fence.resourceLeases());
        resources.sort(Comparator.comparing(ResourceLease::resourceId));
        List<ResourceLease> currentResources = new ArrayList<>(resources.size());
        for (ResourceLease suppliedResource : resources) {
            ResourceLease currentResource = requireCurrentResourceLease(
                    connection, suppliedResource);
            if (!executionId.equals(currentResource.executionId())
                    || !current.ownerToken().equals(currentResource.ownerToken())) {
                throw staleResourceFence();
            }
            currentResources.add(currentResource);
        }

        // Lease expiry is linearized only after every fence row is locked. Sampling before a
        // blocked row lock could otherwise authorize a lease that expired during the wait.
        Instant now = clock.instant();
        if (!current.expiresAt().isAfter(now)) {
            throw new FlowStoreException("TXFLOW_LEASE_EXPIRED",
                    "Execution lease has expired");
        }
        for (ResourceLease currentResource : currentResources) {
            if (!currentResource.expiresAt().isAfter(now)) {
                throw new FlowStoreException("TXFLOW_RESOURCE_LEASE_EXPIRED",
                        "Resource lease has expired");
            }
        }
    }

    private boolean sameDatabaseTimestamp(Instant expected, Timestamp actual) {
        return actual != null
                && expected.truncatedTo(ChronoUnit.MICROS).equals(
                actual.toInstant().truncatedTo(ChronoUnit.MICROS));
    }

    private FlowExecutionSnapshot normalizeSnapshotTimestamp(
            FlowExecutionSnapshot snapshot) {
        Instant normalized = normalizeTimestamp(snapshot.updatedAt());
        if (normalized.equals(snapshot.updatedAt())) return snapshot;
        return new FlowExecutionSnapshot(
                snapshot.executionId(), snapshot.definitionFingerprint(),
                snapshot.requestFingerprint(), snapshot.state(), snapshot.revision(),
                snapshot.lastSequence(), snapshot.compactedThroughSequence(), normalized,
                snapshot.data());
    }

    private FlowEvent normalizeEventTimestamp(FlowEvent event) {
        Instant normalized = normalizeTimestamp(event.timestamp());
        if (normalized.equals(event.timestamp())) return event;
        return new FlowEvent(event.sequence(), event.executionId(), event.type(), normalized,
                event.stepId(), event.transactionHash(), event.details());
    }

    private Instant normalizeTimestamp(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    private Instant normalizeLeaseExpiry(Instant now, Duration duration) {
        Instant requestedExpiry = now.plus(duration);
        Instant normalized = normalizeTimestamp(requestedExpiry);
        if (!normalized.equals(requestedExpiry)) {
            normalized = normalized.plus(1, ChronoUnit.MICROS);
        }
        if (!normalized.isAfter(now)) {
            throw new IllegalArgumentException(
                    "lease duration is below the relational timestamp precision");
        }
        return normalized;
    }

    private long nextLeaseEpoch(Connection connection) throws SQLException {
        String sql = dialect.forUpdate(
                "SELECT last_epoch FROM txflow_lease_epoch WHERE singleton_id = 1");
        long current;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet row = statement.executeQuery()) {
            if (!row.next()) throw corrupt("Lease epoch singleton is missing");
            current = row.getLong(1);
        }
        if (current < 0) throw corrupt("Lease fencing epoch is negative");
        if (current == Long.MAX_VALUE) {
            throw new FlowStoreException("TXFLOW_LEASE_EPOCH_EXHAUSTED",
                    "Lease fencing epoch is exhausted");
        }
        long next = current + 1;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE txflow_lease_epoch SET last_epoch = ? "
                        + "WHERE singleton_id = 1 AND last_epoch = ?")) {
            statement.setLong(1, next);
            statement.setLong(2, current);
            if (statement.executeUpdate() != 1) {
                throw new FlowStoreException("TXFLOW_STORE_SERIALIZATION_FAILURE",
                        "Lease epoch changed concurrently");
            }
        }
        return next;
    }

    private ExecutionLease readExecutionLease(Connection connection, String executionId,
                                              boolean lock) throws SQLException {
        String sql = "SELECT execution_id, owner_token, fence_epoch, expires_at "
                + "FROM txflow_execution_lease WHERE execution_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(
                lock ? dialect.forUpdate(sql) : sql)) {
            statement.setString(1, executionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                return new ExecutionLease(row.getString(1), row.getString(2), row.getLong(3),
                        row.getTimestamp(4).toInstant());
            }
        }
    }

    private ExecutionLease requireCurrentExecutionLease(Connection connection,
                                                        ExecutionLease supplied)
            throws SQLException {
        ExecutionLease current = readExecutionLease(connection, supplied.executionId(), true);
        if (current == null || current.epoch() != supplied.epoch()
                || !current.ownerToken().equals(supplied.ownerToken())) {
            throw staleExecutionFence();
        }
        return current;
    }

    private void insertExecutionLease(Connection connection, ExecutionLease lease)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txflow_execution_lease "
                        + "(execution_id, owner_token, fence_epoch, expires_at) "
                        + "VALUES (?, ?, ?, ?)")) {
            bindExecutionLease(statement, lease);
            statement.executeUpdate();
        }
    }

    private void updateExecutionLease(Connection connection, ExecutionLease lease)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE txflow_execution_lease SET owner_token = ?, fence_epoch = ?, "
                        + "expires_at = ? WHERE execution_id = ?")) {
            statement.setString(1, lease.ownerToken());
            statement.setLong(2, lease.epoch());
            statement.setTimestamp(3, Timestamp.from(lease.expiresAt()));
            statement.setString(4, lease.executionId());
            if (statement.executeUpdate() != 1) throw staleExecutionFence();
        }
    }

    private void bindExecutionLease(PreparedStatement statement, ExecutionLease lease)
            throws SQLException {
        statement.setString(1, lease.executionId());
        statement.setString(2, lease.ownerToken());
        statement.setLong(3, lease.epoch());
        statement.setTimestamp(4, Timestamp.from(lease.expiresAt()));
    }

    private ResourceLease readResourceLease(Connection connection, String resourceId,
                                            boolean lock) throws SQLException {
        String sql = "SELECT resource_id, execution_id, owner_token, fence_epoch, expires_at "
                + "FROM txflow_resource_lease WHERE resource_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(
                lock ? dialect.forUpdate(sql) : sql)) {
            statement.setString(1, resourceId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                return new ResourceLease(row.getString(1), row.getString(2), row.getString(3),
                        row.getLong(4), row.getTimestamp(5).toInstant());
            }
        }
    }

    private ResourceLease requireCurrentResourceLease(Connection connection,
                                                      ResourceLease supplied)
            throws SQLException {
        ResourceLease current = readResourceLease(connection, supplied.resourceId(), true);
        if (current == null || current.epoch() != supplied.epoch()
                || !current.executionId().equals(supplied.executionId())
                || !current.ownerToken().equals(supplied.ownerToken())) {
            throw staleResourceFence();
        }
        return current;
    }

    private void insertResourceLease(Connection connection, ResourceLease lease)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txflow_resource_lease "
                        + "(resource_id, execution_id, owner_token, fence_epoch, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            bindResourceLease(statement, lease);
            statement.executeUpdate();
        }
    }

    private void updateResourceLease(Connection connection, ResourceLease lease)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE txflow_resource_lease SET execution_id = ?, owner_token = ?, "
                        + "fence_epoch = ?, expires_at = ? WHERE resource_id = ?")) {
            statement.setString(1, lease.executionId());
            statement.setString(2, lease.ownerToken());
            statement.setLong(3, lease.epoch());
            statement.setTimestamp(4, Timestamp.from(lease.expiresAt()));
            statement.setString(5, lease.resourceId());
            if (statement.executeUpdate() != 1) throw staleResourceFence();
        }
    }

    private void bindResourceLease(PreparedStatement statement, ResourceLease lease)
            throws SQLException {
        statement.setString(1, lease.resourceId());
        statement.setString(2, lease.executionId());
        statement.setString(3, lease.ownerToken());
        statement.setLong(4, lease.epoch());
        statement.setTimestamp(5, Timestamp.from(lease.expiresAt()));
    }

    private void requireExecutionExists(Connection connection, String executionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT execution_id FROM txflow_execution WHERE execution_id = ?")) {
            statement.setString(1, executionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new FlowStoreException("TXFLOW_EXECUTION_NOT_FOUND",
                            "Durable execution does not exist");
                }
            }
        }
    }

    private void requireTerminalForCompaction(FlowExecutionState state) {
        switch (state) {
            case COMPLETED:
            case PARTIALLY_COMPLETED:
            case FAILED:
            case ROLLED_BACK:
            case CANCELLED:
                return;
            default:
                throw new FlowStoreException("TXFLOW_COMPACTION_NOT_TERMINAL",
                        "Only terminal executions may be compacted");
        }
    }

    private <T> T inTransaction(String operation, SqlWork<T> work) {
        ensureOpen();
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException failure) {
            throw mapSqlFailure(operation, failure);
        }

        boolean originalAutoCommit;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            closeAfterFailure(connection, failure);
            throw mapSqlFailure(operation, failure);
        }

        T result;
        try {
            result = work.apply(connection);
        } catch (FlowStoreException failure) {
            rollbackOrThrow(connection, operation, failure);
            restoreAutoCommitAfterFailure(connection, originalAutoCommit, failure);
            closeAfterFailure(connection, failure);
            throw failure;
        } catch (SQLException failure) {
            rollbackOrThrow(connection, operation, failure);
            restoreAutoCommitAfterFailure(connection, originalAutoCommit, failure);
            closeAfterFailure(connection, failure);
            throw mapSqlFailure(operation, failure);
        } catch (RuntimeException | Error failure) {
            rollbackOrThrow(connection, operation, failure);
            restoreAutoCommitAfterFailure(connection, originalAutoCommit, failure);
            closeAfterFailure(connection, failure);
            throw failure;
        }

        try {
            connection.commit();
        } catch (SQLException failure) {
            FlowStoreException uncertain = new FlowStoreException(
                    "TXFLOW_STORE_COMMIT_UNCERTAIN",
                    "Relational TxFlow transaction commit outcome is uncertain during "
                            + operation,
                    RdbmsSqlExceptionSanitizer.sanitize(failure));
            // A failed commit call does not reveal whether the database committed. Rolling back
            // or changing auto-commit here can create a second, contradictory outcome.
            closeAfterFailure(connection, uncertain);
            throw uncertain;
        }

        restoreAutoCommitAfterSuccess(connection, originalAutoCommit);
        closeAfterSuccess(connection);
        return result;
    }

    private void rollbackOrThrow(Connection connection, String operation, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            FlowStoreException uncertain = new FlowStoreException(
                    "TXFLOW_STORE_ROLLBACK_UNCERTAIN",
                    "Relational TxFlow transaction rollback outcome is uncertain during "
                            + operation,
                    RdbmsSqlExceptionSanitizer.sanitize(rollbackFailure));
            uncertain.addSuppressed(sanitizeOriginalFailure(original));
            // Restoring auto-commit after a failed rollback may commit work whose outcome is
            // unknown. Dispose of the connection immediately and force caller reconciliation.
            closeAfterFailure(connection, uncertain);
            throw uncertain;
        }
    }

    private Throwable sanitizeOriginalFailure(Throwable original) {
        return original instanceof SQLException sqlFailure
                ? RdbmsSqlExceptionSanitizer.sanitize(sqlFailure)
                : original;
    }

    private void restoreAutoCommitAfterFailure(Connection connection, boolean autoCommit,
                                               Throwable original) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException restoreFailure) {
            original.addSuppressed(RdbmsSqlExceptionSanitizer.sanitize(restoreFailure));
        }
    }

    private void closeAfterFailure(Connection connection, Throwable original) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            original.addSuppressed(RdbmsSqlExceptionSanitizer.sanitize(closeFailure));
        }
    }

    private void restoreAutoCommitAfterSuccess(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // The commit is authoritative. A cleanup failure cannot turn it into a reported
            // operation failure; closing lets a pool discard or reset the connection.
        }
    }

    private void closeAfterSuccess(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // The operation has committed. Connection disposal is best-effort at this boundary.
        }
    }

    private FlowStoreException mapSqlFailure(String operation, SQLException failure) {
        SQLException safeCause = RdbmsSqlExceptionSanitizer.sanitize(failure);
        if (dialect.isUniqueConstraintViolation(failure)) {
            return new FlowStoreException("TXFLOW_RDBMS_UNIQUE_CONFLICT",
                    "Relational TxFlow store observed a concurrent unique claim", safeCause);
        }
        String state = failure.getSQLState();
        if (state != null && (state.startsWith("08"))) {
            return new FlowStoreException("TXFLOW_STORE_UNAVAILABLE",
                    "Relational TxFlow store is unavailable during " + operation, safeCause);
        }
        if (dialect.isRetryableTransactionFailure(failure)) {
            return new FlowStoreException("TXFLOW_STORE_SERIALIZATION_FAILURE",
                    "Relational TxFlow transaction was concurrently invalidated during "
                            + operation, safeCause);
        }
        return new FlowStoreException("TXFLOW_STORE_OPERATION_FAILED",
                "Relational TxFlow store operation failed during " + operation, safeCause);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new FlowStoreException("TXFLOW_STORE_CLOSED",
                    "Relational TxFlow store is closed");
        }
    }

    private void validateLeaseTime(Instant now, Duration duration) {
        if (now == null || duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    "lease time and positive duration are required");
        }
    }

    private void requireText(String value, String name, int maxUtf8Bytes) {
        FlowStoreTextPolicy.requireIdentifier(value, name, maxUtf8Bytes);
    }

    private FlowStoreException staleExecutionFence() {
        return new FlowStoreException("TXFLOW_STALE_FENCE",
                "Execution lease fence is stale");
    }

    private FlowStoreException staleResourceFence() {
        return new FlowStoreException("TXFLOW_STALE_RESOURCE_FENCE",
                "Resource lease fence is stale");
    }

    private FlowStoreException corrupt(String message) {
        return new FlowStoreException("TXFLOW_STORE_CORRUPT", message);
    }

    private String utf8(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private record EncodedEvent(FlowEvent event, byte[] payload) {
    }

    /** Builder for data-source ownership, dialect, schema, and deterministic clock settings. */
    public static final class Builder {
        private DataSource dataSource;
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;
        private TxFlowSqlDialect dialect;
        private SchemaManagement schemaManagement;
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        /**
         * Uses an application-owned data source. The store never closes it.
         *
         * @param value application data source
         * @return this builder
         */
        public Builder dataSource(DataSource value) {
            this.dataSource = value;
            return this;
        }

        /**
         * Uses simple non-pooling DriverManager connections for the supplied URL.
         *
         * @param value JDBC URL
         * @return this builder
         */
        public Builder jdbcUrl(String value) {
            this.jdbcUrl = value;
            return this;
        }

        /**
         * Supplies URL-mode credentials without embedding them in the JDBC URL.
         *
         * @param value JDBC username
         * @return this builder
         */
        public Builder username(String value) {
            this.username = value;
            return this;
        }

        /**
         * Supplies the URL-mode JDBC password.
         *
         * @param value JDBC password
         * @return this builder
         */
        public Builder password(String value) {
            this.password = value;
            return this;
        }

        /**
         * Optionally loads a JDBC driver for legacy or isolated class-loader deployments.
         *
         * @param value driver class name
         * @return this builder
         */
        public Builder driverClassName(String value) {
            this.driverClassName = value;
            return this;
        }

        /**
         * Explicitly selects a dialect. Otherwise H2 or PostgreSQL is detected.
         *
         * @param value SQL dialect
         * @return this builder
         */
        public Builder dialect(TxFlowSqlDialect value) {
            this.dialect = value;
            return this;
        }

        /**
         * Selects migration, validation, or externally managed schema startup.
         *
         * @param value schema behavior
         * @return this builder
         */
        public Builder schemaManagement(SchemaManagement value) {
            this.schemaManagement = value;
            return this;
        }

        /**
         * Supplies the time source used to validate mutation-fence expiry.
         *
         * @param value store clock
         * @return this builder
         */
        public Builder clock(Clock value) {
            this.clock = value;
            return this;
        }

        /**
         * Validates configuration, initializes the schema, and creates the store.
         *
         * @return ready relational store
         */
        public RdbmsFlowExecutionStore build() {
            boolean urlMode = jdbcUrl != null;
            if ((dataSource == null) == (jdbcUrl == null)) {
                throw new IllegalStateException(
                        "Exactly one of dataSource or jdbcUrl must be configured");
            }
            if (urlMode && jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("jdbcUrl cannot be blank");
            }
            if (!urlMode && (username != null || password != null || driverClassName != null)) {
                throw new IllegalStateException(
                        "JDBC URL credentials and driver class cannot accompany a DataSource");
            }
            if (password != null && username == null) {
                throw new IllegalStateException("A JDBC password requires a username");
            }
            String selectedJdbcUrl = urlMode ? applyEmbeddedH2DurabilityDefault(jdbcUrl) : null;
            DataSource selectedDataSource = urlMode
                    ? new DriverManagerDataSource(
                    selectedJdbcUrl, username, password, driverClassName)
                    : dataSource;
            TxFlowSqlDialect selectedDialect = dialect != null
                    ? dialect : detectDialect(selectedDataSource, selectedJdbcUrl);
            SchemaManagement selectedManagement = schemaManagement != null
                    ? schemaManagement : urlMode ? SchemaManagement.MIGRATE
                    : SchemaManagement.VALIDATE;
            Clock selectedClock = Objects.requireNonNull(clock, "clock");
            Connection anchor = null;
            try {
                if (selectedDialect == H2Dialect.INSTANCE) {
                    // An H2 in-memory database otherwise disappears between per-operation
                    // connections. The same store-owned connection gives file mode an orderly
                    // close boundary. Closing it never closes an application DataSource.
                    anchor = selectedDataSource.getConnection();
                }
                if (anchor != null) {
                    selectedDialect.validateDatabase(anchor);
                } else {
                    try (Connection connection = selectedDataSource.getConnection()) {
                        selectedDialect.validateDatabase(connection);
                    }
                }
                new RdbmsSchemaManager(selectedDataSource, selectedDialect, selectedClock)
                        .initialize(selectedManagement);
                return new RdbmsFlowExecutionStore(selectedDataSource, selectedDialect,
                        FlowStoreCodec.standard(), selectedClock, anchor);
            } catch (SQLException failure) {
                closeQuietly(anchor);
                throw new FlowStoreException("TXFLOW_STORE_CONFIGURATION_FAILED",
                        "Relational TxFlow store configuration failed",
                        RdbmsSqlExceptionSanitizer.sanitize(failure));
            } catch (RuntimeException | Error failure) {
                closeQuietly(anchor);
                throw failure;
            }
        }

        private String applyEmbeddedH2DurabilityDefault(String url) {
            String normalized = url.toUpperCase(java.util.Locale.ROOT);
            if (H2Dialect.INSTANCE.accepts(url) && !normalized.contains(";WRITE_DELAY=")) {
                // H2 otherwise delays durable file writes by default. Force committed
                // transactions to disk for the extension's restart-durable embedded profile.
                return url + (url.endsWith(";") ? "" : ";") + "WRITE_DELAY=0";
            }
            return url;
        }

        private TxFlowSqlDialect detectDialect(DataSource source, String knownUrl) {
            if (H2Dialect.INSTANCE.accepts(knownUrl)) return H2Dialect.INSTANCE;
            if (PostgresDialect.INSTANCE.accepts(knownUrl)) return PostgresDialect.INSTANCE;
            try (Connection connection = source.getConnection()) {
                DatabaseMetaData metadata = connection.getMetaData();
                String detectedUrl = metadata.getURL();
                if (H2Dialect.INSTANCE.accepts(detectedUrl)) return H2Dialect.INSTANCE;
                if (PostgresDialect.INSTANCE.accepts(detectedUrl)) return PostgresDialect.INSTANCE;
                String product = metadata.getDatabaseProductName();
                if (product != null && product.equalsIgnoreCase("H2")) return H2Dialect.INSTANCE;
                if (product != null && product.toLowerCase(java.util.Locale.ROOT)
                        .contains("postgresql")) return PostgresDialect.INSTANCE;
            } catch (SQLException failure) {
                throw new FlowStoreException("TXFLOW_STORE_CONFIGURATION_FAILED",
                        "Database metadata could not be inspected",
                        RdbmsSqlExceptionSanitizer.sanitize(failure));
            }
            throw new IllegalArgumentException(
                    "Database is not recognized; configure a tested TxFlowSqlDialect");
        }

        private void closeQuietly(Connection connection) {
            if (connection == null) return;
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Preserve the configuration failure that caused cleanup.
            }
        }
    }
}
