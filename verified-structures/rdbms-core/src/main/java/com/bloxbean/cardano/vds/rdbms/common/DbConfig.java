package com.bloxbean.cardano.vds.rdbms.common;

import com.bloxbean.cardano.vds.rdbms.dialect.H2Dialect;
import com.bloxbean.cardano.vds.rdbms.dialect.PostgresDialect;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;
import com.bloxbean.cardano.vds.rdbms.dialect.SqliteDialect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Database configuration for RDBMS state trees.
 *
 * <p>Supports JDBC DataSource or connection parameters. For production use,
 * it's recommended to use HikariCP for connection pooling.
 *
 * @since 0.8.0
 */
public class DbConfig implements AutoCloseable {
    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final String tablePrefix;
    private final boolean ownsDataSource;

    private DbConfig(DataSource dataSource, SqlDialect dialect, String tablePrefix, boolean ownsDataSource) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.tablePrefix = tablePrefix == null ? "" : tablePrefix.trim();
        this.ownsDataSource = ownsDataSource;
    }

    /**
     * Creates a builder for DbConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default configuration with no table prefix.
     *
     * @param dataSource the JDBC data source
     * @param dialect the SQL dialect
     * @return a DbConfig with default settings
     */
    public static DbConfig defaults(DataSource dataSource, SqlDialect dialect) {
        return new DbConfig(dataSource, dialect, "", false);
    }

    /**
     * Closes the underlying {@link DataSource} if this config created it (via
     * {@link Builder#jdbcUrl} / {@link Builder#simpleJdbcUrl}). Externally supplied data sources
     * (via {@link Builder#dataSource}) are left untouched — their lifecycle belongs to the caller.
     */
    @Override
    public void close() {
        if (ownsDataSource && dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to close DataSource", e);
            }
        }
    }

    /**
     * Returns the JDBC data source.
     *
     * @return the data source
     */
    public DataSource dataSource() {
        return dataSource;
    }

    /**
     * Returns the SQL dialect.
     *
     * @return the dialect
     */
    public SqlDialect dialect() {
        return dialect;
    }

    /**
     * Returns the table prefix for namespace support.
     *
     * @return the table prefix (empty string for default)
     */
    public String tablePrefix() {
        return tablePrefix;
    }

    /**
     * Builder for DbConfig.
     */
    public static class Builder {
        private DataSource dataSource;
        private SqlDialect dialect;
        private String tablePrefix = "";
        private boolean ownsDataSource = false;
        private HikariConfig pendingHikariConfig;
        private boolean built;

        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("DbConfig.Builder may only build one configuration");
            }
        }

        /**
         * If this builder previously created a pool it owns, close it before the reference is
         * replaced by another {@code dataSource(...)} / {@code jdbcUrl(...)} call, so an overwrite
         * never orphans a live connection pool.
         */
        private void closeOwnedDataSourceIfAny() {
            if (ownsDataSource && dataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) dataSource).close();
                } catch (Exception ignored) {
                    // best-effort close of a to-be-discarded pool
                }
            }
        }

        /**
         * Sets the JDBC data source.
         *
         * @param dataSource the data source
         * @return this builder
         */
        public Builder dataSource(DataSource dataSource) {
            ensureNotBuilt();
            closeOwnedDataSourceIfAny();
            this.dataSource = dataSource;
            this.pendingHikariConfig = null;
            this.ownsDataSource = false; // externally supplied; caller owns its lifecycle
            return this;
        }

        /**
         * Sets the SQL dialect.
         *
         * @param dialect the dialect
         * @return this builder
         */
        public Builder dialect(SqlDialect dialect) {
            ensureNotBuilt();
            this.dialect = dialect;
            return this;
        }

        /**
         * Sets the table prefix for namespace support.
         *
         * @param tablePrefix the table prefix (e.g., "account", "shard1")
         * @return this builder
         */
        public Builder tablePrefix(String tablePrefix) {
            ensureNotBuilt();
            this.tablePrefix = tablePrefix;
            return this;
        }

        /**
         * Creates a HikariCP data source from JDBC URL and auto-detects the dialect.
         *
         * @param jdbcUrl the JDBC URL
         * @param username the database username
         * @param password the database password
         * @return this builder with DataSource and Dialect configured
         */
        public Builder jdbcUrl(String jdbcUrl, String username, String password) {
            ensureNotBuilt();
            SqlDialect detectedDialect = detectDialect(jdbcUrl);
            closeOwnedDataSourceIfAny();
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);

            // Defer starting the connection pool until build() has validated the builder. This
            // prevents abandoned/invalid builders from leaking pool threads and connections.
            this.pendingHikariConfig = hikariConfig;
            this.dataSource = null;
            this.ownsDataSource = false;
            this.dialect = detectedDialect;

            return this;
        }

        /**
         * Creates a simple non-pooled data source (for testing only).
         *
         * @param jdbcUrl the JDBC URL
         * @return this builder with DataSource and Dialect configured
         */
        public Builder simpleJdbcUrl(String jdbcUrl) {
            ensureNotBuilt();
            SqlDialect detectedDialect = detectDialect(jdbcUrl);
            closeOwnedDataSourceIfAny();
            this.dataSource = new SimpleDataSource(jdbcUrl);
            this.pendingHikariConfig = null;
            this.ownsDataSource = true; // this config created the data source
            this.dialect = detectedDialect;
            return this;
        }

        private SqlDialect detectDialect(String jdbcUrl) {
            if (jdbcUrl.startsWith("jdbc:postgresql:")) {
                return new PostgresDialect();
            } else if (jdbcUrl.startsWith("jdbc:h2:")) {
                return new H2Dialect();
            } else if (jdbcUrl.startsWith("jdbc:sqlite:")) {
                return new SqliteDialect();
            } else {
                throw new IllegalArgumentException(
                    "Cannot auto-detect dialect from JDBC URL: " + jdbcUrl + ". " +
                    "Please specify dialect explicitly."
                );
            }
        }

        /**
         * Builds the DbConfig.
         *
         * @return the configured DbConfig
         * @throws IllegalStateException if required fields are missing
         */
        public DbConfig build() {
            ensureNotBuilt();
            if (dataSource == null && pendingHikariConfig == null) {
                throw new IllegalStateException("dataSource is required");
            }
            if (dialect == null) {
                throw new IllegalStateException("dialect is required");
            }
            DataSource configuredDataSource = dataSource;
            boolean configOwnsDataSource = ownsDataSource;
            if (pendingHikariConfig != null) {
                configuredDataSource = new HikariDataSource(pendingHikariConfig);
                configOwnsDataSource = true;
            }
            DbConfig config = new DbConfig(configuredDataSource, dialect, tablePrefix,
                    configOwnsDataSource);
            // Ownership has transferred to the returned config. Reusing this builder would make
            // lifecycle ownership ambiguous, so consume it.
            built = true;
            dataSource = null;
            pendingHikariConfig = null;
            ownsDataSource = false;
            return config;
        }
    }
}
