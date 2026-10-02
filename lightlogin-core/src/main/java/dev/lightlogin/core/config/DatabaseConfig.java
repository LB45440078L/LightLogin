package dev.lightlogin.core.config;

import java.util.Objects;

/**
 * Database connection settings.
 *
 * <p>{@code encryptedPassword} may be an {@code enc:}-prefixed AES-GCM envelope produced by the
 * {@code SecretBox}; the persistence bootstrap decrypts it with the master key. Storing the
 * database password in ciphertext means a leaked {@code config.yml} alone does not yield database
 * credentials.</p>
 *
 * @param type              backend to use
 * @param host              server host (ignored for SQLite)
 * @param port              server port (ignored for SQLite)
 * @param database          database/schema name (ignored for SQLite)
 * @param username          account name (ignored for SQLite)
 * @param encryptedPassword password, possibly encrypted
 * @param sqliteFile        path to the SQLite file
 * @param poolSize          maximum pooled connections
 * @param connectionTimeoutMillis time to wait for a connection
 */
public record DatabaseConfig(
        DatabaseType type,
        String host,
        int port,
        String database,
        String username,
        String encryptedPassword,
        String sqliteFile,
        int poolSize,
        long connectionTimeoutMillis) {

    /** Supported backends. */
    public enum DatabaseType {
        SQLITE("org.sqlite.JDBC", "jdbc:sqlite:"),
        MYSQL("org.mariadb.jdbc.Driver", "jdbc:mariadb://"),
        MARIADB("org.mariadb.jdbc.Driver", "jdbc:mariadb://"),
        POSTGRESQL("org.postgresql.Driver", "jdbc:postgresql://");

        private final String driverClass;
        private final String urlPrefix;

        DatabaseType(String driverClass, String urlPrefix) {
            this.driverClass = driverClass;
            this.urlPrefix = urlPrefix;
        }

        public String driverClass() {
            return driverClass;
        }

        public String urlPrefix() {
            return urlPrefix;
        }

        public boolean isEmbedded() {
            return this == SQLITE;
        }

        /** Parses leniently, defaulting to SQLite on an unknown name. */
        public static DatabaseType parse(String name) {
            if (name == null) {
                return SQLITE;
            }
            return switch (name.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "MYSQL", "MARIADB" -> MARIADB;
                case "POSTGRESQL", "POSTGRES", "PG" -> POSTGRESQL;
                default -> SQLITE;
            };
        }
    }

    public DatabaseConfig {
        Objects.requireNonNull(type, "type");
        host = host == null ? "127.0.0.1" : host;
        database = database == null ? "lightlogin" : database;
        username = username == null ? "" : username;
        encryptedPassword = encryptedPassword == null ? "" : encryptedPassword;
        sqliteFile = sqliteFile == null ? "lightlogin.db" : sqliteFile;
        if (poolSize < 1) {
            poolSize = 1;
        }
    }

    public static DatabaseConfig sqliteDefault() {
        return new DatabaseConfig(DatabaseType.SQLITE, "127.0.0.1", 0, "lightlogin", "", "",
                "lightlogin.db", 4, 10_000);
    }
}