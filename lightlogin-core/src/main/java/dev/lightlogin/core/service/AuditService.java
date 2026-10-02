package dev.lightlogin.core.service;

import dev.lightlogin.core.model.AuditEntry;
import dev.lightlogin.core.port.AuditRepository;
import dev.lightlogin.core.security.SecretRedactor;

import java.util.Objects;

/**
 * Writes audit records, redacting secrets on the way in.
 *
 * <p>Redaction happens at this single choke point rather than at each call site, so it is
 * impossible to add a new audited action that forgets to scrub a token. Storage failures are
 * swallowed and reported through a listener: an audit write must never be able to fail the login
 * it is describing.</p>
 */
public final class AuditService {

    /** Notified when an audit write fails, so the plugin can log it without recursion. */
    public interface FailureListener {
        void onAuditFailure(String action, RuntimeException cause);
    }

    private final AuditRepository repository;
    private final SecretRedactor redactor;
    private final java.util.function.LongSupplier clock;
    private volatile FailureListener failureListener = (action, cause) -> {
    };

    public AuditService(AuditRepository repository, SecretRedactor redactor) {
        this(repository, redactor, System::currentTimeMillis);
    }

    AuditService(AuditRepository repository, SecretRedactor redactor, java.util.function.LongSupplier clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.clock = clock;
    }

    public void setFailureListener(FailureListener listener) {
        this.failureListener = listener == null ? (action, cause) -> {
        } : listener;
    }

    /** Records an event, redacting the detail field. */
    public void record(String actor, String action, String subject, String detail, String ip) {
        AuditEntry entry = AuditEntry.of(clock.getAsLong(), actor, action, subject,
                redactor.redact(detail), ip);
        try {
            repository.append(entry);
        } catch (RuntimeException e) {
            failureListener.onAuditFailure(action, e);
        }
    }

    /** Convenience overload without a detail field. */
    public void record(String actor, String action, String subject, String ip) {
        record(actor, action, subject, "", ip);
    }
}