package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * RDBMS implementation of {@link NodeStore} for Merkle Patricia Trie.
 *
 * <p>Provides database-neutral persistence for MPT nodes using standard SQL.
 * Thread-safety is provided by the underlying JDBC DataSource connection pooling.
 *
 * <p><b>Batch Operations:</b> For atomic multi-operation transactions, use
 * {@link #withTransaction(TransactionCallback)}.</p>
 *
 * @since 0.8.0
 */
public class RdbmsNodeStore implements NodeStore, AutoCloseable {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final RdbmsMptSchema schema;
    private final byte keyPrefix;
    private final KeyCodec keyCodec;

    // ThreadLocal for batch operations (matches RocksDB pattern)
    private static final ThreadLocal<Connection> TL_CONNECTION = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, byte[]>> TL_STAGED = new ThreadLocal<>();

    /**
     * Creates an MPT node store with the specified configuration and namespace.
     *
     * @param config the database configuration
     * @param keyPrefix the key prefix (namespace ID, 0-255)
     */
    public RdbmsNodeStore(DbConfig config, byte keyPrefix) {
        this.dataSource = config.dataSource();
        this.dialect = config.dialect();
        this.schema = new RdbmsMptSchema(config.tablePrefix());
        this.keyPrefix = keyPrefix;
        this.keyCodec = dialect.keyCodec();
    }

    /**
     * Creates an MPT node store with default namespace (0x00).
     *
     * @param config the database configuration
     */
    public RdbmsNodeStore(DbConfig config) {
        this(config, (byte) 0x00);
    }

    @Override
    public byte[] get(byte[] hash) {
        // Check ThreadLocal cache first (for batch read-your-writes)
        Map<String, byte[]> staged = TL_STAGED.get();
        if (staged != null) {
            String key = keyToString(hash);
            byte[] cached = staged.get(key);
            if (cached != null) return cached;
            // Check if it was deleted in this transaction
            if (staged.containsKey(key) && staged.get(key) == null) {
                return null;
            }
        }

        // Check if we're in a batch transaction
        Connection batchConn = TL_CONNECTION.get();
        if (batchConn != null) {
            return getFromConnection(batchConn, hash);
        }

        // Normal read
        try (Connection conn = dataSource.getConnection()) {
            return getFromConnection(conn, hash);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get node", e);
        }
    }

    private byte[] getFromConnection(Connection conn, byte[] hash) {
        String sql = "SELECT node_data FROM " + schema.nodesTable() +
                     " WHERE namespace = ? AND node_hash = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, hash);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return keyCodec.getKey(rs, "node_data");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get node", e);
        }
    }

    @Override
    public void put(byte[] hash, byte[] nodeBytes) {
        Connection batchConn = TL_CONNECTION.get();

        if (batchConn != null) {
            // Batch mode: stage operation
            try {
                putToConnection(batchConn, hash, nodeBytes);

                // Update staged cache for read-your-writes
                Map<String, byte[]> staged = TL_STAGED.get();
                if (staged == null) {
                    staged = new HashMap<>();
                    TL_STAGED.set(staged);
                }
                staged.put(keyToString(hash), nodeBytes);

            } catch (SQLException e) {
                throw new RuntimeException("Failed to stage node", e);
            }
        } else {
            // Normal mode: immediate write
            try (Connection conn = dataSource.getConnection()) {
                putToConnection(conn, hash, nodeBytes);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to put node", e);
            }
        }
    }

    private void putToConnection(Connection conn, byte[] hash, byte[] nodeBytes)
            throws SQLException {
        // Use INSERT OR IGNORE / ON CONFLICT DO NOTHING for idempotency
        String sql = dialect.insertOrIgnoreSql(
            schema.nodesTable(),
            "namespace, node_hash, node_data",
            "?, ?, ?"
        );

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, hash);
            keyCodec.setKey(stmt, 3, nodeBytes);
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(byte[] hash) {
        Connection batchConn = TL_CONNECTION.get();

        if (batchConn != null) {
            try {
                deleteFromConnection(batchConn, hash);

                // Mark as deleted in staged cache
                Map<String, byte[]> staged = TL_STAGED.get();
                if (staged != null) {
                    staged.put(keyToString(hash), null);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to stage delete", e);
            }
        } else {
            try (Connection conn = dataSource.getConnection()) {
                deleteFromConnection(conn, hash);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete node", e);
            }
        }
    }

    private void deleteFromConnection(Connection conn, byte[] hash) throws SQLException {
        String sql = "DELETE FROM " + schema.nodesTable() +
                     " WHERE namespace = ? AND node_hash = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            keyCodec.setKey(stmt, 2, hash);
            stmt.executeUpdate();
        }
    }

    /**
     * Executes operations within a transaction.
     *
     * <p>All NodeStore operations within the callback are executed atomically.
     * Changes are committed on success or rolled back on exception.
     *
     * <p>Provides read-your-writes consistency via ThreadLocal staging.
     *
     * @param <T> the return type
     * @param work the work to execute
     * @return the result of the work
     * @throws RuntimeException if the work throws an exception
     */
    public <T> T withTransaction(TransactionCallback<T> work) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            TL_CONNECTION.set(conn);
            TL_STAGED.set(new HashMap<>());

            T result = work.execute();
            conn.commit();
            return result;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new RuntimeException("Transaction failed", e);

        } finally {
            TL_CONNECTION.remove();
            TL_STAGED.remove();
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * Callback for transaction execution.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute() throws Exception;
    }

    @Override
    public void close() {
        // DataSource managed externally
    }

    private String keyToString(byte[] key) {
        return java.util.Arrays.toString(key);
    }
}
