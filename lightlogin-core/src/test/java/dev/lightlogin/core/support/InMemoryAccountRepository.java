package dev.lightlogin.core.support;

import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.port.AccountRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** An in-memory {@link AccountRepository} for tests. */
public final class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> byUuid = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findByUuid(String uuid) {
        return Optional.ofNullable(byUuid.get(uuid));
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        String lower = username.toLowerCase(Locale.ROOT);
        return byUuid.values().stream().filter(a -> a.usernameLower().equals(lower)).findFirst();
    }

    @Override
    public void save(Account account) {
        byUuid.put(account.uuid(), account);
    }

    @Override
    public void updatePassword(String uuid, String passwordHash) {
        mutate(uuid, a -> a.withPasswordHash(passwordHash));
    }

    @Override
    public void recordLogin(String uuid, String ip, long nowMillis) {
        mutate(uuid, a -> a.withLogin(ip, nowMillis));
    }

    @Override
    public int incrementFailedAttempts(String uuid) {
        Account account = byUuid.get(uuid);
        if (account == null) {
            return 0;
        }
        Account updated = account.withFailedAttempts(account.failedAttempts() + 1);
        byUuid.put(uuid, updated);
        return updated.failedAttempts();
    }

    @Override
    public void resetFailedAttempts(String uuid) {
        mutate(uuid, a -> a.withFailedAttempts(0).withLockedUntil(0));
    }

    @Override
    public void lock(String uuid, long untilMillis) {
        mutate(uuid, a -> a.withLockedUntil(untilMillis));
    }

    @Override
    public void updateEmail(String uuid, String email) {
        mutate(uuid, a -> a.withEmail(email));
    }

    @Override
    public void delete(String uuid) {
        byUuid.remove(uuid);
    }

    @Override
    public long countByRegistrationIp(String ip) {
        return byUuid.values().stream().filter(a -> ip.equals(a.registrationIp())).count();
    }

    @Override
    public List<Account> page(int offset, int limit) {
        List<Account> all = new ArrayList<>(byUuid.values());
        all.sort(Comparator.comparingLong(Account::createdAtMillis).reversed());
        return slice(all, offset, limit);
    }

    @Override
    public List<Account> search(String query, int offset, int limit) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<Account> all = new ArrayList<>(byUuid.values());
        all.removeIf(a -> !a.usernameLower().contains(lower));
        all.sort(Comparator.comparingLong(Account::createdAtMillis).reversed());
        return slice(all, offset, limit);
    }

    @Override
    public long count() {
        return byUuid.size();
    }

    @Override
    public long countByLastIpSince(String ip, long sinceMillis) {
        return byUuid.values().stream()
                .filter(a -> ip.equals(a.lastIp()) && a.lastLoginMillis() >= sinceMillis)
                .count();
    }

    private void mutate(String uuid, java.util.function.UnaryOperator<Account> op) {
        byUuid.computeIfPresent(uuid, (k, v) -> op.apply(v));
    }

    private static List<Account> slice(List<Account> all, int offset, int limit) {
        int from = Math.min(Math.max(0, offset), all.size());
        int to = Math.min(from + Math.max(0, limit), all.size());
        return List.copyOf(all.subList(from, to));
    }
}