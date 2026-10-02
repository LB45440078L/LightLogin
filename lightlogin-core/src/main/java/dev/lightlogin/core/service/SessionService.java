package dev.lightlogin.core.service;

import dev.lightlogin.core.config.SecurityConfig;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.model.Session;
import dev.lightlogin.core.port.SessionRepository;
import dev.lightlogin.core.util.Hashing;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Issues and validates login sessions.
 *
 * <p>Sessions let a returning player be recognised across reconnects without re-entering a
 * password, while still binding the session to an IP. The raw token is returned to the caller once
 * and only its digest is stored, so the persisted data cannot be replayed.</p>
 */
public final class SessionService {

    private final SessionRepository repository;
    private final TokenGenerator tokens;
    private final SecurityConfig config;
    private final LongSupplier clock;

    public SessionService(SessionRepository repository, TokenGenerator tokens, SecurityConfig config) {
        this(repository, tokens, config, System::currentTimeMillis);
    }

    SessionService(SessionRepository repository, TokenGenerator tokens, SecurityConfig config,
                   LongSupplier clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = clock;
    }

    /** Creates a session and returns the raw token to hand to the client. */
    public String create(String uuid, String ip) {
        long now = clock.getAsLong();
        String raw = tokens.token(32);
        Session session = new Session(Hashing.sha256Hex(raw), uuid, ip, now,
                now + config.sessionTtlMillis());
        repository.create(session);
        return raw;
    }

    /** Validates a raw token, returning the session when present, unexpired and IP-consistent. */
    public Optional<Session> validate(String rawToken, String ip) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        long now = clock.getAsLong();
        Optional<Session> found = repository.findByTokenHash(Hashing.sha256Hex(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.get();
        if (session.isExpired(now)) {
            repository.deleteByTokenHash(session.tokenHash());
            return Optional.empty();
        }
        if (!session.ip().equals(ip)) {
            // A session used from a different address is treated as compromised.
            repository.deleteByTokenHash(session.tokenHash());
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Invalidates a single session. */
    public void invalidate(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            repository.deleteByTokenHash(Hashing.sha256Hex(rawToken));
        }
    }

    /** Invalidates every session for a player (used on unregister and password change). */
    public void invalidateAll(String uuid) {
        repository.deleteByUuid(uuid);
    }

    /** Removes expired sessions; returns the count. */
    public int purgeExpired() {
        return repository.deleteExpired(clock.getAsLong());
    }
}