package dev.lightlogin.persistence;

import com.zaxxer.hikari.HikariDataSource;
import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.port.AccountRepository;
import dev.lightlogin.core.port.AdminAccountRepository;
import dev.lightlogin.core.port.AuditRepository;
import dev.lightlogin.core.port.IpBanRepository;
import dev.lightlogin.core.port.SessionRepository;
import dev.lightlogin.core.port.StorageException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Owns the connection pool and the repository adapters, and runs migrations at startup.
 *
 * <p>One object to construct and one to close, so the plugin's lifecycle has a single place to
 * bring storage up and tear it down.</p>
 */
public final class PersistenceBootstrap implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcAccountRepository accounts;
    private final JdbcSessionRepository sessions;
    private final JdbcIpBanRepository bans;
    private final JdbcAuditRepository audit;
    private final JdbcAdminAccountRepository admins;

    private PersistenceBootstrap(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.accounts = new JdbcAccountRepository(dataSource);
        this.sessions = new JdbcSessionRepository(dataSource);
        this.bans = new JdbcIpBanRepository(dataSource);
        this.audit = new JdbcAuditRepository(dataSource);
        this.admins = new JdbcAdminAccountRepository(dataSource);
    }

    /**
     * Creates the pool and applies pending migrations.
     *
     * @param config            database settings
     * @param decryptedPassword plaintext database password (already decrypted)
     * @param dataFolder        base directory for a relative SQLite file
     * @return the wired persistence layer
     */
    public static PersistenceBootstrap start(DatabaseConfig config, String decryptedPassword, Path dataFolder) {
        // Probe first. With initializationFailTimeout(-1) the pool starts happily against a
        // database it cannot actually reach, and the failure only surfaces ten seconds later as an
        // opaque "Connection is not available" — which hides the real cause, such as a missing
        // native library or a driver that is not on the classpath.
        verifyDriverAndConnection(config, decryptedPassword, dataFolder);

        HikariDataSource dataSource = DataSourceFactory.create(config, decryptedPassword, dataFolder);
        PersistenceBootstrap bootstrap = new PersistenceBootstrap(dataSource);
        new SchemaMigrator(dataSource).migrate();
        return bootstrap;
    }

    /**
     * Loads the driver and opens one real connection, translating any failure into a
     * {@link StorageException} that names the actual cause.
     */
    static void verifyDriverAndConnection(DatabaseConfig config, String password, Path dataFolder) {
        String url = DataSourceFactory.jdbcUrl(config, dataFolder);
        String driverClass = config.type().driverClass();

        Class<?> driverType;
        try {
            driverType = Class.forName(driverClass, true, PersistenceBootstrap.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new StorageException("The " + config.type() + " JDBC driver (" + driverClass
                    + ") is not on the classpath. " + driverHint(config.type()), e);
        } catch (LinkageError e) {
            // A driver whose static initialiser fails, e.g. a native library that cannot load.
            throw new StorageException("The " + config.type() + " JDBC driver (" + driverClass
                    + ") failed to initialise: " + describe(rootCause(e)) + ' '
                    + driverHint(config.type()), e);
        }

        Properties properties = new Properties();
        if (!config.type().isEmbedded()) {
            properties.setProperty("user", config.username());
            properties.setProperty("password", password);
            // Bound the TCP connect so a wrong host fails in seconds instead of hanging startup.
            // The unit differs by driver: PostgreSQL counts connectTimeout in seconds, MariaDB in
            // milliseconds.
            int seconds = Math.max(1, (int) (config.connectionTimeoutMillis() / 1000));
            if (config.type() == DatabaseConfig.DatabaseType.POSTGRESQL) {
                properties.setProperty("connectTimeout", String.valueOf(seconds));
            } else {
                properties.setProperty("connectTimeout", String.valueOf(config.connectionTimeoutMillis()));
            }
        }

        // Connect through the Driver instance rather than DriverManager: inside a plugin
        // classloader, DriverManager's view of which drivers are visible is not always the plugin's
        // view, and a direct connect surfaces the real cause immediately.
        try {
            Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();
            try (Connection connection = driver.connect(url, properties)) {
                if (connection == null) {
                    throw new StorageException("The " + config.type() + " driver does not accept the URL "
                            + url + ". " + driverHint(config.type()));
                }
                if (!connection.isValid(5)) {
                    throw new StorageException("The " + config.type() + " connection at " + url
                            + " reported itself invalid");
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new StorageException("Could not instantiate the " + config.type() + " JDBC driver ("
                    + driverClass + "): " + describe(rootCause(e)), e);
        } catch (SQLException e) {
            throw new StorageException("Could not open the " + config.type() + " database at " + url
                    + ": " + describe(rootCause(e)), e);
        } catch (LinkageError e) {
            // The sqlite-jdbc failure mode: the class is present but a class or native library it
            // needs is not, surfacing as NoClassDefFoundError or UnsatisfiedLinkError on first use.
            throw new StorageException("The " + config.type() + " driver could not load a required class "
                    + "or its native library: " + describe(rootCause(e)) + ' '
                    + driverHint(config.type()), e);
        }
    }

    /** A short, actionable hint appended to driver failures. */
    private static String driverHint(DatabaseConfig.DatabaseType type) {
        if (type.isEmbedded()) {
            return "The bundled SQLite driver loads a native library from its own package path, so it "
                    + "must not be relocated when shading; check the build's relocation configuration.";
        }
        return "Place the driver jar in plugins/LightLogin/libs/ (see docs/07-development.md), or use "
                + "the default build, which bundles it.";
    }

    /** {@code SimpleName: message}, so the line stays readable in a server log. */
    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown cause";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getName()
                : throwable.getClass().getSimpleName() + ": " + message;
    }

    /** The deepest cause, so a wrapped driver failure names the real problem. */
    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Applies any pending migrations again (safe to call; migrations are idempotent per ledger). */
    public List<String> migrate() {
        return new SchemaMigrator(dataSource).migrate();
    }

    public AccountRepository accounts() {
        return accounts;
    }

    public SessionRepository sessions() {
        return sessions;
    }

    public IpBanRepository bans() {
        return bans;
    }

    public AuditRepository audit() {
        return audit;
    }

    public AdminAccountRepository admins() {
        return admins;
    }

    /** Whether the pool can hand out a connection right now. */
    public boolean isHealthy() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}