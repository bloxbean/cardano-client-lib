package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.stream.BindingOutcome;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBatchResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBatchStatus;
import com.bloxbean.cardano.client.txflow.stream.TxStreamBinding;
import com.bloxbean.cardano.client.txflow.stream.TxStreamDuplicateItemException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemRecord;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;
import com.bloxbean.cardano.client.txflow.stream.StreamOwnershipLease;
import com.bloxbean.cardano.client.txflow.stream.TxStreamPlannedRecord;
import com.bloxbean.cardano.client.txflow.stream.TxStreamStateStore;
import com.bloxbean.cardano.client.txflow.stream.TxStreamStoreCodec;

import javax.sql.DataSource;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transactional JDBC implementation of {@link TxStreamStateStore}, the durable backing for the
 * ADR 0004 restart re-attach protocol.
 *
 * <p>The store owns the stream's planning metadata — the item registry, write-ahead execution
 * bindings, denormalized item projections (with their per-item compare-and-swap watermark), the
 * persisted planned executions used to re-dispatch an absent execution, and batch projections. It
 * mirrors {@link RdbmsFlowExecutionStore}: every compound operation runs in one JDBC transaction;
 * connections, statements, and result sets are closed on every path; {@link Error} is caught in the
 * transaction wrapper; and it never creates a thread, executor, scheduler, or connection pool.
 * {@link #isDurable()} is {@code true}, so the stream builder requires a durable engine store to
 * pair with it.</p>
 *
 * <p>Authority is split exactly as the SPI defines: authoritative planning writes
 * ({@link #registerItem}, {@link #bind}, {@link #persistPlanned}) fail closed, while
 * {@link #projectItem} applies a denormalized view under a strict per-item CAS
 * ({@code sourceSequence > stored}) so a stale projection can never overwrite a newer one. Eviction
 * is a no-op: a durable store retains settled items, planned records, and projections indefinitely
 * (the retention-cap lift) so re-attach always sees the full planning history.</p>
 *
 * <p><b>No secrets are ever written.</b> The persisted planned record carries only the
 * portable-encoded flow, non-sensitive bindings, and secure-binding <em>references</em> plus their
 * fingerprints. Rejecting the sanctioned inline-secret channel is enforced upstream (the stream
 * fails such an item typed before this store is reached); this store neither scans nor scrubs and
 * faithfully persists exactly what the stream classified as non-sensitive.</p>
 */
public final class RdbmsTxStreamStateStore implements TxStreamStateStore, AutoCloseable {
    private final DataSource dataSource;
    private final TxFlowSqlDialect dialect;
    private final TxStreamStoreCodec codec;
    private final Clock clock;
    private final Connection ownedAnchorConnection;
    private final AtomicBoolean closed = new AtomicBoolean();

    private RdbmsTxStreamStateStore(DataSource dataSource, TxFlowSqlDialect dialect,
                                    TxStreamStoreCodec codec, Clock clock,
                                    Connection ownedAnchorConnection) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.codec = codec;
        this.clock = clock;
        this.ownedAnchorConnection = ownedAnchorConnection;
    }

    /**
     * Starts configuration of a relational stream store.
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
    public boolean isDurable() {
        return true;
    }

    @Override
    public boolean supportsOwnership() {
        return true;
    }

    @Override
    public void registerItem(TxStreamItemRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            inTransaction("register stream item", connection -> {
                RegistrationRow existing = readRegistration(connection, record.itemId());
                if (existing != null && existing.registered()) {
                    throw new TxStreamDuplicateItemException(record.itemId(),
                            "Item is already registered: " + record.itemId());
                }
                if (existing != null) {
                    updateRegistration(connection, record);
                } else {
                    insertRegistration(connection, record);
                }
                return null;
            });
        } catch (TxStreamException failure) {
            // A concurrent insert of the same brand-new item surfaces as a unique conflict.
            if ("TXSTREAM_STORE_UNIQUE_CONFLICT".equals(failure.getCode())) {
                throw new TxStreamDuplicateItemException(record.itemId(),
                        "Item is already registered: " + record.itemId());
            }
            throw failure;
        }
    }

    @Override
    public void bind(String itemId, TxStreamBinding binding) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(binding, "binding");
        inTransaction("bind stream item", connection -> {
            RegistrationRow registration = readRegistration(connection, itemId);
            if (registration == null || !registration.registered()) {
                throw new TxStreamException("TXSTREAM_ITEM_UNKNOWN",
                        "Cannot bind unregistered item: " + itemId);
            }
            upsertBinding(connection, itemId, binding);
            return null;
        });
    }

    @Override
    public void confirmBinding(String itemId, BindingOutcome outcome) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(outcome, "outcome");
        inTransaction("confirm stream binding", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE txstream_binding SET outcome = ? WHERE item_id = ?")) {
                statement.setString(1, outcome.name());
                statement.setString(2, itemId);
                if (statement.executeUpdate() != 1) {
                    throw new TxStreamException("TXSTREAM_BINDING_MISSING",
                            "No binding recorded for item: " + itemId);
                }
            }
            return null;
        });
    }

    @Override
    public boolean projectItem(TxStreamItemResult result, long sourceSequence) {
        Objects.requireNonNull(result, "result");
        return inTransaction("project stream item", connection -> {
            Long stored = readProjectionSequence(connection, result.getItemId());
            // Strict compare-and-swap: only a strictly greater source sequence wins.
            if (stored != null && sourceSequence <= stored) {
                return Boolean.FALSE;
            }
            boolean rowExists = stored != null || registrationRowExists(connection,
                    result.getItemId());
            if (rowExists) {
                updateProjection(connection, result, sourceSequence);
            } else {
                insertProjectionOnly(connection, result, sourceSequence);
            }
            return Boolean.TRUE;
        });
    }

    @Override
    public Optional<TxStreamItemResult> getItem(String streamId, String itemId) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(itemId, "itemId");
        return inTransaction("read stream item", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT stream_id, status, execution_id, step_id, projection_lane_name, "
                            + "transaction_hash, error_code, error_message, updated_at "
                            + "FROM txstream_item "
                            + "WHERE item_id = ? AND stream_id = ? AND status IS NOT NULL")) {
                statement.setString(1, itemId);
                statement.setString(2, streamId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return Optional.empty();
                    return Optional.of(decodeProjection(row, itemId));
                }
            }
        });
    }

    @Override
    public Optional<Long> lastProjectionSequence(String streamId, String itemId) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(itemId, "itemId");
        return inTransaction("read stream projection sequence", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT projection_sequence FROM txstream_item "
                            + "WHERE item_id = ? AND stream_id = ? "
                            + "AND projection_sequence IS NOT NULL")) {
                statement.setString(1, itemId);
                statement.setString(2, streamId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return Optional.<Long>empty();
                    return Optional.of(row.getLong(1));
                }
            }
        });
    }

    @Override
    public void evictItem(String itemId) {
        // Durable: retain settled items indefinitely (retention-cap lift).
    }

    @Override
    public List<String> listNonTerminalItemIds(String streamId) {
        Objects.requireNonNull(streamId, "streamId");
        return inTransaction("list non-terminal stream items", connection -> {
            List<String> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT item_id FROM txstream_item "
                            + "WHERE stream_id = ? AND terminal = ?")) {
                statement.setString(1, streamId);
                statement.setBoolean(2, false);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(rows.getString(1));
                }
            }
            return result;
        });
    }

    @Override
    public void persistPlanned(TxStreamPlannedRecord record) {
        Objects.requireNonNull(record, "record");
        String metadata = codec.encodePlannedMetadata(record.bindings(),
                record.secureBindingReferences(), record.secureBindingFingerprints(),
                record.members(), record.templateId(), record.templateFingerprint());
        inTransaction("persist planned execution", connection -> {
            ExistingPlanned existing = readPlannedClaim(connection, record.executionId());
            if (existing == null) {
                insertPlanned(connection, record, metadata);
            } else if (!sameClaimAndMembers(existing, record)) {
                // A record for a different claim/member set must never overwrite an existing
                // row for this execution id — that would corrupt the planning history the
                // re-attach protocol depends on. Keep the existing record.
                return null;
            }
            // An equivalent re-persist (same claim + member set) is idempotent; the row is
            // already present with byte-identical deterministic content, so keep it.
            return null;
        });
    }

    @Override
    public List<TxStreamPlannedRecord> listPlanned(String streamId) {
        Objects.requireNonNull(streamId, "streamId");
        return inTransaction("list planned executions", connection -> {
            List<TxStreamPlannedRecord> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT execution_id, idempotency_key, lane_name, "
                            + "canonical_spending_identity, portable_flow, metadata_payload "
                            + "FROM txstream_planned WHERE stream_id = ?")) {
                statement.setString(1, streamId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        TxStreamStoreCodec.PlannedMetadata metadata =
                                codec.decodePlannedMetadata(readText(rows, "metadata_payload"));
                        result.add(new TxStreamPlannedRecord(streamId, rows.getString("execution_id"),
                                rows.getString("idempotency_key"), rows.getString("lane_name"),
                                rows.getString("canonical_spending_identity"),
                                readText(rows, "portable_flow"), metadata.bindings(),
                                metadata.secureBindingReferences(),
                                metadata.secureBindingFingerprints(), metadata.members(),
                                metadata.templateId(), metadata.templateFingerprint()));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public void recordBatch(TxStreamBatchResult batch) {
        Objects.requireNonNull(batch, "batch");
        String itemIds = codec.encodeStringList(batch.itemIds());
        String executionIds = codec.encodeStringList(batch.executionIds());
        String failureCode = errorCode(batch.failure());
        String failureMessage = errorMessage(batch.failure());
        inTransaction("record stream batch", connection -> {
            if (batchExists(connection, batch.streamId(), batch.batchId())) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE txstream_batch SET status = ?, item_ids = ?, "
                                + "execution_ids = ?, failure_code = ?, failure_message = ? "
                                + "WHERE stream_id = ? AND batch_id = ?")) {
                    statement.setString(1, batch.status().name());
                    statement.setString(2, itemIds);
                    statement.setString(3, executionIds);
                    statement.setString(4, failureCode);
                    statement.setString(5, failureMessage);
                    statement.setString(6, batch.streamId());
                    statement.setString(7, batch.batchId());
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO txstream_batch (stream_id, batch_id, status, item_ids, "
                                + "execution_ids, failure_code, failure_message) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, batch.streamId());
                    statement.setString(2, batch.batchId());
                    statement.setString(3, batch.status().name());
                    statement.setString(4, itemIds);
                    statement.setString(5, executionIds);
                    statement.setString(6, failureCode);
                    statement.setString(7, failureMessage);
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public Optional<TxStreamBatchResult> getBatch(String streamId, String batchId) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(batchId, "batchId");
        return inTransaction("read stream batch", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status, item_ids, execution_ids, failure_code, failure_message "
                            + "FROM txstream_batch WHERE stream_id = ? AND batch_id = ?")) {
                statement.setString(1, streamId);
                statement.setString(2, batchId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return Optional.<TxStreamBatchResult>empty();
                    Throwable failure = decodeError(row.getString("failure_code"),
                            row.getString("failure_message"));
                    return Optional.of(new TxStreamBatchResult(streamId, batchId,
                            TxStreamBatchStatus.valueOf(row.getString("status")),
                            codec.decodeStringList(readText(row, "item_ids")),
                            codec.decodeStringList(readText(row, "execution_ids")), failure));
                }
            }
        });
    }

    @Override
    public void evictBatch(String batchId) {
        // Durable: retain terminal batches.
    }

    @Override
    public void persistBootstrapFingerprint(String streamId, String fingerprint) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        inTransaction("persist bootstrap fingerprint", connection -> {
            boolean exists;
            try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                    "SELECT stream_id FROM txstream_bootstrap WHERE stream_id = ?"))) {
                statement.setString(1, streamId);
                try (ResultSet row = statement.executeQuery()) {
                    exists = row.next();
                }
            }
            if (exists) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE txstream_bootstrap SET fingerprint = ? WHERE stream_id = ?")) {
                    statement.setString(1, fingerprint);
                    statement.setString(2, streamId);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO txstream_bootstrap (stream_id, fingerprint) VALUES (?, ?)")) {
                    statement.setString(1, streamId);
                    statement.setString(2, fingerprint);
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public Optional<String> getBootstrapFingerprint(String streamId) {
        Objects.requireNonNull(streamId, "streamId");
        return inTransaction("read bootstrap fingerprint", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT fingerprint FROM txstream_bootstrap WHERE stream_id = ?")) {
                statement.setString(1, streamId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return Optional.<String>empty();
                    return Optional.of(row.getString(1));
                }
            }
        });
    }

    @Override
    public Optional<StreamOwnershipLease> tryAcquireOwnership(String streamId, String ownerToken,
                                                             Instant now, Duration duration) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(ownerToken, "ownerToken");
        validateLeaseRequest(now, duration);
        Instant expiry = now.plus(duration);
        return inTransaction("acquire stream ownership", connection -> {
            OwnershipRow current = readOwnership(connection, streamId);
            if (current != null && current.ownerToken() != null
                    && current.expiresAt() != null && current.expiresAt().isAfter(now)
                    && !current.ownerToken().equals(ownerToken)) {
                return Optional.<StreamOwnershipLease>empty();
            }
            long epoch = (current == null ? 0L : current.epoch()) + 1L;
            if (current == null) {
                insertOwnership(connection, streamId, ownerToken, epoch, expiry);
            } else {
                updateOwnership(connection, streamId, ownerToken, epoch, expiry);
            }
            return Optional.of(new StreamOwnershipLease(streamId, ownerToken, epoch, expiry));
        });
    }

    @Override
    public StreamOwnershipLease renewOwnership(StreamOwnershipLease lease, Instant now,
                                               Duration duration) {
        Objects.requireNonNull(lease, "lease");
        validateLeaseRequest(now, duration);
        Instant expiry = now.plus(duration);
        return inTransaction("renew stream ownership", connection -> {
            OwnershipRow current = readOwnership(connection, lease.streamId());
            if (current == null || current.epoch() != lease.epoch()
                    || current.ownerToken() == null
                    || !current.ownerToken().equals(lease.ownerToken())) {
                throw new TxStreamException("TXSTREAM_OWNERSHIP_FENCED",
                        "Ownership lease for stream '" + lease.streamId()
                                + "' is no longer current (epoch " + lease.epoch()
                                + " superseded or released)");
            }
            updateOwnership(connection, lease.streamId(), lease.ownerToken(), lease.epoch(), expiry);
            return new StreamOwnershipLease(lease.streamId(), lease.ownerToken(), lease.epoch(),
                    expiry);
        });
    }

    @Override
    public void releaseOwnership(StreamOwnershipLease lease) {
        Objects.requireNonNull(lease, "lease");
        inTransaction("release stream ownership", connection -> {
            OwnershipRow current = readOwnership(connection, lease.streamId());
            if (current != null && current.epoch() == lease.epoch()
                    && lease.ownerToken().equals(current.ownerToken())) {
                // Clear the owner but retain the epoch high-water in the row so a
                // later acquire mints a strictly higher epoch (monotonicity).
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE txstream_ownership SET owner_token = NULL WHERE stream_id = ?")) {
                    statement.setString(1, lease.streamId());
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }

    private OwnershipRow readOwnership(Connection connection, String streamId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                "SELECT owner_token, epoch, expires_at FROM txstream_ownership WHERE stream_id = ?"))) {
            statement.setString(1, streamId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                Timestamp expiresAt = row.getTimestamp("expires_at");
                return new OwnershipRow(row.getString("owner_token"), row.getLong("epoch"),
                        expiresAt != null ? expiresAt.toInstant() : null);
            }
        }
    }

    private void insertOwnership(Connection connection, String streamId, String ownerToken,
                                 long epoch, Instant expiry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO txstream_ownership"
                + " (stream_id, owner_token, epoch, expires_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, streamId);
            statement.setString(2, ownerToken);
            statement.setLong(3, epoch);
            statement.setTimestamp(4, timestamp(expiry));
            statement.executeUpdate();
        }
    }

    private void updateOwnership(Connection connection, String streamId, String ownerToken,
                                 long epoch, Instant expiry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE txstream_ownership"
                + " SET owner_token = ?, epoch = ?, expires_at = ? WHERE stream_id = ?")) {
            statement.setString(1, ownerToken);
            statement.setLong(2, epoch);
            statement.setTimestamp(3, timestamp(expiry));
            statement.setString(4, streamId);
            statement.executeUpdate();
        }
    }

    private static void validateLeaseRequest(Instant now, Duration duration) {
        if (now == null || duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("lease time and positive duration are required");
        }
    }

    private record OwnershipRow(String ownerToken, long epoch, Instant expiresAt) {
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
                throw new TxStreamException("TXSTREAM_STORE_CLOSE_FAILED",
                        "Relational TxFlow stream store could not close its embedded resource",
                        RdbmsSqlExceptionSanitizer.sanitize(failure));
            }
        }
    }

    // ---- registration ----

    private RegistrationRow readRegistration(Connection connection, String itemId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                "SELECT idempotency_key FROM txstream_item WHERE item_id = ?"))) {
            statement.setString(1, itemId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                return new RegistrationRow(row.getString(1) != null);
            }
        }
    }

    private boolean registrationRowExists(Connection connection, String itemId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                "SELECT item_id FROM txstream_item WHERE item_id = ?"))) {
            statement.setString(1, itemId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    private void insertRegistration(Connection connection, TxStreamItemRecord record)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txstream_item (item_id, idempotency_key, lane_name, "
                        + "fingerprint, accepted_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, record.itemId());
            statement.setString(2, record.idempotencyKey());
            statement.setString(3, record.laneName());
            statement.setString(4, record.fingerprint());
            statement.setTimestamp(5, timestamp(record.acceptedAt()));
            statement.executeUpdate();
        }
    }

    private void updateRegistration(Connection connection, TxStreamItemRecord record)
            throws SQLException {
        // A projection-only row (created by an out-of-order projectItem) becomes registered;
        // projection columns are left untouched.
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE txstream_item SET idempotency_key = ?, lane_name = ?, "
                        + "fingerprint = ?, accepted_at = ? WHERE item_id = ?")) {
            statement.setString(1, record.idempotencyKey());
            statement.setString(2, record.laneName());
            statement.setString(3, record.fingerprint());
            statement.setTimestamp(4, timestamp(record.acceptedAt()));
            statement.setString(5, record.itemId());
            statement.executeUpdate();
        }
    }

    // ---- binding ----

    private void upsertBinding(Connection connection, String itemId, TxStreamBinding binding)
            throws SQLException {
        boolean exists;
        try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                "SELECT item_id FROM txstream_binding WHERE item_id = ?"))) {
            statement.setString(1, itemId);
            try (ResultSet row = statement.executeQuery()) {
                exists = row.next();
            }
        }
        if (exists) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE txstream_binding SET execution_id = ?, flow_id = ?, step_id = ?, "
                            + "lane_name = ?, outcome = NULL WHERE item_id = ?")) {
                statement.setString(1, binding.executionId());
                statement.setString(2, binding.flowId());
                statement.setString(3, binding.stepId());
                statement.setString(4, binding.laneName());
                statement.setString(5, itemId);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO txstream_binding (item_id, execution_id, flow_id, step_id, "
                            + "lane_name, outcome) VALUES (?, ?, ?, ?, ?, NULL)")) {
                statement.setString(1, itemId);
                statement.setString(2, binding.executionId());
                statement.setString(3, binding.flowId());
                statement.setString(4, binding.stepId());
                statement.setString(5, binding.laneName());
                statement.executeUpdate();
            }
        }
    }

    // ---- projection ----

    private Long readProjectionSequence(Connection connection, String itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                "SELECT projection_sequence FROM txstream_item WHERE item_id = ?"))) {
            statement.setString(1, itemId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                long sequence = row.getLong(1);
                return row.wasNull() ? null : sequence;
            }
        }
    }

    private void updateProjection(Connection connection, TxStreamItemResult result, long sequence)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE txstream_item SET stream_id = ?, status = ?, execution_id = ?, "
                        + "step_id = ?, projection_lane_name = ?, transaction_hash = ?, "
                        + "error_code = ?, error_message = ?, updated_at = ?, "
                        + "projection_sequence = ?, terminal = ? WHERE item_id = ?")) {
            bindProjection(statement, result, sequence);
            statement.setString(12, result.getItemId());
            statement.executeUpdate();
        }
    }

    private void insertProjectionOnly(Connection connection, TxStreamItemResult result,
                                      long sequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txstream_item (stream_id, status, execution_id, step_id, "
                        + "projection_lane_name, transaction_hash, error_code, error_message, "
                        + "updated_at, projection_sequence, terminal, item_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindProjection(statement, result, sequence);
            statement.setString(12, result.getItemId());
            statement.executeUpdate();
        }
    }

    private void bindProjection(PreparedStatement statement, TxStreamItemResult result,
                                long sequence) throws SQLException {
        statement.setString(1, result.getStreamId());
        statement.setString(2, result.getStatus().name());
        statement.setString(3, result.getExecutionId());
        statement.setString(4, result.getStepId());
        statement.setString(5, result.getLaneName());
        statement.setString(6, result.getTransactionHash());
        statement.setString(7, errorCode(result.getError()));
        statement.setString(8, errorMessage(result.getError()));
        statement.setTimestamp(9, timestamp(result.getUpdatedAt()));
        statement.setLong(10, sequence);
        statement.setBoolean(11, isTerminal(result.getStatus()));
    }

    private TxStreamItemResult decodeProjection(ResultSet row, String itemId) throws SQLException {
        Throwable error = decodeError(row.getString("error_code"), row.getString("error_message"));
        Timestamp updatedAt = row.getTimestamp("updated_at");
        return TxStreamItemResult.builder(row.getString("stream_id"), itemId,
                        TxStreamItemStatus.valueOf(row.getString("status")))
                .executionId(row.getString("execution_id"))
                .stepId(row.getString("step_id"))
                .laneName(row.getString("projection_lane_name"))
                .transactionHash(row.getString("transaction_hash"))
                .error(error)
                .updatedAt(updatedAt != null ? updatedAt.toInstant() : clock.instant())
                .build();
    }

    private static boolean isTerminal(TxStreamItemStatus status) {
        return status == TxStreamItemStatus.CONFIRMED
                || status == TxStreamItemStatus.FAILED
                || status == TxStreamItemStatus.CANCELLED;
    }

    // ---- planned ----

    private ExistingPlanned readPlannedClaim(Connection connection, String executionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                "SELECT idempotency_key, metadata_payload FROM txstream_planned "
                        + "WHERE execution_id = ?"))) {
            statement.setString(1, executionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                Set<String> itemIds = new HashSet<>();
                codec.decodePlannedMetadata(readText(row, "metadata_payload")).members()
                        .forEach(member -> itemIds.add(member.itemId()));
                return new ExistingPlanned(row.getString("idempotency_key"), itemIds);
            }
        }
    }

    private void insertPlanned(Connection connection, TxStreamPlannedRecord record, String metadata)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO txstream_planned (execution_id, stream_id, idempotency_key, "
                        + "lane_name, canonical_spending_identity, portable_flow, metadata_payload) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, record.executionId());
            statement.setString(2, record.streamId());
            statement.setString(3, record.idempotencyKey());
            statement.setString(4, record.laneName());
            statement.setString(5, record.canonicalSpendingIdentity());
            statement.setString(6, record.portableFlow());
            statement.setString(7, metadata);
            statement.executeUpdate();
        }
    }

    private static boolean sameClaimAndMembers(ExistingPlanned existing,
                                               TxStreamPlannedRecord incoming) {
        if (!existing.idempotencyKey().equals(incoming.idempotencyKey())) {
            return false;
        }
        Set<String> incomingItems = new HashSet<>();
        incoming.members().forEach(member -> incomingItems.add(member.itemId()));
        return existing.itemIds().equals(incomingItems);
    }

    // ---- batch ----

    private boolean batchExists(Connection connection, String streamId, String batchId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.forUpdate(
                "SELECT batch_id FROM txstream_batch "
                        + "WHERE stream_id = ? AND batch_id = ?"))) {
            statement.setString(1, streamId);
            statement.setString(2, batchId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    // ---- error persistence ----

    private String errorCode(Throwable error) {
        if (error instanceof TxStreamException streamException) {
            return streamException.getCode();
        }
        return null;
    }

    private String errorMessage(Throwable error) {
        if (error == null) return null;
        return error.getMessage() != null ? error.getMessage() : error.toString();
    }

    private Throwable decodeError(String code, String message) {
        if (code != null) {
            return new TxStreamException(code, message);
        }
        if (message != null) {
            return new TxStreamException("TXSTREAM_STORE_PROJECTED_ERROR", message);
        }
        return null;
    }

    // ---- shared ----

    private String readText(ResultSet row, String column) throws SQLException {
        String value = row.getString(column);
        if (value == null) {
            throw new TxStreamException("TXSTREAM_STORE_CORRUPT",
                    "Relational stream payload is missing for column " + column);
        }
        return value;
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant.truncatedTo(ChronoUnit.MICROS));
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
        } catch (TxStreamException failure) {
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
            TxStreamException uncertain = new TxStreamException(
                    "TXSTREAM_STORE_COMMIT_UNCERTAIN",
                    "Relational TxFlow stream transaction commit outcome is uncertain during "
                            + operation, RdbmsSqlExceptionSanitizer.sanitize(failure));
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
            TxStreamException uncertain = new TxStreamException(
                    "TXSTREAM_STORE_ROLLBACK_UNCERTAIN",
                    "Relational TxFlow stream transaction rollback outcome is uncertain during "
                            + operation, RdbmsSqlExceptionSanitizer.sanitize(rollbackFailure));
            uncertain.addSuppressed(sanitizeOriginalFailure(original));
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
            // The commit is authoritative; a cleanup failure cannot turn it into a failure.
        }
    }

    private void closeAfterSuccess(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // The operation has committed. Connection disposal is best-effort at this boundary.
        }
    }

    private TxStreamException mapSqlFailure(String operation, SQLException failure) {
        SQLException safeCause = RdbmsSqlExceptionSanitizer.sanitize(failure);
        if (dialect.isUniqueConstraintViolation(failure)) {
            return new TxStreamException("TXSTREAM_STORE_UNIQUE_CONFLICT",
                    "Relational TxFlow stream store observed a concurrent unique write", safeCause);
        }
        String state = failure.getSQLState();
        if (state != null && state.startsWith("08")) {
            return new TxStreamException("TXSTREAM_STORE_UNAVAILABLE",
                    "Relational TxFlow stream store is unavailable during " + operation, safeCause);
        }
        if (dialect.isRetryableTransactionFailure(failure)) {
            return new TxStreamException("TXSTREAM_STORE_SERIALIZATION_FAILURE",
                    "Relational TxFlow stream transaction was concurrently invalidated during "
                            + operation, safeCause);
        }
        return new TxStreamException("TXSTREAM_STORE_OPERATION_FAILED",
                "Relational TxFlow stream store operation failed during " + operation, safeCause);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new TxStreamException("TXSTREAM_STORE_CLOSED",
                    "Relational TxFlow stream store is closed");
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private record RegistrationRow(boolean registered) {
    }

    private record ExistingPlanned(String idempotencyKey, Set<String> itemIds) {
    }

    /** Builder mirroring {@link RdbmsFlowExecutionStore.Builder} for the stream store. */
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
         * Supplies the time source used for projection timestamps.
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
         * @return ready relational stream store
         */
        public RdbmsTxStreamStateStore build() {
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
                    // Hold one store-owned connection so an in-memory H2 database does not
                    // disappear between per-operation connections, and file mode gets an orderly
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
                new RdbmsStreamSchemaManager(selectedDataSource, selectedDialect, selectedClock)
                        .initialize(selectedManagement);
                return new RdbmsTxStreamStateStore(selectedDataSource, selectedDialect,
                        TxStreamStoreCodec.standard(), selectedClock, anchor);
            } catch (SQLException failure) {
                closeQuietly(anchor);
                throw new TxStreamException("TXSTREAM_STORE_CONFIGURATION_FAILED",
                        "Relational TxFlow stream store configuration failed",
                        RdbmsSqlExceptionSanitizer.sanitize(failure));
            } catch (RuntimeException | Error failure) {
                closeQuietly(anchor);
                throw failure;
            }
        }

        private String applyEmbeddedH2DurabilityDefault(String url) {
            String normalized = url.toUpperCase(java.util.Locale.ROOT);
            if (H2Dialect.INSTANCE.accepts(url) && !normalized.contains(";WRITE_DELAY=")) {
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
                throw new TxStreamException("TXSTREAM_STORE_CONFIGURATION_FAILED",
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
