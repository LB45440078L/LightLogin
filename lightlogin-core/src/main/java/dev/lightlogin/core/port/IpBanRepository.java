package dev.lightlogin.core.port;

import dev.lightlogin.core.model.IpBan;

import java.util.List;
import java.util.Optional;

/** Storage port for network bans (single addresses and CIDR blocks). */
public interface IpBanRepository {

    void save(IpBan ban);

    Optional<IpBan> findByTarget(String target);

    void deleteByTarget(String target);

    List<IpBan> all();

    /** Returns the first active ban covering {@code ip}, or empty. */
    Optional<IpBan> findActiveFor(String ip, long nowMillis);

    /** Removes expired non-permanent bans; returns the count removed. */
    int purgeExpired(long nowMillis);

    long count();
}