package dev.lightlogin.persistence;

import dev.lightlogin.core.model.Session;
import dev.lightlogin.core.port.SessionRepository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** JDBC implementation of {@link SessionRepository}. */
public final class JdbcSessionRepository extends JdbcSupport implements SessionRepository {

    public JdbcSessionRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void create(Session session) {
        transaction("create", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ll_sessions (token_hash, uuid, ip, issued_at, expires_at) VALUES (?,?,?,?,?)")) {
                ps.setString(1, session.tokenHash());
                ps.setString(2, session.uuid());
                ps.setString(3, session.ip());
                ps.setLong(4, session.issuedAtMillis());
                ps.setLong(5, session.expiresAtMillis());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        return query("findByTokenHash", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT token_hash, uuid, ip, issued_at, expires_at FROM ll_sessions WHERE token_hash = ?")) {
                ps.setString(1, tokenHash);
                try (ResultSet rows = ps.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new Session(rows.getString("token_hash"), rows.getString("uuid"),
                            rows.getString("ip"), rows.getLong("issued_at"), rows.getLong("expires_at")));
                }
            }
        });
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        transaction("deleteByTokenHash", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ll_sessions WHERE token_hash = ?")) {
                ps.setString(1, tokenHash);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void deleteByUuid(String uuid) {
        transaction("deleteByUuid", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ll_sessions WHERE uuid = ?")) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public int deleteExpired(long nowMillis) {
        return transactionReturning("deleteExpired", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ll_sessions WHERE expires_at < ?")) {
                ps.setLong(1, nowMillis);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public long count() {
        return query("count", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM ll_sessions");
                 ResultSet rows = ps.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        });
    }
}