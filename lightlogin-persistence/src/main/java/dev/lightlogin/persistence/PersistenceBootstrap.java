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
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

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
     * Creates the pool and applies pending migrations, resolving the driver from this module's own
     * class loader. Convenient for tests and for a build where the driver is on the classpath.
     */
    public static PersistenceBootstrap start(DatabaseConfig config, String decryptedPassword, Path dataFolder) {
        return start(config, decryptedPassword, dataFolder, PersistenceBootstrap.class.getClassLoader());
    }

    /**
     * Creates the pool and applies pending migrations.
     *
     * @param config            database settings
     * @param decryptedPassword plaintext database password (already decrypted)
     * @param dataFolder        base directory for a relative SQLite file
     * @param driverLoader      class loader that can see the configured driver
     * @return the wired persistence layer
     */
    public static PersistenceBootstrap start(DatabaseConfig config, String decryptedPassword,
                                             Path dataFolder, ClassLoader driverLoader) {
        Objects.requireNonNull(driverLoader, "driverLoader");

        // Probe first. With initializationFailTimeout(-1) the pool starts happily against a
        // database it cannot actually reach, and the failure only surfaces ten seconds later as an
        // opaque "Connection is not available" — which hides the real cause, such as a missing
        // native library or a driver that is not on the classpath.
        verifyDriverAndConnection(config, decryptedPassword, dataFolder, driverLoader);

        HikariDataSource dataSource = DataSourceFactory.create(config, decryptedPassword, dataFolder, driverLoader);
        PersistenceBootstrap bootstrap = new PersistenceBootstrap(dataSource);
        new SchemaMigrator(dataSource).migrate();
        return bootstrap;
    }

    /**
     * Opens one real connection through the driver before the pool exists, translating any failure
     * into a {@link StorageException} that names the actual cause.
     */
    static void verifyDriverAndConnection(DatabaseConfig config, String password, Path dataFolder,
                                          ClassLoader driverLoader) {
        String url = DataSourceFactory.jdbcUrl(config, dataFolder);
        DriverDataSource source = DataSourceFactory.dataSource(config, password, dataFolder, driverLoader);
        try (Connection connection = source.getConnection()) {
            if (!connection.isValid(5)) {
                throw new StorageException("The " + config.type() + " connection at " + url
                        + " reported itself invalid");
            }
        } catch (SQLException e) {
            throw new StorageException("Could not open the " + config.type() + " database at " + url
                    + ": " + JdbcDrivers.describe(JdbcDrivers.rootCause(e)), e);
        } catch (LinkageError e) {
            // The sqlite-jdbc failure mode: the class is present but a class or native library it
            // needs is not, surfacing as NoClassDefFoundError or UnsatisfiedLinkError on first use.
            throw new StorageException("The " + config.type() + " driver could not load a required class "
                    + "or its native library: " + JdbcDrivers.describe(JdbcDrivers.rootCause(e)) + ' '
                    + JdbcDrivers.hint(config.type()), e);
        }
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