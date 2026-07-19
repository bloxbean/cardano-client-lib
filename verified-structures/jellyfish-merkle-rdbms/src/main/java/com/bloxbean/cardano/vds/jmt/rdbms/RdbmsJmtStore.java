package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.JmtEncoding;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.NodeKey;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
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
import java.util.Optional;

/**
 * RDBMS implementation of {@link JmtStore}.
 *
 * <p>Provides database-neutral persistence for Jellyfish Merkle Trees using standard SQL.
 * Thread-safety is provided by the underlying JDBC DataSource connection pooling.
 *
 * @since 0.8.0
 */
public class RdbmsJmtStore implements JmtStore {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final RdbmsJmtSchema schema;
    private final byte keyPrefix;
    private final KeyCodec keyCodec;
    private static final byte NODE_KEY_PREFIX = 0x4E; // 'N'
    private static final int KEY_HASH_LENGTH = 32;

    /**
     * Creates a JMT store with the specified configuration and namespace.
     *
     * @param config the database configuration
     * @param keyPrefix the key prefix (namespace ID, 0-255)
     */
    public RdbmsJmtStore(DbConfig config, byte keyPrefix) {
        this.dataSource = config.dataSource();
        this.dialect = config.dialect();
        this.schema = new RdbmsJmtSchema(config.tablePrefix());
        this.keyPrefix = keyPrefix;
        this.keyCodec = dialect.keyCodec();
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
        // Floor lookup: newest node on path with version <= requested version
        // CRITICAL: Must filter stale nodes like InMemoryJmtStore does
        String sql = "SELECT node_path, version, node_data FROM " + schema.nodesTable() +
                     " WHERE namespace = ? AND node_path = ? AND version <= ? " +
                     "  AND NOT EXISTS (" +
                     "    SELECT 1 FROM " + schema.staleTable() +
                     "    WHERE " + schema.staleTable() + ".namespace = " + schema.nodesTable() + ".namespace" +
                     "      AND " + schema.staleTable() + ".node_path = " + schema.nodesTable() + ".node_path" +
                     "      AND " + schema.staleTable() + ".node_version = " + schema.nodesTable() + ".version" +
                     "      AND " + schema.staleTable() + ".stale_since <= ?" +
                     "  )" +
                     " ORDER BY version DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, encodePath(path));
            stmt.setLong(3, version);
            stmt.setLong(4, version); // stale_since parameter

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
        // For RDBMS, floor lookup is the same as getNode
        return getNode(version, path);
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
        return new RdbmsCommitBatch(version);
    }

