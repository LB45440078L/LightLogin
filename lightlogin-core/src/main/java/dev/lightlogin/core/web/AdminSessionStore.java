package dev.lightlogin.core.web;

import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.model.AdminRole;
import dev.lightlogin.core.util.Hashing;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of authenticated panel sessions.
 *
 * <p>Panel sessions are not persisted: a restart invalidates them, which is the safe default for an
 * administrative surface. The cookie carries a random identifier whose digest is what the store
 * holds, so a memory disclosure does not hand over a live session. Each session also carries a CSRF
 * token that must be echoed by every state-changing request.</p>
 */
public final class AdminSessionStore {

    /**
     * An authenticated panel session.
     *
     * @param id        raw session id (as held in the cookie)
     * @param username  the administrator
     * @param role      authorisation level
     * @param csrfToken per-session CSRF token
     * @param expiresAt expiry (epoch millis)
     */
    public record AdminSession(String id, String username, AdminRole role, String csrfToken, long expiresAt) {

        public boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private final Map<String, AdminSession> byDigest = new ConcurrentHashMap<>();
    private final TokenGenerator tokens;
    private final long ttlMillis;

    public AdminSessionStore(TokenGenerator tokens, long ttlMillis) {
        this.tokens = tokens;
        this.ttlMillis = ttlMillis;
    }

    /** Creates a new session and returns it (including the raw id for the cookie). */
    public AdminSession create(String username, AdminRole role) {
        String id = tokens.token(32);
        String csrf = tokens.token(24);
        AdminSession session = new AdminSession(id, username, role, csrf, System.currentTimeMillis() + ttlMillis);
        byDigest.put(Hashing.sha256Hex(id), session);
        return session;
    }

    /** Validates a cookie value, dropping it when expired. */
    public Optional<AdminSession> validate(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String digest = Hashing.sha256Hex(rawId);
        AdminSession session = byDigest.get(digest);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(System.currentTimeMillis())) {
            byDigest.remove(digest);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Destroys a session by cookie value. */
    public void destroy(String rawId) {
        if (rawId != null && !rawId.isBlank()) {
            byDigest.remove(Hashing.sha256Hex(rawId));
        }
    }

    /** Destroys every session for a username (used when a password changes). */
    public void destroyForUser(String username) {
        byDigest.values().removeIf(s -> s.username().equals(username));
    }

    /** Removes expired sessions. */
    public int sweep() {
        long now = System.currentTimeMillis();
        int before = byDigest.size();
        byDigest.values().removeIf(s -> s.isExpired(now));
        return before - byDigest.size();
    }

    public int size() {
        return byDigest.size();
    }
}