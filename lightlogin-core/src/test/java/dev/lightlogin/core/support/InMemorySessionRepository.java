package dev.lightlogin.core.support;

import dev.lightlogin.core.model.Session;
import dev.lightlogin.core.port.SessionRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** An in-memory {@link SessionRepository} for tests. */
public final class InMemorySessionRepository implements SessionRepository {

    private final Map<String, Session> byHash = new ConcurrentHashMap<>();

    @Override
    public void create(Session session) {
        byHash.put(session.tokenHash(), session);
    }

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(byHash.get(tokenHash));
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        byHash.remove(tokenHash);
    }

    @Override
    public void deleteByUuid(String uuid) {
        byHash.values().removeIf(s -> s.uuid().equals(uuid));
    }

    @Override
    public int deleteExpired(long nowMillis) {
        int before = byHash.size();
        byHash.values().removeIf(s -> s.isExpired(nowMillis));
        return before - byHash.size();
    }

    @Override
    public long count() {
        return byHash.size();
    }
}