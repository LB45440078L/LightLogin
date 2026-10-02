package dev.lightlogin.core.service;

import dev.lightlogin.core.config.SafetyConfig;
import dev.lightlogin.core.geo.CountryResolver;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.port.IpBanRepository;
import dev.lightlogin.core.security.AuditAction;
import dev.lightlogin.core.util.IpAddress;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Decides whether an address may connect, combining explicit bans, country blocking and
 * whitelists.
 *
 * <p>The checks are ordered cheapest-first and short-circuit: a whitelist hit skips everything
 * else, then explicit bans, then country rules. A cached ban list avoids a database round trip on
 * every join while still expiring entries.</p>
 */
public final class IpBanService {

    private final IpBanRepository repository;
    private final SafetyConfig config;
    private final CountryResolver countryResolver;
    private final LongSupplier clock;

    public IpBanService(IpBanRepository repository, SafetyConfig config, CountryResolver countryResolver) {
        this(repository, config, countryResolver, System::currentTimeMillis);
    }

    IpBanService(IpBanRepository repository, SafetyConfig config, CountryResolver countryResolver,
                 LongSupplier clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.config = Objects.requireNonNull(config, "config");
        this.countryResolver = Objects.requireNonNull(countryResolver, "countryResolver");
        this.clock = clock;
    }

    /** The reason a connection is refused, or empty when it is allowed. */
    public sealed interface Verdict permits Verdict.Allowed, Verdict.Banned, Verdict.CountryBlocked {

        record Allowed() implements Verdict {
        }

        record Banned(String target, String reason) implements Verdict {
        }

        record CountryBlocked(String country) implements Verdict {
        }

        Verdict ALLOWED = new Allowed();
    }

    /** Evaluates whether {@code ip} may connect. */
    public Verdict check(String ip) {
        if (ip == null || ip.isBlank()) {
            return Verdict.ALLOWED;
        }
        if (isWhitelisted(ip)) {
            return Verdict.ALLOWED;
        }
        if (config.ipBansEnabled()) {
            Optional<IpBan> ban = repository.findActiveFor(ip, clock.getAsLong());
            if (ban.isPresent()) {
                IpBan b = ban.get();
                return new Verdict.Banned(b.target(), b.reason());
            }
        }
        if (config.countryBlockingEnabled() && countryResolver.isAvailable()) {
            String country = countryResolver.countryOf(ip);
            if (country != null) {
                if (config.blockedCountries().contains(country)) {
                    return new Verdict.CountryBlocked(country);
                }
                if (!config.allowedCountries().isEmpty() && !config.allowedCountries().contains(country)) {
                    return new Verdict.CountryBlocked(country);
                }
            }
        }
        return Verdict.ALLOWED;
    }

    /** Adds a ban. */
    public void ban(IpBan ban) {
        repository.save(ban);
    }

    /** Removes a ban for a target; returns whether one existed. */
    public boolean unban(String target) {
        boolean existed = repository.findByTarget(target).isPresent();
        if (existed) {
            repository.deleteByTarget(target);
        }
        return existed;
    }

    public List<IpBan> listBans() {
        return repository.all();
    }

    /** Removes expired temporary bans. */
    public int purgeExpired() {
        return repository.purgeExpired(clock.getAsLong());
    }

    /** Records a ban in the audit trail (kept separate so callers control the actor). */
    public void auditBan(String actor, IpBan ban, AuditService audit) {
        audit.record(actor, AuditAction.IP_BANNED, ban.target(),
                ban.reason().isBlank() ? ban.source().name() : ban.reason(), "");
    }

    private boolean isWhitelisted(String ip) {
        if (config.whitelistedIps().isEmpty()) {
            return false;
        }
        IpAddress address = IpAddress.ofOrNull(ip);
        if (address == null) {
            return false;
        }
        for (String entry : config.whitelistedIps()) {
            if (address.isInRange(entry)) {
                return true;
            }
        }
        return false;
    }

    public SafetyConfig config() {
        return config;
    }
}