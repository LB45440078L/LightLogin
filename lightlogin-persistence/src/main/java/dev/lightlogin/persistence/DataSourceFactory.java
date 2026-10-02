package dev.lightlogin.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.port.StorageException;

import java.nio.file.Path;
import java.sql.Driver;
import java.util.Objects;
import java.util.Properties;

/**
 * Builds the connection pool for a configured backend.
 *
 * <p>The driver is resolved through a caller-supplied class loader, so the plugin jar does not have
 * to carry the drivers, and the pool is handed a {@link DriverDataSource} rather than a driver
 * class name — HikariCP would otherwise resolve the driver with its own class loader and miss one
 * loaded from somewhere else.</p>
 *
 * <p>The pool is deliberately small. A login plugin's database traffic is bursty and short-lived,
 * and extra connections increase lock contention (especially on SQLite, where a single writer is
 * the rule). {@code initializationFailTimeout(-1)} means a briefly unreachable database does not
 * prevent startup; {@link PersistenceBootstrap} probes the connection explicitly first, so a
 * genuine misconfiguration is reported with its real cause rather than as an opaque pool timeout
 * ten seconds later.</p>
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    /**
     * The JDBC URL for a configuration, resolving a relative SQLite file against the data folder.
     *
     * <p>Exposed because the startup probe must open exactly the database the pool will.</p>
     */
    public static String jdbcUrl(DatabaseConfig config, Path dataFolder) {
        Objects.requireNonNull(config, "config");
        return switch (config.type()) {
            case SQLITE -> {
                Path file = Path.of(config.sqliteFile());
                if (!file.isAbsolute() && dataFolder != null) {
                    file = dataFolder.resolve(config.sqliteFile());
                }
                yield "jdbc:sqlite:" + file.toAbsolutePath();
            }
            case MARIADB, MYSQL -> config.type().urlPrefix() + config.host() + ':' + config.port()
                    + '/' + config.database()
                    + "?useUnicode=true&characterEncoding=utf8&useServerPrepStmts=true"
                    + "&cachePrepStmts=true&prepStmtCacheSize=250";
            case POSTGRESQL -> config.type().urlPrefix() + config.host() + ':' + config.port()
                    + '/' + config.database();
        };
    }

    /** Builds the {@link DriverDataSource} the pool will use. */
    static DriverDataSource dataSource(DatabaseConfig config, String password, Path dataFolder,
                                       ClassLoader driverLoader) {
        String url = jdbcUrl(config, dataFolder);
        Driver driver = DriverDataSource.require(
                JdbcDrivers.load(config.type(), driverLoader), config.type().name());
        Properties properties = JdbcDrivers.connectionProperties(config, password);
        DriverDataSource source = new DriverDataSource(driver, url, properties, config.type().name());
        if (!source.acceptsUrl()) {
            throw new StorageException("The " + config.type() + " driver does not accept the URL "
                    + url + ". " + JdbcDrivers.hint(config.type()));
        }
        return source;
    }

    /**
     * Creates a pool.
     *
     * @param config            the database settings
     * @param decryptedPassword the plaintext database password (already decrypted by the caller)
     * @param dataFolder        directory in which a relative SQLite file is resolved
     * @param driverLoader      class loader that can see the configured driver
     */
    public static HikariDataSource create(DatabaseConfig config, String decryptedPassword,
                                          Path dataFolder, ClassLoader driverLoader) {
        Objects.requireNonNull(config, "config");
        DriverDataSource source = dataSource(config, decryptedPassword, dataFolder, driverLoader);

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("lightlogin-pool");
        hikari.setInitializationFailTimeout(-1);
        hikari.setConnectionTimeout(config.connectionTimeoutMillis());
        hikari.setMaxLifetime(30 * 60 * 1000L);
        hikari.setLeakDetectionThreshold(0);
        hikari.setDataSource(source);

        if (config.type().isEmbedded()) {
            // SQLite tolerates few concurrent writers; a small pool avoids lock thrash.
            hikari.setMaximumPoolSize(Math.min(4, config.poolSize()));
            // Wait rather than fail immediately when another writer holds the lock.
            hikari.setConnectionInitSql("PRAGMA busy_timeout=5000");
        } else {
            hikari.setMaximumPoolSize(config.poolSize());
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        }

        try {
            return new HikariDataSource(hikari);
        } catch (RuntimeException e) {
            throw new StorageException("Could not create the connection pool", e);
        }
    }
}