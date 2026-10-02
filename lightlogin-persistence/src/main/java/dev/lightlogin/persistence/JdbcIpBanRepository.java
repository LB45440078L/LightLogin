package dev.lightlogin.persistence;

import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.port.IpBanRepository;
import dev.lightlogin.core.util.IpAddress;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link IpBanRepository}. */
public final class JdbcIpBanRepository extends JdbcSupport implements IpBanRepository {

    public JdbcIpBanRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void save(IpBan ban) {
        transaction("save", connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ll_ip_bans SET reason = ?, actor = ?, created_at = ?, expires_at = ?, source = ? "
                            + "WHERE target = ?")) {
                update.setString(1, ban.reason());
                update.setString(2, ban.actor());
                update.setLong(3, ban.createdAtMillis());
                update.setLong(4, ban.expiresAtMillis());
                update.setString(5, ban.source().name());
                update.setString(6, ban.target());
                if (update.executeUpdate() > 0) {
                    return;
                }
            } catch (SQLException e) {
                throw e;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ll_ip_bans (target, reason, actor, created_at, expires_at, source) "
                            + "VALUES (?,?,?,?,?,?)")) {
                insert.setString(1, ban.target());
                insert.setString(2, ban.reason());
                insert.setString(3, ban.actor());
                insert.setLong(4, ban.createdAtMillis());
                insert.setLong(5, ban.expiresAtMillis());
                insert.setString(6, ban.source().name());
                try {
                    insert.executeUpdate();
                } catch (SQLException e) {
                    // Another writer inserted the same target first: the desired end state holds.
                    if (!isConstraintViolation(e)) {
                        throw e;
                    }
                }
            }
        });
    }

    @Override
    public Optional<IpBan> findByTarget(String target) {
        return query("findByTarget", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT target, reason, actor, created_at, expires_at, source FROM ll_ip_bans WHERE target = ?")) {
                ps.setString(1, target);
                try (ResultSet rows = ps.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void deleteByTarget(String target) {
        transaction("deleteByTarget", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ll_ip_bans WHERE target = ?")) {
                ps.setString(1, target);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<IpBan> all() {
        return query("all", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT target, reason, actor, created_at, expires_at, source FROM ll_ip_bans");
                 ResultSet rows = ps.executeQuery()) {
                List<IpBan> bans = new ArrayList<>();
                while (rows.next()) {
                    bans.add(map(rows));
                }
                return bans;
            }
        });
    }

    @Override
    public Optional<IpBan> findActiveFor(String ip, long nowMillis) {
        IpAddress address = IpAddress.ofOrNull(ip);
        if (address == null) {
            return Optional.empty();
        }
        // Fetch candidates and match in application code: CIDR containment is not portable SQL.
        return query("findActiveFor", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT target, reason, actor, created_at, expires_at, source FROM ll_ip_bans "
                            + "WHERE expires_at = 0 OR expires_at > ?")) {
                ps.setLong(1, nowMillis);
                try (ResultSet rows = ps.executeQuery()) {
                    while (rows.next()) {
                        IpBan ban = map(rows);
                        if (address.isInRange(ban.target())) {
                            return Optional.of(ban);
                        }
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public int purgeExpired(long nowMillis) {
        return transactionReturning("purgeExpired", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ll_ip_bans WHERE expires_at <> 0 AND expires_at <= ?")) {
                ps.setLong(1, nowMillis);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public long count() {
        return query("count", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM ll_ip_bans");
                 ResultSet rows = ps.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        });
    }

    private static IpBan map(ResultSet rows) throws SQLException {
        return new IpBan(rows.getString("target"), rows.getString("reason"), rows.getString("actor"),
                rows.getLong("created_at"), rows.getLong("expires_at"),
                IpBan.BanSource.valueOf(rows.getString("source")));
    }
}