package dev.lightlogin.core.port;

import dev.lightlogin.core.model.Session;

import java.util.Optional;

/** Storage port for login sessions. Tokens are stored only as digests. */
public interface SessionRepository {

    void create(Session session);

    Optional<Session> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    void deleteByUuid(String uuid);

    /** Deletes every session that has expired at {@code nowMillis}; returns the count removed. */
    int deleteExpired(long nowMillis);

    long count();
}