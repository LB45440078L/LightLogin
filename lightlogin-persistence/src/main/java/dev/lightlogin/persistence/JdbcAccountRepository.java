package dev.lightlogin.persistence;

import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AccountStatus;
import dev.lightlogin.core.port.AccountRepository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link AccountRepository}. */
public final class JdbcAccountRepository extends JdbcSupport implements AccountRepository {

    private static final String COLUMNS = "uuid, username, username_lower, password_hash, email, status, "
            + "failed_attempts, locked_until, created_at, last_login, last_ip, registration_ip";

    public JdbcAccountRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<Account> findByUuid(String uuid) {
        return query("findByUuid", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM ll_accounts WHERE uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rows = ps.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return query("findByUsername", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM ll_accounts WHERE username_lower = ?")) {
                ps.setString(1, username.toLowerCase(java.util.Locale.ROOT));
                try (ResultSet rows = ps.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void save(Account account) {
        transaction("save", connection -> {
            int updated = update(connection, account);
            if (updated == 0) {
                insert(connection, account);
            }
        });
    }

    private int update(java.sql.Connection connection, Account account) throws SQLException {
        String sql = "UPDATE ll_accounts SET username = ?, username_lower = ?, password_hash = ?, "
                + "email = ?, status = ?, failed_attempts = ?, locked_until = ?, created_at = ?, "
                + "last_login = ?, last_ip = ?, registration_ip = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.username());
            ps.setString(2, account.usernameLower());
            setNullable(ps, 3, account.passwordHash());
            setNullable(ps, 4, account.email());
            ps.setString(5, account.status().name());
            ps.setInt(6, account.failedAttempts());
            ps.setLong(7, account.lockedUntilMillis());
            ps.setLong(8, account.createdAtMillis());
            ps.setLong(9, account.lastLoginMillis());
            setNullable(ps, 10, account.lastIp());
            setNullable(ps, 11, account.registrationIp());
            ps.setString(12, account.uuid());
            return ps.executeUpdate();
        }
    }

    private void insert(java.sql.Connection connection, Account account) throws SQLException {
        String sql = "INSERT INTO ll_accounts (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.uuid());
            ps.setString(2, account.username());
            ps.setString(3, account.usernameLower());
            setNullable(ps, 4, account.passwordHash());
            setNullable(ps, 5, account.email());
            ps.setString(6, account.status().name());
            ps.setInt(7, account.failedAttempts());
            ps.setLong(8, account.lockedUntilMillis());
            ps.setLong(9, account.createdAtMillis());
            ps.setLong(10, account.lastLoginMillis());
            setNullable(ps, 11, account.lastIp());
            setNullable(ps, 12, account.registrationIp());
            ps.executeUpdate();
        }
    }

    @Override
    public void updatePassword(String uuid, String passwordHash) {
        transaction("updatePassword", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ll_accounts SET password_hash = ?, failed_attempts = 0, locked_until = 0, "
                            + "status = ? WHERE uuid = ?")) {
                ps.setString(1, passwordHash);
                ps.setString(2, AccountStatus.ACTIVE.name());
                ps.setString(3, uuid);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void recordLogin(String uuid, String ip, long nowMillis) {
        transaction("recordLogin", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ll_accounts SET last_ip = ?, last_login = ?, failed_attempts = 0, "
                            + "locked_until = 0, status = ? WHERE uuid = ?")) {
                setNullable(ps, 1, ip);
                ps.setLong(2, nowMillis);
                ps.setString(3, AccountStatus.ACTIVE.name());
                ps.setString(4, uuid);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public int incrementFailedAttempts(String uuid) {
        return transactionReturning("incrementFailedAttempts", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ll_accounts SET failed_attempts = failed_attempts + 1 WHERE uuid = ?")) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT failed_attempts FROM ll_accounts WHERE uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rows = ps.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public void resetFailedAttempts(String uuid) {
        transaction("resetFailedAttempts", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ll_accounts SET failed_attempts = 0, locked_until = 0, status = ? WHERE uuid = ?")) {
                ps.setString(1, AccountStatus.ACTIVE.name());
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void lock(String uuid, long untilMillis) {
        transaction("lock", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ll_accounts SET locked_until = ?, status = ? WHERE uuid = ?")) {
                ps.setLong(1, untilMillis);
                ps.setString(2, untilMillis > 0 ? AccountStatus.LOCKED.name() : AccountStatus.ACTIVE.name());
                ps.setString(3, uuid);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void updateEmail(String uuid, String email) {
        transaction("updateEmail", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ll_accounts SET email = ? WHERE uuid = ?")) {
                setNullable(ps, 1, email);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void delete(String uuid) {
        transaction("delete", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ll_accounts WHERE uuid = ?")) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public long countByRegistrationIp(String ip) {
        return query("countByRegistrationIp", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM ll_accounts WHERE registration_ip = ?")) {
                ps.setString(1, ip);
                try (ResultSet rows = ps.executeQuery()) {
                    return rows.next() ? rows.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public List<Account> page(int offset, int limit) {
        return query("page", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM ll_accounts ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                ps.setInt(1, Math.max(0, limit));
                ps.setInt(2, Math.max(0, offset));
                return mapAll(ps);
            }
        });
    }

    @Override
    public List<Account> search(String query, int offset, int limit) {
        String like = "%" + (query == null ? "" : query.toLowerCase(java.util.Locale.ROOT)) + "%";
        return query("search", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM ll_accounts WHERE username_lower LIKE ? "
                            + "ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, like);
                ps.setInt(2, Math.max(0, limit));
                ps.setInt(3, Math.max(0, offset));
                return mapAll(ps);
            }
        });
    }

    @Override
    public long count() {
        return query("count", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM ll_accounts");
                 ResultSet rows = ps.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        });
    }

    @Override
    public long countByLastIpSince(String ip, long sinceMillis) {
        return query("countByLastIpSince", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM ll_accounts WHERE last_ip = ? AND last_login >= ?")) {
                ps.setString(1, ip);
                ps.setLong(2, sinceMillis);
                try (ResultSet rows = ps.executeQuery()) {
                    return rows.next() ? rows.getLong(1) : 0L;
                }
            }
        });
    }

    private List<Account> mapAll(PreparedStatement ps) throws SQLException {
        List<Account> accounts = new ArrayList<>();
        try (ResultSet rows = ps.executeQuery()) {
            while (rows.next()) {
                accounts.add(map(rows));
            }
        }
        return accounts;
    }

    private static Account map(ResultSet rows) throws SQLException {
        return new Account(
                rows.getString("uuid"),
                rows.getString("username"),
                rows.getString("username_lower"),
                rows.getString("password_hash"),
                rows.getString("email"),
                AccountStatus.valueOf(rows.getString("status")),
                rows.getInt("failed_attempts"),
                rows.getLong("locked_until"),
                rows.getLong("created_at"),
                rows.getLong("last_login"),
                rows.getString("last_ip"),
                rows.getString("registration_ip"));
    }

    private static void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}