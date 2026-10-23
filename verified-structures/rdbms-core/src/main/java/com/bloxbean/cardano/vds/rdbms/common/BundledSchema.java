package com.bloxbean.cardano.vds.rdbms.common;

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
 * Runs a store module's bundled DDL ({@code <resourceRoot>/{h2,postgres,sqlite}/schema.sql}) with table and
 * index names rewritten through {@link TableNameResolver}.
 *
 * <p>Support code for the public schema helpers of the RDBMS store modules ({@code RdbmsMpfSchema},
 * {@code RdbmsJmtSchemaInstaller}); applications use those helpers instead of this class.</p>
 *
 * <p>The DDL must use only {@code CREATE ... IF NOT EXISTS} statements, so running it repeatedly is safe.
 * {@code --} comments are removed and statements are split at {@code ;}.</p>
 */
public final class BundledSchema {

    /**
     * Longest identifier PostgreSQL keeps (NAMEDATALEN - 1). PostgreSQL silently truncates longer names, so two
     * long prefixes could map to the same table; the limit is enforced for every dialect to keep schemas portable.
     */
    public static final int MAX_IDENTIFIER_LENGTH = 63;

    private final String label;
    private final Class<?> resourceAnchor;
    private final String resourceRoot;
    private final String[] names;

    /**
     * @param label          short name used in messages, for example {@code "MPF"}
     * @param resourceAnchor a class whose class loader holds the DDL resources
     * @param resourceRoot   the resource directory holding one sub-directory per dialect, for example
     *                       {@code "/ddl/mpf"}
     * @param names          every table and index name in the DDL, rewritten with the table prefix
     */
    public BundledSchema(String label, Class<?> resourceAnchor, String resourceRoot, String... names) {
        this.label = Objects.requireNonNull(label, "label");
        this.resourceAnchor = Objects.requireNonNull(resourceAnchor, "resourceAnchor");
        this.resourceRoot = Objects.requireNonNull(resourceRoot, "resourceRoot");
        this.names = names.clone();
    }

    /**
     * Creates the tables and indexes if they do not exist yet.
     *
     * <p>If the data source hands out connections with auto-commit off, all statements run in one transaction,
     * which is committed at the end or rolled back on failure. With auto-commit on (the JDBC default) each
     * statement commits on its own; after a failure, fix the cause and run it again, since every statement is
     * idempotent.</p>
     *
     * @param config the database configuration (data source, dialect and optional table prefix)
     * @throws IllegalArgumentException if the dialect has no bundled DDL, or the prefix is invalid or makes a
     *                                  generated name longer than {@link #MAX_IDENTIFIER_LENGTH}
     * @throws IllegalStateException    if the DDL cannot be read or executed
     */
    public void createIfNotExists(DbConfig config) {
        Objects.requireNonNull(config, "config");
        List<String> statements = statements(config.dialect(), config.tablePrefix());
        try (Connection conn = config.dataSource().getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    stmt.execute(sql);
                }
                if (!autoCommit) {
                    conn.commit();
                }
            } catch (SQLException | RuntimeException e) {
                if (!autoCommit) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackFailure) {
                        e.addSuppressed(rollbackFailure);
                    }
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create " + label + " schema for " + config.dialect().name(), e);
        }
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
    public List<String> statements(SqlDialect dialect, String tablePrefix) {
        Objects.requireNonNull(dialect, "dialect");
        TableNameResolver resolver = new TableNameResolver(tablePrefix);
        for (String name : names) {
            String resolved = resolver.resolve(name);
            if (resolved.length() > MAX_IDENTIFIER_LENGTH) {
                throw new IllegalArgumentException("Table prefix '" + resolver.tablePrefix() + "' is too long: generated name '"
                        + resolved + "' has " + resolved.length() + " characters, the maximum is " + MAX_IDENTIFIER_LENGTH);
            }
        }
        String ddl = readResource(resourceRoot + "/" + ddlDirectory(dialect) + "/schema.sql");

        StringBuilder withoutComments = new StringBuilder();
        for (String line : ddl.split("\\R")) {
            int comment = line.indexOf("--");
            withoutComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }

        List<String> out = new ArrayList<>();
        for (String raw : withoutComments.toString().split(";")) {
            String sql = raw.trim();
            if (sql.isEmpty()) continue;
            for (String name : names) {
                sql = sql.replaceAll("\\b" + name + "\\b", Matcher.quoteReplacement(resolver.resolve(name)));
            }
            out.add(sql);
        }
        return out;
    }

    private String ddlDirectory(SqlDialect dialect) {
        if (dialect instanceof H2Dialect) return "h2";
        if (dialect instanceof PostgresDialect) return "postgres";
        if (dialect instanceof SqliteDialect) return "sqlite";
        throw new IllegalArgumentException("No bundled " + label + " DDL for dialect " + dialect.name()
                + "; apply an equivalent schema manually");
    }

    private String readResource(String path) {
        try (InputStream in = resourceAnchor.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled DDL resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
