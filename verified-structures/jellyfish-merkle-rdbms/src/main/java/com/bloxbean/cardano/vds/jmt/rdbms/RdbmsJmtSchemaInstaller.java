package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.rdbms.common.BundledSchema;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.common.TableNameResolver;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;

import java.util.List;

/**
 * Creates the JMT RDBMS tables ({@code jmt_nodes}, {@code jmt_values}, {@code jmt_roots}, {@code jmt_latest},
 * {@code jmt_stale}, {@code jmt_metadata}) and their indexes.
 *
 * <p>{@link RdbmsJmtStore} never creates tables on its own. Call {@link #createIfNotExists(DbConfig)} once before
 * opening the store, or apply the bundled DDL ({@code /ddl/jmt/{h2,postgres,sqlite}/schema.sql}) with your own
 * migration tool.</p>
 *
 * <p>The bundled DDL only uses {@code CREATE ... IF NOT EXISTS}, so calling this method repeatedly is safe.
 * Table and index names honour {@link DbConfig#tablePrefix()}: with prefix {@code "acct"} the tables are
 * {@code acct_jmt_nodes}, {@code acct_jmt_values} and so on, and the indexes are prefixed the same way, so several
 * prefixed schemas can live in one database.</p>
 *
 * <pre>{@code
 * DbConfig config = DbConfig.builder()
 *     .jdbcUrl("jdbc:postgresql://localhost:5432/jmt", "user", "password")
 *     .build();
 * RdbmsJmtSchemaInstaller.createIfNotExists(config);
 * try (RdbmsJmtStore store = new RdbmsJmtStore(config)) {
 *     JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
 *     ...
 * }
 * }</pre>
 */
public final class RdbmsJmtSchemaInstaller {

    /** Base names used in the bundled DDL, rewritten through {@link TableNameResolver}. */
    private static final BundledSchema DDL = new BundledSchema("JMT", RdbmsJmtSchemaInstaller.class, "/ddl/jmt",
            "jmt_nodes", "jmt_values", "jmt_roots", "jmt_latest", "jmt_stale", "jmt_metadata",
            "idx_jmt_nodes_ns_path", "idx_jmt_nodes_ns_ver", "idx_jmt_values_ns_key", "idx_jmt_values_ns_ver",
            "idx_jmt_roots_ns", "idx_jmt_stale_ns_since");

    /** Longest identifier allowed for a generated table or index name. */
    public static final int MAX_IDENTIFIER_LENGTH = BundledSchema.MAX_IDENTIFIER_LENGTH;

    private RdbmsJmtSchemaInstaller() {
    }

    /**
     * Creates the JMT tables and indexes if they do not exist yet.
     *
     * <p>If the data source hands out connections with auto-commit off, all statements run in one transaction,
     * which is committed at the end or rolled back on failure. With auto-commit on (the JDBC default) each
     * statement commits on its own; after a failure, fix the cause and call this method again, since every
     * statement is idempotent.</p>
     *
     * @param config the database configuration (data source, dialect and optional table prefix)
     * @throws IllegalArgumentException if the dialect has no bundled DDL, or the prefix is invalid or makes a
     *                                  generated name longer than {@link #MAX_IDENTIFIER_LENGTH}
     * @throws IllegalStateException    if the DDL cannot be read or executed
     */
    public static void createIfNotExists(DbConfig config) {
        DDL.createIfNotExists(config);
    }

    /**
     * Returns the DDL statements {@link #createIfNotExists(DbConfig)} would run, with prefixed names.
     *
     * @param dialect     the SQL dialect
     * @param tablePrefix the table prefix, or empty/null for none
     * @return the statements, in execution order
     * @throws IllegalArgumentException if the dialect has no bundled DDL, or the prefix is invalid or makes a
     *                                  generated name longer than {@link #MAX_IDENTIFIER_LENGTH}
     */
    public static List<String> statements(SqlDialect dialect, String tablePrefix) {
        return DDL.statements(dialect, tablePrefix);
    }
}
