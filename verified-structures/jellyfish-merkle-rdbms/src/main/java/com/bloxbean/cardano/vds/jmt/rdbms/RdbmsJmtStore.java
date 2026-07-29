package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.JmtEncoding;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.NodeKey;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessCoordinator;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessLease;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatDescriptor;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatMismatchException;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStoreInspection;
import com.bloxbean.cardano.vds.jmt.store.JmtWriteConflictException;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RDBMS implementation of {@link JmtStore}.
 *
 * <p>Provides database-neutral persistence for Jellyfish Merkle Trees using standard SQL.
 * Mutations are fail-fast serialized per namespace. PostgreSQL additionally uses transaction-
 * scoped advisory locks so separate store instances and JVMs cannot mutate one namespace at the
 * same time. Its JDBC pool must provide at least two connections because an access lease holds one
 * connection while tree reads or the atomic write transaction use another.
 *
 * @since 0.8.0
 */
public class RdbmsJmtStore implements JmtStore {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final RdbmsJmtSchema schema;
    private final byte keyPrefix;
    private final KeyCodec keyCodec;
    private final JmtAccessCoordinator accessCoordinator;
    private volatile JmtFormatDescriptor formatDescriptor;
    private static final byte NODE_KEY_PREFIX = 0x4E; // 'N'
    private static final int KEY_HASH_LENGTH = 32;

    /**
     * Creates a JMT store with the specified configuration and namespace.
     *
     * @param config the database configuration
     * @param keyPrefix the key prefix (namespace ID, 0-255)
     */
    public RdbmsJmtStore(DbConfig config, byte keyPrefix) {
        this(config, keyPrefix, createAccessCoordinator(config, keyPrefix));
    }

    /** Creates a store with the coordinator selected for its SQL dialect. */
    private RdbmsJmtStore(DbConfig config,
                          byte keyPrefix,
                          JmtAccessCoordinator accessCoordinator) {
        Objects.requireNonNull(config, "config");
        this.dataSource = config.dataSource();
        this.dialect = config.dialect();
        this.schema = new RdbmsJmtSchema(config.tablePrefix());
        this.keyPrefix = keyPrefix;
        this.keyCodec = dialect.keyCodec();
        this.accessCoordinator = Objects.requireNonNull(accessCoordinator, "accessCoordinator");
        loadAndValidateExistingFormat();
    }

    private static JmtAccessCoordinator createAccessCoordinator(DbConfig config, byte keyPrefix) {
        Objects.requireNonNull(config, "config");
        if (!"PostgreSQL".equalsIgnoreCase(config.dialect().name())) {
            return new JmtAccessCoordinator();
        }
        RdbmsJmtSchema schema = new RdbmsJmtSchema(config.tablePrefix());
        String identity = schema.metadataTable() + ":" + (keyPrefix & 0xFF);
        return new JmtAccessCoordinator(new PostgresJmtAccessLockProvider(
                config.dataSource(), identity));
    }

    /**
     * Creates a JMT store with default namespace (0x00).
     *
     * @param config the database configuration
     */
    public RdbmsJmtStore(DbConfig config) {
        this(config, (byte) 0x00);
    }

    @Override
    public JmtAccessCoordinator accessCoordinator() {
        return accessCoordinator;
    }

