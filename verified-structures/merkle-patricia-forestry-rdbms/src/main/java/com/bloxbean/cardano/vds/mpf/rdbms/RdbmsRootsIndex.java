package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.api.RootsIndex;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * RDBMS implementation of {@link RootsIndex} for versioned root hash management.
 *
 * <p>Stores root hashes in the {@code mpt_roots} table with version tracking,
 * and maintains a fast-lookup "latest" pointer in the {@code mpt_latest} table.</p>
 *
 * <p>Supports the same ThreadLocal transaction context as {@link RdbmsNodeStore}
 * for atomic operations across both nodes and roots.</p>
 *
 * @since 0.8.0
 */
public class RdbmsRootsIndex implements RootsIndex, AutoCloseable {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final RdbmsMptSchema schema;
    private final byte keyPrefix;
    private final KeyCodec keyCodec;

    /**
     * Creates a roots index with the specified configuration and namespace.
     *
     * @param config    the database configuration
     * @param keyPrefix the namespace ID (0-255)
     */
    public RdbmsRootsIndex(DbConfig config, byte keyPrefix) {
        this.dataSource = config.dataSource();
        this.dialect = config.dialect();
        this.schema = new RdbmsMptSchema(config.tablePrefix());
        this.keyPrefix = keyPrefix;
        this.keyCodec = dialect.keyCodec();
    }

    /**
     * Creates a roots index with default namespace (0x00).
     *
     * @param config the database configuration
     */
    public RdbmsRootsIndex(DbConfig config) {
        this(config, (byte) 0x00);
    }

    @Override
    public void put(long versionOrSlot, byte[] rootHash) {
        Connection conn = RdbmsNodeStore.currentConnection();
        if (conn != null) {
            putWithConnection(conn, versionOrSlot, rootHash);
        } else {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                putWithConnection(c, versionOrSlot, rootHash);
                c.commit();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to store root hash", e);
            }
        }
    }

    private void putWithConnection(Connection conn, long versionOrSlot, byte[] rootHash) {
        try {
            // Delete existing root at this version (if any) then insert new one.
            // This handles the case where version 0 is overwritten in SINGLE_VERSION mode.
            String deleteSql = "DELETE FROM " + schema.rootsTable() +
                               " WHERE namespace = ? AND version = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, versionOrSlot);
                stmt.executeUpdate();
            }

            String insertSql = "INSERT INTO " + schema.rootsTable() +
                                " (namespace, version, root_hash) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, versionOrSlot);
                keyCodec.setKey(stmt, 3, rootHash);
                stmt.executeUpdate();
            }

            // Upsert into mpt_latest
            String upsertSql = dialect.upsertLatestSql(schema.latestTable());
            try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                stmt.setInt(1, keyPrefix & 0xFF);
                stmt.setLong(2, versionOrSlot);
                keyCodec.setKey(stmt, 3, rootHash);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store root hash", e);
        }
    }

    @Override
    public byte[] get(long versionOrSlot) {
        Connection conn = RdbmsNodeStore.currentConnection();
        if (conn != null) {
            return getWithConnection(conn, versionOrSlot);
        }
        try (Connection c = dataSource.getConnection()) {
            return getWithConnection(c, versionOrSlot);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve root hash", e);
        }
    }

    private byte[] getWithConnection(Connection conn, long versionOrSlot) {
        String sql = "SELECT root_hash FROM " + schema.rootsTable() +
                     " WHERE namespace = ? AND version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, versionOrSlot);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return keyCodec.getKey(rs, "root_hash");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve root hash for version " + versionOrSlot, e);
        }
    }

    @Override
    public byte[] latest() {
        Connection conn = RdbmsNodeStore.currentConnection();
        if (conn != null) {
            return latestWithConnection(conn);
        }
        try (Connection c = dataSource.getConnection()) {
            return latestWithConnection(c);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve latest root hash", e);
        }
    }

    private byte[] latestWithConnection(Connection conn) {
        String sql = "SELECT latest_root FROM " + schema.latestTable() +
                     " WHERE namespace = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return keyCodec.getKey(rs, "latest_root");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve latest root hash", e);
        }
    }

    /**
     * Returns the highest version number that has been stored.
     *
     * @return the last stored version, or -1 if no versions have been stored
     */
    public long lastVersion() {
        Connection conn = RdbmsNodeStore.currentConnection();
        if (conn != null) {
            return lastVersionWithConnection(conn);
        }
        try (Connection c = dataSource.getConnection()) {
            return lastVersionWithConnection(c);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve last version", e);
        }
    }

    private long lastVersionWithConnection(Connection conn) {
        String sql = "SELECT latest_version FROM " + schema.latestTable() +
                     " WHERE namespace = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("latest_version");
                }
                return -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve last version", e);
        }
    }

    /**
     * Returns the next available version number.
     *
     * @return the next version number
     */
    public long nextVersion() {
        return lastVersion() + 1;
    }

    /**
     * Returns a sorted view of all stored versions and their root hashes.
     *
     * @return a NavigableMap of all version-root pairs, sorted by version
     */
    public NavigableMap<Long, byte[]> listAll() {
        NavigableMap<Long, byte[]> map = new TreeMap<>();
        String sql = "SELECT version, root_hash FROM " + schema.rootsTable() +
                     " WHERE namespace = ? ORDER BY version";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long version = rs.getLong("version");
                    byte[] rootHash = keyCodec.getKey(rs, "root_hash");
                    map.put(version, rootHash);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list all roots", e);
        }
        return map;
    }

    /**
     * Returns all versions in the specified range [fromInclusive, toInclusive].
     *
     * @param fromInclusive the starting version (inclusive)
     * @param toInclusive   the ending version (inclusive)
     * @return a NavigableMap of version-root pairs within the range
     */
    public NavigableMap<Long, byte[]> listRange(long fromInclusive, long toInclusive) {
        NavigableMap<Long, byte[]> map = new TreeMap<>();
        if (fromInclusive > toInclusive) return map;

        String sql = "SELECT version, root_hash FROM " + schema.rootsTable() +
                     " WHERE namespace = ? AND version >= ? AND version <= ? ORDER BY version";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, keyPrefix & 0xFF);
            stmt.setLong(2, fromInclusive);
            stmt.setLong(3, toInclusive);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long version = rs.getLong("version");
                    byte[] rootHash = keyCodec.getKey(rs, "root_hash");
                    map.put(version, rootHash);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list roots in range", e);
        }
        return map;
    }

    @Override
    public void close() {
        // DataSource managed externally
    }
}
