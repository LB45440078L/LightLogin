package dev.lightlogin.core.port;

import dev.lightlogin.core.model.AuditEntry;

import java.util.List;

/** Append-only storage port for the audit trail. */
public interface AuditRepository {

    void append(AuditEntry entry);

    /** The most recent {@code limit} entries, newest first. */
    List<AuditEntry> recent(int limit);

    /** A page of entries, newest first. */
    List<AuditEntry> page(int offset, int limit);

    /** Entries for a specific subject, newest first. */
    List<AuditEntry> forSubject(String subject, int limit);

    long count();

    /** Deletes entries older than {@code beforeMillis}; returns the count removed. */
    int purgeOlderThan(long beforeMillis);
}