package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.common.TableNameResolver;
import com.bloxbean.cardano.vds.rdbms.dialect.H2Dialect;
import com.bloxbean.cardano.vds.rdbms.dialect.PostgresDialect;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;
import com.bloxbean.cardano.vds.rdbms.dialect.SqliteDialect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

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
    private static final String[] NAMES = {
            "mpt_nodes", "mpt_roots", "mpt_latest", "idx_mpt_nodes_ns", "idx_mpt_roots_ns"};

    private RdbmsMpfSchema() {
    }

    /**
     * Creates the MPF tables and indexes if they do not exist yet.
     *
     * @param config the database configuration (data source, dialect and optional table prefix)
     * @throws IllegalArgumentException if the dialect has no bundled DDL
     * @throws IllegalStateException    if the DDL cannot be read or executed
     */
    public static void createIfNotExists(DbConfig config) {
        Objects.requireNonNull(config, "config");
        List<String> statements = statements(config.dialect(), config.tablePrefix());
        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create MPF schema for " + config.dialect().name(), e);
        }
    }

    /**
     * Returns the DDL statements {@link #createIfNotExists(DbConfig)} would run, with prefixed names.
     *
     * @param dialect     the SQL dialect
     * @param tablePrefix the table prefix, or empty/null for none
     * @return the statements, in execution order
     */
    public static List<String> statements(SqlDialect dialect, String tablePrefix) {
        Objects.requireNonNull(dialect, "dialect");
        TableNameResolver resolver = new TableNameResolver(tablePrefix);
        String ddl = readResource("/ddl/mpf/" + ddlDirectory(dialect) + "/schema.sql");

        StringBuilder withoutComments = new StringBuilder();
        for (String line : ddl.split("\\R")) {
            int comment = line.indexOf("--");
            withoutComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }

        List<String> out = new ArrayList<>();
        for (String raw : withoutComments.toString().split(";")) {
            String sql = raw.trim();
            if (sql.isEmpty()) continue;
            for (String name : NAMES) {
                sql = sql.replaceAll("\\b" + name + "\\b", Matcher.quoteReplacement(resolver.resolve(name)));
            }
            out.add(sql);
        }
        return out;
    }

    private static String ddlDirectory(SqlDialect dialect) {
        if (dialect instanceof H2Dialect) return "h2";
        if (dialect instanceof PostgresDialect) return "postgres";
        if (dialect instanceof SqliteDialect) return "sqlite";
        throw new IllegalArgumentException("No bundled MPF DDL for dialect " + dialect.name()
                + "; apply an equivalent schema manually");
    }

    private static String readResource(String path) {
        try (InputStream in = RdbmsMpfSchema.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled DDL resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
