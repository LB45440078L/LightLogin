package dev.lightlogin.persistence;

import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.port.StorageException;

import java.sql.Driver;
import java.util.Properties;

/**
 * Loads a JDBC driver and builds its connection properties.
 *
 * <p>Drivers are loaded reflectively through a caller-supplied {@link ClassLoader} rather than
 * being compile-time dependencies, which is what allows the plugin jar to omit them. Every failure
 * is translated into a {@link StorageException} whose message names the actual cause — the
 * distinction between "driver not on the classpath", "driver's static initialiser blew up" and
 * "database refused the connection" is exactly what makes a startup failure diagnosable.</p>
 */
final class JdbcDrivers {

    private JdbcDrivers() {
    }

    /** Loads and instantiates the driver class. */
    static Driver load(DatabaseConfig.DatabaseType type, ClassLoader loader) {
        String driverClass = type.driverClass();
        Class<?> driverType;
        try {
            driverType = Class.forName(driverClass, true, loader);
        } catch (ClassNotFoundException e) {
            throw new StorageException("The " + type + " JDBC driver (" + driverClass
                    + ") is not available. " + hint(type), e);
        } catch (LinkageError e) {
            // A driver whose static initialiser fails, e.g. a native library that cannot load.
            throw new StorageException("The " + type + " JDBC driver (" + driverClass
                    + ") failed to initialise: " + describe(rootCause(e)) + ' ' + hint(type), e);
        }
        try {
            return (Driver) driverType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new StorageException("Could not instantiate the " + type + " JDBC driver ("
                    + driverClass + "): " + describe(rootCause(e)), e);
        }
    }

    /**
     * Connection properties, including credentials and a bounded connect timeout.
     *
     * <p>The timeout unit differs by driver: PostgreSQL counts {@code connectTimeout} in seconds,
     * MariaDB/MySQL in milliseconds. Getting this wrong either hangs startup for minutes or times
     * out immediately, so it is set per dialect rather than uniformly.</p>
     */
    static Properties connectionProperties(DatabaseConfig config, String password) {
        Properties properties = new Properties();
        if (config.type().isEmbedded()) {
            return properties;
        }
        properties.setProperty("user", config.username());
        properties.setProperty("password", password == null ? "" : password);
        if (config.type() == DatabaseConfig.DatabaseType.POSTGRESQL) {
            properties.setProperty("connectTimeout",
                    String.valueOf(Math.max(1, config.connectionTimeoutMillis() / 1000)));
        } else {
            properties.setProperty("connectTimeout", String.valueOf(config.connectionTimeoutMillis()));
        }
        return properties;
    }

    /** A short, actionable hint appended to driver failures. */
    static String hint(DatabaseConfig.DatabaseType type) {
        if (type.isEmbedded()) {
            return "The bundled SQLite driver loads a native library from its own package path, so it "
                    + "must not be relocated when shading; check the build's relocation configuration.";
        }
        return "Place the driver jar in plugins/LightLogin/libs/, or allow the plugin to download it "
                + "(see the libraries section of config.yml).";
    }

    /** {@code SimpleName: message}, so the line stays readable in a server log. */
    static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown cause";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getName()
                : throwable.getClass().getSimpleName() + ": " + message;
    }

    /** The deepest cause, so a wrapped driver failure names the real problem. */
    static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}