package dev.lightlogin.persistence;

import com.zaxxer.hikari.HikariDataSource;
import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.port.AccountRepository;
import dev.lightlogin.core.port.AdminAccountRepository;
import dev.lightlogin.core.port.AuditRepository;
import dev.lightlogin.core.port.IpBanRepository;
import dev.lightlogin.core.port.SessionRepository;

import java.nio.file.Path;
import java.util.List;

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
        HikariDataSource dataSource = DataSourceFactory.create(config, decryptedPassword, dataFolder);
        PersistenceBootstrap bootstrap = new PersistenceBootstrap(dataSource);
        new SchemaMigrator(dataSource).migrate();
        return bootstrap;
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