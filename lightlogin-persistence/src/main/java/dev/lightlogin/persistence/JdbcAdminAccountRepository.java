package dev.lightlogin.persistence;

import dev.lightlogin.core.model.AdminAccount;
import dev.lightlogin.core.model.AdminRole;
import dev.lightlogin.core.port.AdminAccountRepository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link AdminAccountRepository}. */
public final class JdbcAdminAccountRepository extends JdbcSupport implements AdminAccountRepository {

    public JdbcAdminAccountRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<AdminAccount> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return query("findByUsername", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT username, password_hash, role, created_at, last_login, totp_secret "
                            + "FROM ll_admin_accounts WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rows = ps.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void save(AdminAccount account) {
        transaction("save", connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ll_admin_accounts SET password_hash = ?, role = ?, created_at = ?, "
                            + "last_login = ?, totp_secret = ? WHERE username = ?")) {
                update.setString(1, account.passwordHash());
                update.setString(2, account.role().name());
                update.setLong(3, account.createdAtMillis());
                update.setLong(4, account.lastLoginMillis());
                setNullable(update, 5, account.totpSecret());
                update.setString(6, account.username());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ll_admin_accounts (username, password_hash, role, created_at, "
                            + "last_login, totp_secret) VALUES (?,?,?,?,?,?)")) {
                insert.setString(1, account.username());
                insert.setString(2, account.passwordHash());
                insert.setString(3, account.role().name());
                insert.setLong(4, account.createdAtMillis());
                insert.setLong(5, account.lastLoginMillis());
                setNullable(insert, 6, account.totpSecret());
                try {
                    insert.executeUpdate();
                } catch (SQLException e) {
                    if (!isConstraintViolation(e)) {
                        throw e;
                    }
                }
            }
        });
    }

    @Override
    public void delete(String username) {
        transaction("delete", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ll_admin_accounts WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void recordLogin(String username, long nowMillis) {
        transaction("recordLogin", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ll_admin_accounts SET last_login = ? WHERE username = ?")) {
                ps.setLong(1, nowMillis);
                ps.setString(2, username);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<AdminAccount> all() {
        return query("all", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT username, password_hash, role, created_at, last_login, totp_secret "
                            + "FROM ll_admin_accounts");
                 ResultSet rows = ps.executeQuery()) {
                List<AdminAccount> accounts = new ArrayList<>();
                while (rows.next()) {
                    accounts.add(map(rows));
                }
                return accounts;
            }
        });
    }

    @Override
    public long count() {
        return query("count", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM ll_admin_accounts");
                 ResultSet rows = ps.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        });
    }

    private static AdminAccount map(ResultSet rows) throws SQLException {
        return new AdminAccount(rows.getString("username"), rows.getString("password_hash"),
                AdminRole.valueOf(rows.getString("role")), rows.getLong("created_at"),
                rows.getLong("last_login"), rows.getString("totp_secret"));
    }

    private static void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}