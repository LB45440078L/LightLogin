package dev.lightlogin.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.port.StorageException;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Builds the connection pool for a configured backend.
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

    /**
     * Creates a pool.
     *
     * @param config            the database settings
     * @param decryptedPassword the plaintext database password (already decrypted by the caller)
     * @param dataFolder        directory in which a relative SQLite file is resolved
     */
    public static HikariDataSource create(DatabaseConfig config, String decryptedPassword, Path dataFolder) {
        Objects.requireNonNull(config, "config");
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("lightlogin-pool");
        hikari.setInitializationFailTimeout(-1);
        hikari.setConnectionTimeout(config.connectionTimeoutMillis());
        hikari.setMaxLifetime(30 * 60 * 1000L);
        hikari.setLeakDetectionThreshold(0);
        hikari.setJdbcUrl(jdbcUrl(config, dataFolder));
        hikari.setDriverClassName(config.type().driverClass());

        if (config.type().isEmbedded()) {
            // SQLite tolerates few concurrent writers; a small pool avoids lock thrash.
            hikari.setMaximumPoolSize(Math.min(4, config.poolSize()));
            // Wait rather than fail immediately when another writer holds the lock.
            hikari.setConnectionInitSql("PRAGMA busy_timeout=5000");
        } else {
            hikari.setUsername(config.username());
            hikari.setPassword(decryptedPassword);
            hikari.setMaximumPoolSize(config.poolSize());
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
            // Same connect bound the startup probe uses; the unit differs by driver.
            if (config.type() == DatabaseConfig.DatabaseType.POSTGRESQL) {
                hikari.addDataSourceProperty("connectTimeout",
                        String.valueOf(Math.max(1, config.connectionTimeoutMillis() / 1000)));
            } else {
                hikari.addDataSourceProperty("connectTimeout",
                        String.valueOf(config.connectionTimeoutMillis()));
            }
        }

        try {
            return new HikariDataSource(hikari);
        } catch (RuntimeException e) {
            throw new StorageException("Could not create the connection pool", e);
        }
    }
}