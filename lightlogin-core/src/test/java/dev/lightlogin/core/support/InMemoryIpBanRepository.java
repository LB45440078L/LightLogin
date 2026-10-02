package dev.lightlogin.core.support;

import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.port.IpBanRepository;
import dev.lightlogin.core.util.IpAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** An in-memory {@link IpBanRepository} for tests. */
public final class InMemoryIpBanRepository implements IpBanRepository {

    private final Map<String, IpBan> byTarget = new ConcurrentHashMap<>();

    @Override
    public void save(IpBan ban) {
        byTarget.put(ban.target(), ban);
    }

    @Override
    public Optional<IpBan> findByTarget(String target) {
        return Optional.ofNullable(byTarget.get(target));
    }

    @Override
    public void deleteByTarget(String target) {
        byTarget.remove(target);
    }

    @Override
    public List<IpBan> all() {
        return List.copyOf(byTarget.values());
    }

    @Override
    public Optional<IpBan> findActiveFor(String ip, long nowMillis) {
        IpAddress address = IpAddress.ofOrNull(ip);
        if (address == null) {
            return Optional.empty();
        }
        for (IpBan ban : byTarget.values()) {
            if (!ban.isActive(nowMillis)) {
                continue;
            }
            if (address.isInRange(ban.target())) {
                return Optional.of(ban);
            }
        }
        return Optional.empty();
    }

    @Override
    public int purgeExpired(long nowMillis) {
        int before = byTarget.size();
        byTarget.values().removeIf(b -> !b.isPermanent() && !b.isActive(nowMillis));
        return before - byTarget.size();
    }

    @Override
    public long count() {
        return byTarget.size();
    }

    /** Snapshot for assertions. */
    public List<IpBan> snapshot() {
        return new ArrayList<>(byTarget.values());
    }
}