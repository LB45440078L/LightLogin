package dev.lightlogin.core.support;

import dev.lightlogin.core.model.AdminAccount;
import dev.lightlogin.core.port.AdminAccountRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** An in-memory {@link AdminAccountRepository} for tests. */
public final class InMemoryAdminAccountRepository implements AdminAccountRepository {

    private final Map<String, AdminAccount> byName = new ConcurrentHashMap<>();

    @Override
    public Optional<AdminAccount> findByUsername(String username) {
        return username == null ? Optional.empty() : Optional.ofNullable(byName.get(username));
    }

    @Override
    public void save(AdminAccount account) {
        byName.put(account.username(), account);
    }

    @Override
    public void delete(String username) {
        byName.remove(username);
    }

    @Override
    public void recordLogin(String username, long nowMillis) {
        byName.computeIfPresent(username, (k, v) -> v.withLastLogin(nowMillis));
    }

    @Override
    public List<AdminAccount> all() {
        return List.copyOf(byName.values());
    }

    @Override
    public long count() {
        return byName.size();
    }
}