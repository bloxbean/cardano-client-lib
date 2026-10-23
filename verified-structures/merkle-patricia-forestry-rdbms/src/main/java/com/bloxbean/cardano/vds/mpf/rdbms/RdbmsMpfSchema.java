package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.rdbms.common.BundledSchema;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.common.TableNameResolver;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;

import java.util.List;

/**
 * Creates the MPF RDBMS tables ({@code mpt_nodes}, {@code mpt_roots}, {@code mpt_latest}) and their indexes.
 *
 * <p>The RDBMS stores never create tables on their own. Call {@link #createIfNotExists(DbConfig)} once before
 * opening {@link RdbmsNodeStore}, {@link RdbmsRootsIndex} or {@link RdbmsStateTrees}, or apply the bundled DDL
 * ({@code /ddl/mpf/{h2,postgres,sqlite}/schema.sql}) with your own migration tool.</p>
 *
 * <p>The bundled DDL only uses {@code CREATE ... IF NOT EXISTS}, so calling this method repeatedly is safe.
 * Table and index names honour {@link DbConfig#tablePrefix()}: with prefix {@code "acct"} the tables are
 * {@code acct_mpt_nodes}, {@code acct_mpt_roots} and {@code acct_mpt_latest}, and the indexes are prefixed the
 * same way, so several prefixed schemas can live in one database.</p>
 *
 * <pre>{@code
 * DbConfig config = DbConfig.builder()
 *     .jdbcUrl("jdbc:postgresql://localhost:5432/mpf", "user", "password")
 *     .build();
 * RdbmsMpfSchema.createIfNotExists(config);
 * try (RdbmsStateTrees trees = new RdbmsStateTrees(config)) {
 *     MpfTrie trie = new MpfTrie(trees.nodeStore());
 *     ...
 * }
 * }</pre>
 */
public final class RdbmsMpfSchema {

    /** Base names used in the bundled DDL, rewritten through {@link TableNameResolver}. */
    private static final BundledSchema DDL = new BundledSchema("MPF", RdbmsMpfSchema.class, "/ddl/mpf",
            "mpt_nodes", "mpt_roots", "mpt_latest", "idx_mpt_nodes_ns", "idx_mpt_roots_ns");

    /**
     * Longest identifier PostgreSQL keeps (NAMEDATALEN - 1). PostgreSQL silently truncates longer names, so two
     * long prefixes could map to the same table; the limit is enforced for every dialect to keep schemas portable.
     */
    public static final int MAX_IDENTIFIER_LENGTH = BundledSchema.MAX_IDENTIFIER_LENGTH;

    private RdbmsMpfSchema() {
    }

    /**
     * Creates the MPF tables and indexes if they do not exist yet.
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
