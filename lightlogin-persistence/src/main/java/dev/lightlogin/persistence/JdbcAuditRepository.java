package dev.lightlogin.persistence;

import dev.lightlogin.core.model.AuditEntry;
import dev.lightlogin.core.port.AuditRepository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link AuditRepository}.
 *
 * <p>The monotonic id is drawn from a counter row inside the same transaction as the insert. That
 * is portable across the three backends (no identity columns), race-free (the {@code UPDATE} takes
 * a row lock that serialises concurrent writers) and monotonic even across restarts.</p>
 */
public final class JdbcAuditRepository extends JdbcSupport implements AuditRepository {

    private static final String COUNTER = "audit";

    public JdbcAuditRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void append(AuditEntry entry) {
        transaction("append", connection -> {
            long id = nextId(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ll_audit (id, created_at, actor, action, subject, detail, ip) "
                            + "VALUES (?,?,?,?,?,?,?)")) {
                ps.setLong(1, id);
                ps.setLong(2, entry.timestampMillis());
                ps.setString(3, truncate(entry.actor(), 64));
                ps.setString(4, truncate(entry.action(), 48));
                ps.setString(5, truncate(entry.subject(), 64));
                ps.setString(6, truncate(entry.detail(), 512));
                ps.setString(7, truncate(entry.ip(), 45));
                ps.executeUpdate();
            }
        });
    }

    private long nextId(java.sql.Connection connection) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE ll_counters SET value = value + 1 WHERE name = ?")) {
            update.setString(1, COUNTER);
            if (update.executeUpdate() == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO ll_counters (name, value) VALUES (?, 1)")) {
                    insert.setString(1, COUNTER);
                    insert.executeUpdate();
                }
            }
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT value FROM ll_counters WHERE name = ?")) {
            select.setString(1, COUNTER);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    @Override
    public List<AuditEntry> recent(int limit) {
        return page(0, limit);
    }

    @Override
    public List<AuditEntry> page(int offset, int limit) {
        return query("page", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, created_at, actor, action, subject, detail, ip FROM ll_audit "
                            + "ORDER BY id DESC LIMIT ? OFFSET ?")) {
                ps.setInt(1, Math.max(0, limit));
                ps.setInt(2, Math.max(0, offset));
                return mapAll(ps);
            }
        });
    }

    @Override
    public List<AuditEntry> forSubject(String subject, int limit) {
        return query("forSubject", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, created_at, actor, action, subject, detail, ip FROM ll_audit "
                            + "WHERE subject = ? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, subject);
                ps.setInt(2, Math.max(0, limit));
                return mapAll(ps);
            }
        });
    }

    @Override
    public long count() {
        return query("count", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM ll_audit");
                 ResultSet rows = ps.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        });
    }

    @Override
    public int purgeOlderThan(long beforeMillis) {
        return transactionReturning("purgeOlderThan", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ll_audit WHERE created_at < ?")) {
                ps.setLong(1, beforeMillis);
                return ps.executeUpdate();
            }
        });
    }

    private List<AuditEntry> mapAll(PreparedStatement ps) throws SQLException {
        List<AuditEntry> entries = new ArrayList<>();
        try (ResultSet rows = ps.executeQuery()) {
            while (rows.next()) {
                entries.add(new AuditEntry(rows.getLong("id"), rows.getLong("created_at"),
                        rows.getString("actor"), rows.getString("action"), rows.getString("subject"),
                        rows.getString("detail"), rows.getString("ip")));
            }
        }
        return entries;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}