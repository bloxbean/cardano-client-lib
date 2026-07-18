package com.bloxbean.cardano.client.txflow.store.rdbms;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/** Simple non-pooling data source for URL-based store configuration. */
final class DriverManagerDataSource implements DataSource {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    DriverManagerDataSource(String jdbcUrl, String username, String password,
                            String driverClassName) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.username = username;
        this.password = password;
        if (driverClassName != null && !driverClassName.isBlank()) {
            loadDriver(driverClassName);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (username == null) return DriverManager.getConnection(jdbcUrl);
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    @Override
    public Connection getConnection(String user, String secret) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, secret);
    }

    @Override
    public PrintWriter getLogWriter() {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter writer) {
        DriverManager.setLogWriter(writer);
    }

    @Override
    public void setLoginTimeout(int seconds) {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("DriverManager has no parent logger");
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) return type.cast(this);
        throw new SQLException("Not a wrapper for " + type.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }

    private void loadDriver(String className) {
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            Class.forName(className, true,
                    contextLoader != null ? contextLoader : getClass().getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("Configured JDBC driver class is unavailable", failure);
        }
    }
}