    @Override
    public List<NodeKey> staleNodesUpTo(long versionInclusive) {
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
        // Atomic transaction: delete stale nodes, delete stale markers, delete old values

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                int nodesPruned = pruneStaleNodes(conn, versionInclusive);
                int valuesPruned = pruneStaleValues(conn, versionInclusive);

                conn.commit();
                return nodesPruned + valuesPruned;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
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

    @Override
    public void truncateAfter(long versionExclusive) {
        if (versionExclusive < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        // Rollback: delete everything strictly newer than versionExclusive in one transaction,
        // then repoint the "latest" row at the greatest surviving root (<= versionExclusive).
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
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
            } catch (SQLException e) {
                conn.rollback();
                throw e;
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
        if (encodedPath == null || encodedPath.length == 0) {
            return NibblePath.EMPTY;
        }
        if (encodedPath[0] == NODE_KEY_PREFIX && encodedPath.length >= 1 + Long.BYTES) {
            try {
                return NodeKey.fromBytes(encodedPath).path();
            } catch (IllegalArgumentException ignored) {
                // Fall through to legacy decoding path
            }
        }
        return NibblePath.fromBytes(encodedPath);
    }

    private static void requireKeyHash(byte[] keyHash) {
        if (keyHash == null || keyHash.length != KEY_HASH_LENGTH) {
            throw new IllegalArgumentException("keyHash must be exactly " + KEY_HASH_LENGTH + " bytes");
        }
    }

    // ========== Inner Class: CommitBatch Implementation ==========

    private class RdbmsCommitBatch implements CommitBatch {
        private final long version;
        private final List<BatchOperation> operations = new ArrayList<>();
        private final java.util.Map<NodeKey, JmtNode> nodeUpdates = new java.util.LinkedHashMap<>();
        private final java.util.Map<java.nio.ByteBuffer, byte[]> valueUpdates = new java.util.LinkedHashMap<>();
        private final java.util.Set<java.nio.ByteBuffer> valueDeletions = new java.util.LinkedHashSet<>();
        private final java.util.List<NodeKey> staleNodes = new java.util.ArrayList<>();
        private byte[] rootHash;

        private RdbmsCommitBatch(long version) {
            this.version = version;
        }

        @Override
        public void putNode(NodeKey nodeKey, JmtNode node) {
            // Deduplicate in-memory like InMemoryJmtStore does (last write wins)
            nodeUpdates.put(nodeKey, node);
        }

        @Override
        public void markStale(NodeKey nodeKey) {
            staleNodes.add(nodeKey);
        }

        @Override
        public void putValue(byte[] keyHash, byte[] value) {
            // Deduplicate in-memory (last write wins)
            requireKeyHash(keyHash);
            java.nio.ByteBuffer key = java.nio.ByteBuffer.wrap(Arrays.copyOf(keyHash, keyHash.length));
            valueUpdates.put(key, Arrays.copyOf(value, value.length));
            valueDeletions.remove(key);
        }

        @Override
        public void deleteValue(byte[] keyHash) {
            // Deduplicate in-memory (last write wins)
            requireKeyHash(keyHash);
            java.nio.ByteBuffer key = java.nio.ByteBuffer.wrap(Arrays.copyOf(keyHash, keyHash.length));
            valueUpdates.remove(key);
            valueDeletions.add(key);
        }

        @Override
        public void setRootHash(byte[] rootHash) {
            this.rootHash = rootHash == null ? null : Arrays.copyOf(rootHash, rootHash.length);
        }

        @Override
        public void commit() {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Nodes are immutable and keyed by (namespace, node_path, version). Use an
                    // insert-or-ignore so replaying an already-committed version (crash recovery)
                    // is a no-op across all dialects, instead of relying on a Postgres-only SQLState.
                    // Each table is written with a single batched statement.
                    if (!nodeUpdates.isEmpty()) {
                        String nodeSql = dialect.insertOrIgnoreSql(schema.nodesTable(),
                                "namespace, node_path, version, node_data", "?, ?, ?, ?");
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

                    // Write stale markers (idempotent).
                    if (!staleNodes.isEmpty()) {
                        String staleSql = dialect.insertOrIgnoreSql(schema.staleTable(),
                                "namespace, stale_since, node_path, node_version", "?, ?, ?, ?");
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

                    // Write value updates (idempotent).
                    if (!valueUpdates.isEmpty()) {
                        String valueSql = dialect.insertOrIgnoreSql(schema.valuesTable(),
                                "namespace, key_hash, version, value_data, is_tombstone", "?, ?, ?, ?, FALSE");
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

                    // Write value deletions (idempotent tombstones).
                    if (!valueDeletions.isEmpty()) {
                        String tombstoneSql = dialect.insertOrIgnoreSql(schema.valuesTable(),
                                "namespace, key_hash, version, value_data, is_tombstone", "?, ?, ?, NULL, TRUE");
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

                    // Execute any remaining operations (shouldn't be any now)
                    for (BatchOperation op : operations) {
                        op.execute(conn);
                    }

                    // Store root hash
                    if (rootHash != null) {
                        storeRootHash(conn);
                    }

                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to commit batch", e);
            }
        }

        private void storeRootHash(Connection conn) throws SQLException {
            // A committed version's root is immutable. Replaying the SAME version with the SAME root
            // is an idempotent no-op; replaying it with a DIFFERENT root is a divergent commit that
            // would leave roots/latest inconsistent, so reject it loudly (rolls back the transaction).
            byte[] existingRoot = null;
            String selectRootSql = "SELECT root_hash FROM " + schema.rootsTable() +
                    " WHERE namespace = ? AND version = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectRootSql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, version);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        existingRoot = keyCodec.getKey(rs, "root_hash");
                    }
                }
            }
            if (existingRoot != null) {
                if (!Arrays.equals(existingRoot, rootHash)) {
                    throw new SQLException("Version " + version +
                            " already committed with a different root hash (divergent replay)");
                }
                // identical replay: root row already present.
            } else {
                String insertRootSql = "INSERT INTO " + schema.rootsTable() +
                        " (namespace, version, root_hash) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertRootSql)) {
                    stmt.setInt(1, keyPrefix & 0xFF);
                    stmt.setLong(2, version);
                    keyCodec.setKey(stmt, 3, rootHash);
                    stmt.executeUpdate();
                }
            }

            // The latest pointer is monotonic: only advance it. Replaying an OLDER already-committed
            // version must not regress latestRoot() to that older state.
            long currentLatest = -1L;
            boolean haveLatest = false;
            String latestSql = "SELECT latest_version FROM " + schema.latestTable() + " WHERE namespace = ?";
            try (PreparedStatement stmt = conn.prepareStatement(latestSql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        currentLatest = rs.getLong("latest_version");
                        haveLatest = true;
                    }
                }
            }
            if (!haveLatest || Long.compareUnsigned(version, currentLatest) >= 0) {
                String upsertSql = dialect.upsertLatestSql(schema.latestTable());
                try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                    stmt.setInt(1, keyPrefix & 0xFF);
                    stmt.setLong(2, version);
                    keyCodec.setKey(stmt, 3, rootHash);
                    stmt.executeUpdate();
                }
            }
        }

        @Override
        public void close() {
            operations.clear();
        }
    }

    @FunctionalInterface
    private interface BatchOperation {
        void execute(Connection conn) throws SQLException;
    }
}
