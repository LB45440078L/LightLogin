package dev.lightlogin.core.model;

import java.util.Objects;

/**
 * An immutable audit record. Every security-relevant action is appended here, so an incident can be
 * reconstructed from the database alone.
 *
 * @param id              monotonically increasing id (assigned by storage)
 * @param timestampMillis epoch millis
 * @param actor           "console", a player name, an admin name, or "system"
 * @param action          a stable action code, e.g. {@code LOGIN_SUCCESS}
 * @param subject         the affected account/player, may be empty
 * @param detail          free-form, already redacted of secrets
 * @param ip              source address, may be empty
 */
public record AuditEntry(long id, long timestampMillis, String actor, String action,
                         String subject, String detail, String ip) {

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        actor = actor == null ? "system" : actor;
        subject = subject == null ? "" : subject;
        detail = detail == null ? "" : detail;
        ip = ip == null ? "" : ip;
    }

    /** Creates a new entry (id assigned by storage). */
    public static AuditEntry of(long timestampMillis, String actor, String action,
                                String subject, String detail, String ip) {
        return new AuditEntry(0, timestampMillis, actor, action, subject, detail, ip);
    }
}