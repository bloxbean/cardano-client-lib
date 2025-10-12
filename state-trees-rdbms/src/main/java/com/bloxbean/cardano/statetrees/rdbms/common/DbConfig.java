package com.bloxbean.cardano.statetrees.rdbms.common;

import com.bloxbean.cardano.statetrees.rdbms.dialect.H2Dialect;
import com.bloxbean.cardano.statetrees.rdbms.dialect.PostgresDialect;
import com.bloxbean.cardano.statetrees.rdbms.dialect.SqlDialect;
import com.bloxbean.cardano.statetrees.rdbms.dialect.SqliteDialect;
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
public class DbConfig {
    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final String tablePrefix;

    private DbConfig(DataSource dataSource, SqlDialect dialect, String tablePrefix) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.tablePrefix = tablePrefix == null ? "" : tablePrefix.trim();
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
        return new DbConfig(dataSource, dialect, "");
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

        /**
         * Sets the JDBC data source.
         *
         * @param dataSource the data source
         * @return this builder
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        /**
         * Sets the SQL dialect.
         *
         * @param dialect the dialect
         * @return this builder
         */
        public Builder dialect(SqlDialect dialect) {
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
            // Create HikariCP DataSource
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);

            this.dataSource = new HikariDataSource(hikariConfig);

            // Auto-detect dialect from JDBC URL
            this.dialect = detectDialect(jdbcUrl);

            return this;
        }

        /**
         * Creates a simple non-pooled data source (for testing only).
         *
         * @param jdbcUrl the JDBC URL
         * @return this builder with DataSource and Dialect configured
         */
        public Builder simpleJdbcUrl(String jdbcUrl) {
            this.dataSource = new SimpleDataSource(jdbcUrl);
            this.dialect = detectDialect(jdbcUrl);
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
            if (dataSource == null) {
                throw new IllegalStateException("dataSource is required");
            }
            if (dialect == null) {
                throw new IllegalStateException("dialect is required");
            }
            return new DbConfig(dataSource, dialect, tablePrefix);
        }
    }
}
