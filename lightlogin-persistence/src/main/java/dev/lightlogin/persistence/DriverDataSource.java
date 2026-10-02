package dev.lightlogin.persistence;

import dev.lightlogin.core.port.StorageException;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A {@link DataSource} that opens connections through a {@link Driver} instance.
 *
 * <p>Needed because the JDBC drivers are no longer on the plugin's classpath: they are resolved at
 * runtime (server classpath, or a downloaded jar behind its own class loader). HikariCP normally
 * resolves a driver with {@code Class.forName} using its own class loader, which would not see a
 * driver loaded from another one. Handing Hikari a {@code DataSource} sidesteps that entirely:
 * Hikari only ever calls {@link #getConnection()}, and this class holds a direct reference to the
 * driver that was loaded from wherever it actually lives.</p>
 *
 * <p>Every call returns a brand-new physical connection; pooling is Hikari's job.</p>
 */
final class DriverDataSource implements DataSource {

    private final Driver driver;
    private final String url;
    private final Properties properties;
    private final String description;

    private PrintWriter logWriter;
    private int loginTimeoutSeconds;

    DriverDataSource(Driver driver, String url, Properties properties, String description) {
        this.driver = driver;
        this.url = url;
        this.properties = properties;
        this.description = description;
    }

    /** Whether the driver accepts the configured URL, so a misconfiguration fails at startup. */
    boolean acceptsUrl() {
        try {
            return driver.acceptsURL(url);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = driver.connect(url, copyOf(properties));
        if (connection == null) {
            throw new SQLException("The driver for " + description + " does not accept the URL " + url);
        }
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties overridden = copyOf(properties);
        if (username != null) {
            overridden.setProperty("user", username);
        }
        if (password != null) {
            overridden.setProperty("password", password);
        }
        Connection connection = driver.connect(url, overridden);
        if (connection == null) {
            throw new SQLException("The driver for " + description + " does not accept the URL " + url);
        }
        return connection;
    }

    private static Properties copyOf(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeoutSeconds = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeoutSeconds;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("DriverDataSource does not use java.util.logging");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    @Override
    public String toString() {
        return "DriverDataSource[" + description + ']';
    }

    /** Guards against a null driver slipping in through the builder. */
    static Driver require(Driver driver, String description) {
        if (driver == null) {
            throw new StorageException("No JDBC driver was available for " + description);
        }
        return driver;
    }
}