package dev.lightlogin.persistence;

import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.port.StorageException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Builds the connection pool for a configured backend.
 *
 * <p>The pool is deliberately small. A login plugin's database traffic is bursty and short-lived,
 * and extra connections increase lock contention (especially on SQLite, where a single writer is
 * the rule). {@code initializationFailTimeout(-1)} means a briefly unreachable database does not
 * prevent startup; the first real query surfaces the problem to a caller already prepared to
 * degrade.</p>
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
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

        switch (config.type()) {
            case SQLITE -> {
                Path file = Path.of(config.sqliteFile());
                if (!file.isAbsolute() && dataFolder != null) {
                    file = dataFolder.resolve(config.sqliteFile());
                }
                hikari.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
                hikari.setDriverClassName("org.sqlite.JDBC");
                // SQLite tolerates few concurrent writers; a small pool avoids lock thrash.
                hikari.setMaximumPoolSize(Math.min(4, config.poolSize()));
                // Wait rather than fail immediately when another writer holds the lock.
                hikari.setConnectionInitSql("PRAGMA busy_timeout=5000");
            }
            case MARIADB, MYSQL -> {
                hikari.setJdbcUrl(config.type().urlPrefix() + config.host() + ":" + config.port()
                        + "/" + config.database()
                        + "?useUnicode=true&characterEncoding=utf8&useServerPrepStmts=true"
                        + "&cachePrepStmts=true&prepStmtCacheSize=250");
                hikari.setDriverClassName(config.type().driverClass());
                hikari.setUsername(config.username());
                hikari.setPassword(decryptedPassword);
                hikari.setMaximumPoolSize(config.poolSize());
            }
            case POSTGRESQL -> {
                hikari.setJdbcUrl(config.type().urlPrefix() + config.host() + ":" + config.port()
                        + "/" + config.database());
                hikari.setDriverClassName(config.type().driverClass());
                hikari.setUsername(config.username());
                hikari.setPassword(decryptedPassword);
                hikari.setMaximumPoolSize(config.poolSize());
            }
            default -> throw new StorageException("Unsupported database type: " + config.type());
        }

        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        try {
            return new HikariDataSource(hikari);
        } catch (RuntimeException e) {
            throw new StorageException("Could not create the connection pool", e);
        }
    }
}