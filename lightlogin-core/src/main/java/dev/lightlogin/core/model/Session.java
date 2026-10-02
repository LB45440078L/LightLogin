package dev.lightlogin.core.model;

import java.util.Objects;

/**
 * A persisted login session. The raw session token is never stored: only its SHA-256 digest is
 * kept, so a database leak cannot be replayed against the server. The raw token lives only in the
 * client's possession.
 *
 * @param tokenHash       SHA-256 hex of the raw token
 * @param uuid            owning player
 * @param ip              IP the session was created from
 * @param issuedAtMillis  creation time
 * @param expiresAtMillis expiry time
 */
public record Session(String tokenHash, String uuid, String ip, long issuedAtMillis, long expiresAtMillis) {

    public Session {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(uuid, "uuid");
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}