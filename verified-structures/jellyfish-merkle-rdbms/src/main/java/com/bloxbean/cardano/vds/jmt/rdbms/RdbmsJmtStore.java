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
    public void close() {
        // DataSource is managed externally, no cleanup needed here
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
            java.nio.ByteBuffer key = java.nio.ByteBuffer.wrap(keyHash);
            valueUpdates.put(key, value);
            valueDeletions.remove(key);
        }

        @Override
        public void deleteValue(byte[] keyHash) {
            // Deduplicate in-memory (last write wins)
            java.nio.ByteBuffer key = java.nio.ByteBuffer.wrap(keyHash);
            valueUpdates.remove(key);
            valueDeletions.add(key);
        }

        @Override
        public void setRootHash(byte[] rootHash) {
            this.rootHash = rootHash;
        }

        @Override
        public void commit() {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Write deduplicated nodes
                    // Since we deduplicated in-memory, use plain INSERT (nodes are immutable)
                    // Duplicates should only occur if same (path,version) already exists from previous commit
                    for (java.util.Map.Entry<NodeKey, JmtNode> entry : nodeUpdates.entrySet()) {
                        NodeKey nodeKey = entry.getKey();
                        JmtNode node = entry.getValue();
                        String sql = "INSERT INTO " + schema.nodesTable() +
                                   " (namespace, node_path, version, node_data) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, keyPrefix & 0xFF);
                            keyCodec.setKey(stmt, 2, encodePath(nodeKey.path()));
                            stmt.setLong(3, nodeKey.version());
                            keyCodec.setKey(stmt, 4, node.encode());
                            try {
                                stmt.executeUpdate();
                            } catch (SQLException e) {
                                // Ignore duplicate key errors (node already exists from previous commit)
                                String sqlState = e.getSQLState();
                                if (!"23505".equals(sqlState) && e.getErrorCode() != 23505) {
                                    throw e;
                                }
                                // Otherwise silently ignore - node reused from previous version
                            }
                        }
                    }

                    // Write stale markers
                    for (NodeKey nodeKey : staleNodes) {
                        String sql = "INSERT INTO " + schema.staleTable() +
                                     " (namespace, stale_since, node_path, node_version) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, keyPrefix & 0xFF);
                            stmt.setLong(2, version);
                            keyCodec.setKey(stmt, 3, encodePath(nodeKey.path()));
                            stmt.setLong(4, nodeKey.version());
                            stmt.executeUpdate();
                        }
                    }

                    // Write value updates
                    for (java.util.Map.Entry<java.nio.ByteBuffer, byte[]> entry : valueUpdates.entrySet()) {
                        String sql = "INSERT INTO " + schema.valuesTable() +
                                     " (namespace, key_hash, version, value_data, is_tombstone) " +
                                     "VALUES (?, ?, ?, ?, FALSE)";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, keyPrefix & 0xFF);
                            keyCodec.setKey(stmt, 2, entry.getKey().array());
                            stmt.setLong(3, version);
                            keyCodec.setKey(stmt, 4, entry.getValue());
                            stmt.executeUpdate();
                        }
                    }

                    // Write value deletions
                    for (java.nio.ByteBuffer keyHash : valueDeletions) {
                        String sql = "INSERT INTO " + schema.valuesTable() +
                                     " (namespace, key_hash, version, value_data, is_tombstone) " +
                                     "VALUES (?, ?, ?, NULL, TRUE)";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, keyPrefix & 0xFF);
                            keyCodec.setKey(stmt, 2, keyHash.array());
                            stmt.setLong(3, version);
                            stmt.executeUpdate();
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
            // Insert into roots table
            String insertRootSql = "INSERT INTO " + schema.rootsTable() +
                                   " (namespace, version, root_hash) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertRootSql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, version);
                keyCodec.setKey(stmt, 3, rootHash);
                stmt.executeUpdate();
            }

            // Upsert into latest table
            String upsertSql = dialect.upsertLatestSql(schema.latestTable());
            try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, version);
                keyCodec.setKey(stmt, 3, rootHash);
                stmt.executeUpdate();
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