    @Override
    public void ensureFormat(JmtFormatDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor").requirePersistent();
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance("ensureFormat");
             Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                Optional<JmtFormatDescriptor> existing = readFormat(conn);
                if (existing.isEmpty()) {
                    if (hasNamespaceData(conn)) {
                        throw new JmtFormatMismatchException("Non-empty RDBMS JMT namespace has no "
                                + "format descriptor; rebuild it into a fresh namespace");
                    }
                    String sql = "INSERT INTO " + schema.metadataTable()
                            + " (namespace, format_data, rollback_enabled) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, keyPrefix & 0xFF);
                        keyCodec.setKey(stmt, 2, descriptor.encode());
                        stmt.setBoolean(3, true);
                        stmt.executeUpdate();
                    }
                    conn.commit();
                    formatDescriptor = descriptor;
                    return;
                }
                if (!existing.get().equals(descriptor)) {
                    throw new JmtFormatMismatchException("RDBMS JMT format mismatch: persisted "
                            + existing.get() + ", requested " + descriptor);
                }
                conn.commit();
                formatDescriptor = existing.get();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize RDBMS JMT format", e);
        }
    }

    @Override
    public Optional<JmtFormatDescriptor> formatDescriptor() {
        return Optional.ofNullable(formatDescriptor);
    }

    @Override
    public JmtStoreInspection inspect(int maxRecords) {
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("maxRecords must be > 0");
        }
        requireFormatInitialized();
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireRead("inspect");
             Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            int originalIsolation = conn.getTransactionIsolation();
            InspectionAccumulator result = new InspectionAccumulator(maxRecords);
            try {
                if (!"SQLite".equalsIgnoreCase(dialect.name())) {
                    conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                }
                conn.setAutoCommit(false);
                inspectRoots(conn, result);
                result.latestRoot = readLatestRootForInspection(conn).orElse(null);
                inspectNodes(conn, result);
                inspectValues(conn, result);
                inspectStale(conn, result);
                conn.commit();
                return result.snapshot();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
                if (!"SQLite".equalsIgnoreCase(dialect.name())) {
                    conn.setTransactionIsolation(originalIsolation);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to inspect RDBMS JMT store", e);
        }
    }

    private void inspectRoots(Connection conn, InspectionAccumulator result) throws SQLException {
        String sql = "SELECT version, root_hash FROM " + schema.rootsTable()
                + " WHERE namespace = ? ORDER BY version";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next() && result.take()) {
                    result.roots.add(new VersionedRoot(rs.getLong("version"),
                            keyCodec.getKey(rs, "root_hash")));
                }
            }
        }
    }

    private Optional<VersionedRoot> readLatestRootForInspection(Connection conn) throws SQLException {
        String sql = "SELECT latest_version, latest_root FROM " + schema.latestTable()
                + " WHERE namespace = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new VersionedRoot(rs.getLong("latest_version"),
                        keyCodec.getKey(rs, "latest_root")));
            }
        }
    }

    private void inspectNodes(Connection conn, InspectionAccumulator result) throws SQLException {
        if (result.truncated) {
            return;
        }
        String sql = "SELECT node_path, version, node_data FROM " + schema.nodesTable()
                + " WHERE namespace = ? ORDER BY version, node_path";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next() && result.take()) {
                    try {
                        long version = rs.getLong("version");
                        NodeKey nodeKey = NodeKey.of(decodePath(
                                keyCodec.getKey(rs, "node_path")), version);
                        result.nodes.add(new JmtStoreInspection.NodeRecord(nodeKey,
                                JmtEncoding.decode(keyCodec.getKey(rs, "node_data"))));
                    } catch (RuntimeException e) {
                        result.backendIssues.add("Malformed node record: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void inspectValues(Connection conn, InspectionAccumulator result) throws SQLException {
        if (result.truncated) {
            return;
        }
        String sql = "SELECT key_hash, version, value_data, is_tombstone FROM "
                + schema.valuesTable() + " WHERE namespace = ? ORDER BY version, key_hash";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next() && result.take()) {
                    byte[] value = rs.getBoolean("is_tombstone")
                            ? null : keyCodec.getKey(rs, "value_data");
                    result.values.add(new JmtStoreInspection.ValueRecord(
                            keyCodec.getKey(rs, "key_hash"), rs.getLong("version"), value,
                            rs.getBoolean("is_tombstone")));
                }
            }
        }
    }

    private void inspectStale(Connection conn, InspectionAccumulator result) throws SQLException {
        if (result.truncated) {
            return;
        }
        String sql = "SELECT stale_since, node_path, node_version FROM " + schema.staleTable()
                + " WHERE namespace = ? ORDER BY stale_since, node_path, node_version";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next() && result.take()) {
                    result.stale.add(new JmtStoreInspection.StaleRecord(
                            rs.getLong("stale_since"),
                            NodeKey.of(decodePath(keyCodec.getKey(rs, "node_path")),
                                    rs.getLong("node_version"))));
                }
            }
        }
    }

    private void loadAndValidateExistingFormat() {
        try (Connection conn = dataSource.getConnection()) {
            Optional<JmtFormatDescriptor> existing = readFormat(conn);
            if (existing.isEmpty()) {
                if (hasNamespaceData(conn)) {
                    throw new JmtFormatMismatchException("Non-empty RDBMS JMT namespace has no "
                            + "format descriptor; rebuild it into a fresh namespace");
                }
                return;
            }
            formatDescriptor = existing.get();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read RDBMS JMT format metadata", e);
        }
    }

    private Optional<JmtFormatDescriptor> readFormat(Connection conn) throws SQLException {
        String sql = "SELECT format_data, rollback_enabled FROM " + schema.metadataTable()
                + " WHERE namespace = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                if (!rs.getBoolean("rollback_enabled")) {
                    throw new JmtFormatMismatchException("RDBMS JMT metadata disables required "
                            + "rollback support");
                }
                try {
                    JmtFormatDescriptor descriptor = JmtFormatDescriptor.decode(
                            keyCodec.getKey(rs, "format_data"));
                    descriptor.requirePersistent();
                    return Optional.of(descriptor);
                } catch (IllegalArgumentException | JmtFormatMismatchException e) {
                    throw new JmtFormatMismatchException("Malformed RDBMS JMT format descriptor", e);
                }
            }
        }
    }

    private boolean hasNamespaceData(Connection conn) throws SQLException {
        return tableHasNamespaceData(conn, schema.nodesTable())
                || tableHasNamespaceData(conn, schema.valuesTable())
                || tableHasNamespaceData(conn, schema.rootsTable())
                || tableHasNamespaceData(conn, schema.staleTable())
                || tableHasNamespaceData(conn, schema.latestTable());
    }

    private boolean tableHasNamespaceData(Connection conn, String table) throws SQLException {
        String sql = "SELECT 1 FROM " + table + " WHERE namespace = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void requireFormatInitialized() {
        if (formatDescriptor == null) {
            throw new JmtFormatMismatchException("RDBMS JMT format is not initialized; construct "
                    + "JellyfishMerkleTree with an explicit JmtProfile first");
        }
    }

    @Override
    public Optional<VersionedRoot> latestRoot() {
        String sql = "SELECT latest_version, latest_root FROM " +
                     schema.latestTable() + " WHERE namespace = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long version = rs.getLong("latest_version");
                    byte[] root = keyCodec.getKey(rs, "latest_root");
                    return Optional.of(new VersionedRoot(version, root));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read latest root", e);
        }
    }

    @Override
    public Optional<byte[]> rootHash(long version) {
        String sql = "SELECT root_hash FROM " + schema.rootsTable() +
                     " WHERE namespace = ? AND version = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, version);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(keyCodec.getKey(rs, "root_hash"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read root hash for version " + version, e);
        }
    }

    @Override
    public Optional<NodeEntry> getNode(long version, NibblePath path) {
        // Floor lookup: newest physically retained node on path with version <= requested version.
        // Stale markers drive pruning; they are not logical deletion markers for this insert/update
        // only tree, matching the RocksDB and in-memory backends.
        String sql = "SELECT node_path, version, node_data FROM " + schema.nodesTable() +
                     " WHERE namespace = ? AND node_path = ? AND version <= ? " +
                     " ORDER BY version DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, encodePath(path));
            stmt.setLong(3, version);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] pathBytes = keyCodec.getKey(rs, "node_path");
                    long nodeVersion = rs.getLong("version");
                    byte[] nodeData = keyCodec.getKey(rs, "node_data");

                    NodeKey nodeKey = NodeKey.of(decodePath(pathBytes), nodeVersion);
                    JmtNode node = JmtEncoding.decode(nodeData);
                    return Optional.of(new NodeEntry(nodeKey, node));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get node", e);
        }
    }

    @Override
    public Optional<JmtNode> getNode(NodeKey nodeKey) {
        // NO stale filtering here - this is exact lookup by NodeKey
        // InMemoryJmtStore.getNode(NodeKey) also doesn't check stale
        // Stale filtering only happens in getNode(version, path) floor lookup
        String sql = "SELECT node_data FROM " + schema.nodesTable() +
                     " WHERE namespace = ? AND node_path = ? AND version = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, encodePath(nodeKey.path()));
            stmt.setLong(3, nodeKey.version());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] nodeData = keyCodec.getKey(rs, "node_data");
                    return Optional.of(JmtEncoding.decode(nodeData));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get node by key", e);
        }
    }

    @Override
    public Optional<NodeEntry> floorNode(long version, NibblePath path) {
        return JmtStore.super.floorNode(version, path);
    }

    @Override
    public Optional<byte[]> getValue(byte[] keyHash) {
        requireKeyHash(keyHash);
        // Latest value: greatest version for this key
        String sql = "SELECT value_data, is_tombstone FROM " + schema.valuesTable() +
                     " WHERE namespace = ? AND key_hash = ? " +
                     "ORDER BY version DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, keyHash);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean isTombstone = rs.getBoolean("is_tombstone");
                    if (isTombstone) {
                        return Optional.empty();
                    }
                    byte[] value = keyCodec.getKey(rs, "value_data");
                    return Optional.ofNullable(value);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get value", e);
        }
    }

    @Override
    public Optional<byte[]> getValueAt(byte[] keyHash, long version) {
        requireKeyHash(keyHash);
        String sql = "SELECT value_data, is_tombstone FROM " + schema.valuesTable() +
                     " WHERE namespace = ? AND key_hash = ? AND version <= ? " +
                     "ORDER BY version DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, keyHash);
            stmt.setLong(3, version);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean isTombstone = rs.getBoolean("is_tombstone");
                    if (isTombstone) {
                        return Optional.empty();
                    }
                    byte[] value = keyCodec.getKey(rs, "value_data");
                    return Optional.ofNullable(value);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get value at version", e);
        }
    }

    @Override
    public CommitBatch beginCommit(long version, CommitConfig config) {
        requireFormatInitialized();
        Objects.requireNonNull(config, "config");
        JmtAccessLease lease = accessCoordinator.tryAcquireUpdate("commit", version);
        return new RdbmsCommitBatch(version, config, lease);
    }

    @Override
    public List<NodeKey> staleNodesUpTo(long versionInclusive) {
        requireNonNegativeVersion(versionInclusive);
        String sql = "SELECT node_path, node_version FROM " + schema.staleTable() +
                     " WHERE namespace = ? AND stale_since <= ? " +
                     "ORDER BY stale_since";

        List<NodeKey> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, versionInclusive);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] pathBytes = keyCodec.getKey(rs, "node_path");
                    long nodeVersion = rs.getLong("node_version");

                    NibblePath path = decodePath(pathBytes);
                    result.add(NodeKey.of(path, nodeVersion));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list stale nodes", e);
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public int pruneUpTo(long versionInclusive) {
        requireFormatInitialized();
        requireNonNegativeVersion(versionInclusive);
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance(
                "pruneUpTo", versionInclusive)) {
            Optional<VersionedRoot> latest = latestRoot();
            if (latest.isPresent() && versionInclusive > latest.get().version()) {
                throw new IllegalArgumentException("prune horizon exceeds latest version "
                        + latest.get().version());
            }
            return pruneUpToUnderLease(versionInclusive);
        }
    }

    private int pruneUpToUnderLease(long versionInclusive) {
        // Atomic transaction: delete stale nodes, delete stale markers, delete old values

        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();

            try {
                conn.setAutoCommit(false);
                int nodesPruned = pruneStaleNodes(conn, versionInclusive);
                int valuesPruned = pruneStaleValues(conn, versionInclusive);
                int rootsPruned = pruneOldRoots(conn, versionInclusive);
                int recordsPruned = nodesPruned + valuesPruned + rootsPruned;
                if (recordsPruned > 0) {
                    advancePruneWatermark(conn, versionInclusive);
                }

                conn.commit();
                return recordsPruned;

            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prune", e);
        }
    }

    private int pruneStaleNodes(Connection conn, long versionInclusive) throws SQLException {
        // 1. Delete nodes marked as stale
        String deleteNodesSql =
            "DELETE FROM " + schema.nodesTable() + " " +
            "WHERE namespace = ? AND (node_path, version) IN (" +
            "  SELECT node_path, node_version FROM " + schema.staleTable() +
            "  WHERE namespace = ? AND stale_since <= ?" +
            ")";

        int count = 0;
        try (PreparedStatement stmt = conn.prepareStatement(deleteNodesSql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setInt(2, keyPrefix & 0xFF);
            stmt.setLong(3, versionInclusive);
            count = stmt.executeUpdate();
        }

        // 2. Delete stale markers
        String deleteStaleMarkersSql =
            "DELETE FROM " + schema.staleTable() +
            " WHERE namespace = ? AND stale_since <= ?";

        try (PreparedStatement stmt = conn.prepareStatement(deleteStaleMarkersSql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, versionInclusive);
            stmt.executeUpdate();
        }

        return count;
    }

    private int pruneStaleValues(Connection conn, long versionInclusive) throws SQLException {
        // Safe prune: keep most recent value <= prune target for each key
        String deleteSql =
            "DELETE FROM " + schema.valuesTable() + " " +
            "WHERE namespace = ? AND version <= ? AND (key_hash, version) NOT IN (" +
            "  SELECT key_hash, MAX(version) FROM " + schema.valuesTable() +
            "  WHERE namespace = ? AND version <= ? " +
            "  GROUP BY key_hash" +
            ")";

        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, versionInclusive);
            stmt.setInt(3, keyPrefix & 0xFF);
            stmt.setLong(4, versionInclusive);
            return stmt.executeUpdate();
        }
    }

    private int pruneOldRoots(Connection conn, long versionExclusive) throws SQLException {
        String sql = "DELETE FROM " + schema.rootsTable()
                + " WHERE namespace = ? AND version < ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, versionExclusive);
            return stmt.executeUpdate();
        }
    }

    private long readPruneWatermark(Connection conn) throws SQLException {
        String sql = "SELECT prune_watermark FROM " + schema.metadataTable()
                + " WHERE namespace = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new JmtFormatMismatchException("RDBMS JMT format metadata is missing");
                }
                long watermark = rs.getLong("prune_watermark");
                return rs.wasNull() ? -1 : watermark;
            }
        }
    }

    private void advancePruneWatermark(Connection conn, long versionInclusive) throws SQLException {
        long current = readPruneWatermark(conn);
        if (current >= versionInclusive) {
            return;
        }
        String sql = "UPDATE " + schema.metadataTable()
                + " SET prune_watermark = ?, updated_at = CURRENT_TIMESTAMP WHERE namespace = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, versionInclusive);
            stmt.setInt(2, keyPrefix & 0xFF);
            if (stmt.executeUpdate() != 1) {
                throw new JmtFormatMismatchException("RDBMS JMT format metadata is missing");
            }
        }
    }

    @Override
    public void truncateAfter(long versionExclusive) {
        requireFormatInitialized();
        if (versionExclusive < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance(
                "truncateAfter", versionExclusive)) {
            truncateAfterUnderLease(versionExclusive);
        }
    }

    private void truncateAfterUnderLease(long versionExclusive) {
        // Rollback: delete everything strictly newer than versionExclusive in one transaction,
        // then repoint the "latest" row at the greatest surviving root (<= versionExclusive).
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                long pruneWatermark = readPruneWatermark(conn);
                if (pruneWatermark >= 0 && versionExclusive < pruneWatermark) {
                    throw new IllegalStateException("Cannot truncate to version "
                            + versionExclusive + " below prune watermark " + pruneWatermark);
                }
                deleteWhereVersionGreater(conn, schema.nodesTable(), "version", versionExclusive);
                deleteWhereVersionGreater(conn, schema.valuesTable(), "version", versionExclusive);
                deleteWhereVersionGreater(conn, schema.rootsTable(), "version", versionExclusive);
                deleteWhereVersionGreater(conn, schema.staleTable(), "stale_since", versionExclusive);

                // Recompute the latest pointer from the greatest surviving root.
                Long survivingVersion = null;
                byte[] survivingRoot = null;
                String maxSql = "SELECT version, root_hash FROM " + schema.rootsTable() +
                        " WHERE namespace = ? ORDER BY version DESC LIMIT 1";
                try (PreparedStatement stmt = conn.prepareStatement(maxSql)) {
                    stmt.setInt(1, keyPrefix & 0xFF);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            survivingVersion = rs.getLong("version");
                            survivingRoot = keyCodec.getKey(rs, "root_hash");
                        }
                    }
                }

                if (survivingVersion != null) {
                    String upsertSql = dialect.upsertLatestSql(schema.latestTable());
                    try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                        stmt.setInt(1, keyPrefix & 0xFF);
                        stmt.setLong(2, survivingVersion);
                        keyCodec.setKey(stmt, 3, survivingRoot);
                        stmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM " + schema.latestTable() + " WHERE namespace = ?")) {
                        stmt.setInt(1, keyPrefix & 0xFF);
                        stmt.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to truncate RDBMS JMT store", e);
        }
    }

    private void deleteWhereVersionGreater(Connection conn, String table, String versionColumn,
                                           long versionExclusive) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE namespace = ? AND " + versionColumn + " > ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, versionExclusive);
            stmt.executeUpdate();
        }
    }

    @Override
    public void close() {
        // The DataSource lifecycle belongs to the DbConfig that created it: close that DbConfig
        // (it is AutoCloseable) to release a pool this library opened via jdbcUrl()/simpleJdbcUrl().
        // Externally supplied data sources are the caller's to manage.
    }

    private byte[] encodePath(NibblePath path) {
        return NodeKey.of(path, 0L).toBytes();
    }

    private NibblePath decodePath(byte[] encodedPath) {
        if (encodedPath == null || encodedPath.length < 1 + Long.BYTES
                || encodedPath[0] != NODE_KEY_PREFIX) {
            throw new IllegalArgumentException("Malformed persisted JMT node path encoding");
        }
        NodeKey encoded = NodeKey.fromBytes(encodedPath);
        if (encoded.version() != 0) {
            throw new IllegalArgumentException("Persisted JMT node path has nonzero sentinel version");
        }
        return encoded.path();
    }

    private static void requireKeyHash(byte[] keyHash) {
        if (keyHash == null || keyHash.length != KEY_HASH_LENGTH) {
            throw new IllegalArgumentException("keyHash must be exactly " + KEY_HASH_LENGTH + " bytes");
        }
    }

    private static void requireNonNegativeVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
    }

    // ========== Inner Class: CommitBatch Implementation ==========

    private class RdbmsCommitBatch implements CommitBatch {
        private final long version;
        private final CommitConfig config;
        private final JmtAccessLease lease;
        private final Thread owner = Thread.currentThread();
        private final java.util.Map<NodeKey, JmtNode> nodeUpdates = new java.util.LinkedHashMap<>();
        private final java.util.Map<java.nio.ByteBuffer, byte[]> valueUpdates = new java.util.LinkedHashMap<>();
        private final java.util.Set<java.nio.ByteBuffer> valueDeletions = new java.util.LinkedHashSet<>();
        private final java.util.Set<NodeKey> staleNodes = new java.util.LinkedHashSet<>();
        private byte[] rootHash;
        private boolean closed;

        private RdbmsCommitBatch(long version, CommitConfig config, JmtAccessLease lease) {
            this.version = version;
            this.config = config;
            this.lease = lease;
        }

        @Override
        public void putNode(NodeKey nodeKey, JmtNode node) {
            ensureOpen();
            // Deduplicate in-memory like InMemoryJmtStore does (last write wins)
            nodeUpdates.put(nodeKey, node);
        }

        @Override
        public void markStale(NodeKey nodeKey) {
            ensureOpen();
            staleNodes.add(nodeKey);
        }

        @Override
        public void putValue(byte[] keyHash, byte[] value) {
            ensureOpen();
            // Deduplicate in-memory (last write wins)
            requireKeyHash(keyHash);
            java.nio.ByteBuffer key = java.nio.ByteBuffer.wrap(Arrays.copyOf(keyHash, keyHash.length));
            valueUpdates.put(key, Arrays.copyOf(value, value.length));
            valueDeletions.remove(key);
        }

        @Override
        public void deleteValue(byte[] keyHash) {
            ensureOpen();
            // Deduplicate in-memory (last write wins)
            requireKeyHash(keyHash);
            java.nio.ByteBuffer key = java.nio.ByteBuffer.wrap(Arrays.copyOf(keyHash, keyHash.length));
            valueUpdates.remove(key);
            valueDeletions.add(key);
        }

        @Override
        public void setRootHash(byte[] rootHash) {
            ensureOpen();
            this.rootHash = rootHash == null ? null : Arrays.copyOf(rootHash, rootHash.length);
        }

        @Override
        public void commit() {
            ensureOpen();
            try (Connection conn = dataSource.getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();

                try {
                    conn.setAutoCommit(false);
                    requireRootHash(rootHash);
                    Optional<VersionedRoot> latest = readLatestRoot(conn);
                    Optional<byte[]> committedRoot = readRootHash(conn);
                    if (!config.shouldApply(version, rootHash, latest, committedRoot)) {
                        conn.commit();
                        return;
                    }

                    // Claim the immutable version before writing any associated records. A
                    // concurrent store instance attempting the same version must lose this unique
                    // key race and roll back instead of mixing two batches.
                    insertRootHash(conn);

                    // Nodes are immutable and keyed by (namespace, node_path, version). The store
                    // guard above makes a committed-version replay a whole-batch no-op. Plain
                    // inserts make any unexpected key collision fail and roll back on every SQL
                    // backend instead of allowing H2 MERGE semantics to overwrite a row.
                    if (!nodeUpdates.isEmpty()) {
                        String nodeSql = "INSERT INTO " + schema.nodesTable()
                                + " (namespace, node_path, version, node_data) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(nodeSql)) {
                            for (java.util.Map.Entry<NodeKey, JmtNode> entry : nodeUpdates.entrySet()) {
                                NodeKey nodeKey = entry.getKey();
                                stmt.setInt(1, keyPrefix & 0xFF);
                                keyCodec.setKey(stmt, 2, encodePath(nodeKey.path()));
                                stmt.setLong(3, nodeKey.version());
                                keyCodec.setKey(stmt, 4, entry.getValue().encode());
                                stmt.addBatch();
                            }
                            stmt.executeBatch();
                        }
                    }

                    // Write deduplicated stale markers.
                    if (!staleNodes.isEmpty()) {
                        String staleSql = "INSERT INTO " + schema.staleTable()
                                + " (namespace, stale_since, node_path, node_version)"
                                + " VALUES (?, ?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(staleSql)) {
                            for (NodeKey nodeKey : staleNodes) {
                                stmt.setInt(1, keyPrefix & 0xFF);
                                stmt.setLong(2, version);
                                keyCodec.setKey(stmt, 3, encodePath(nodeKey.path()));
                                stmt.setLong(4, nodeKey.version());
                                stmt.addBatch();
                            }
                            stmt.executeBatch();
                        }
                    }

                    // Write value updates. The in-memory staging map has already deduplicated keys.
                    if (!valueUpdates.isEmpty()) {
                        String valueSql = "INSERT INTO " + schema.valuesTable()
                                + " (namespace, key_hash, version, value_data, is_tombstone)"
                                + " VALUES (?, ?, ?, ?, FALSE)";
                        try (PreparedStatement stmt = conn.prepareStatement(valueSql)) {
                            for (java.util.Map.Entry<java.nio.ByteBuffer, byte[]> entry : valueUpdates.entrySet()) {
                                stmt.setInt(1, keyPrefix & 0xFF);
                                keyCodec.setKey(stmt, 2, entry.getKey().array());
                                stmt.setLong(3, version);
                                keyCodec.setKey(stmt, 4, entry.getValue());
                                stmt.addBatch();
                            }
                            stmt.executeBatch();
                        }
                    }

                    // Write value deletions. Staging makes updates and tombstones mutually exclusive.
                    if (!valueDeletions.isEmpty()) {
                        String tombstoneSql = "INSERT INTO " + schema.valuesTable()
                                + " (namespace, key_hash, version, value_data, is_tombstone)"
                                + " VALUES (?, ?, ?, NULL, TRUE)";
                        try (PreparedStatement stmt = conn.prepareStatement(tombstoneSql)) {
                            for (java.nio.ByteBuffer keyHash : valueDeletions) {
                                stmt.setInt(1, keyPrefix & 0xFF);
                                keyCodec.setKey(stmt, 2, keyHash.array());
                                stmt.setLong(3, version);
                                stmt.addBatch();
                            }
                            stmt.executeBatch();
                        }
                    }

                    // Publish the root with a storage-level compare-and-set. This closes the race
                    // between separate H2/SQLite store instances as well as PostgreSQL: if another
                    // transaction changed the base observed above, every staged write rolls back.
                    compareAndSetLatestRoot(conn, latest);

                    conn.commit();

                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to commit batch", e);
            } finally {
                closeInternal();
            }
        }

        private Optional<VersionedRoot> readLatestRoot(Connection conn) throws SQLException {
            String sql = "SELECT latest_version, latest_root FROM " + schema.latestTable()
                    + " WHERE namespace = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new VersionedRoot(rs.getLong("latest_version"),
                            keyCodec.getKey(rs, "latest_root")));
                }
            }
        }

        private Optional<byte[]> readRootHash(Connection conn) throws SQLException {
            String sql = "SELECT root_hash FROM " + schema.rootsTable()
                    + " WHERE namespace = ? AND version = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, version);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next()
                            ? Optional.of(keyCodec.getKey(rs, "root_hash"))
                            : Optional.empty();
                }
            }
        }

        private void insertRootHash(Connection conn) throws SQLException {
            String sql = "INSERT INTO " + schema.rootsTable()
                    + " (namespace, version, root_hash) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, version);
                keyCodec.setKey(stmt, 3, rootHash);
                try {
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    if (isConstraintViolation(e)) {
                        throw new JmtWriteConflictException("Version " + version
                                + " was claimed concurrently by another JMT commit");
                    }
                    throw e;
                }
            }
        }

        private void compareAndSetLatestRoot(Connection conn,
                                             Optional<VersionedRoot> expectedLatest)
                throws SQLException {
            int changed;
            if (expectedLatest.isEmpty()) {
                String sql = "INSERT INTO " + schema.latestTable()
                        + " (namespace, latest_version, latest_root, updated_at)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, keyPrefix & 0xFF);
                    stmt.setLong(2, version);
                    keyCodec.setKey(stmt, 3, rootHash);
                    try {
                        changed = stmt.executeUpdate();
                    } catch (SQLException e) {
                        if (isConstraintViolation(e)) {
                            throw new JmtWriteConflictException("Latest JMT root was initialized "
                                    + "concurrently while committing version " + version);
                        }
                        throw e;
                    }
                }
            } else {
                String sql = "UPDATE " + schema.latestTable()
                        + " SET latest_version = ?, latest_root = ?, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE namespace = ? AND latest_version = ? AND latest_root = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, version);
                    keyCodec.setKey(stmt, 2, rootHash);
                    stmt.setInt(3, keyPrefix & 0xFF);
                    stmt.setLong(4, expectedLatest.get().version());
                    keyCodec.setKey(stmt, 5, expectedLatest.get().rootHash());
                    changed = stmt.executeUpdate();
                }
            }
            if (changed != 1) {
                throw new JmtWriteConflictException("Latest JMT root changed while committing version "
                        + version);
            }
        }

        private boolean isConstraintViolation(SQLException exception) {
            String sqlState = exception.getSQLState();
            return (sqlState != null && sqlState.startsWith("23"))
                    || ("SQLite".equalsIgnoreCase(dialect.name())
                    && exception.getErrorCode() == 19);
        }

        @Override
        public void close() {
            closeInternal();
        }

        private void ensureOpen() {
            ensureOwner();
            if (closed) {
                throw new IllegalStateException("CommitBatch already closed");
            }
        }

        private void closeInternal() {
            ensureOwner();
            if (closed) {
                return;
            }
            closed = true;
            lease.close();
        }

        private void ensureOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("CommitBatch must be used by its creating thread");
            }
        }

        private void requireRootHash(byte[] hash) {
            int expectedLength = formatDescriptor.hashLength();
            if (hash == null || hash.length != expectedLength) {
                throw new IllegalStateException("Commit root hash must be exactly "
                        + expectedLength + " bytes");
            }
        }
    }

    private static final class InspectionAccumulator {
        private final int maxRecords;
        private int count;
        private boolean truncated;
        private VersionedRoot latestRoot;
        private final List<VersionedRoot> roots = new ArrayList<>();
        private final List<JmtStoreInspection.NodeRecord> nodes = new ArrayList<>();
        private final List<JmtStoreInspection.ValueRecord> values = new ArrayList<>();
        private final List<JmtStoreInspection.StaleRecord> stale = new ArrayList<>();
        private final List<String> backendIssues = new ArrayList<>();

        private InspectionAccumulator(int maxRecords) {
            this.maxRecords = maxRecords;
        }

        private boolean take() {
            if (count >= maxRecords) {
                truncated = true;
                return false;
            }
            count++;
            return true;
        }

        private JmtStoreInspection snapshot() {
            return new JmtStoreInspection(roots, latestRoot, nodes, values, stale,
                    backendIssues, truncated);
        }
    }
}
