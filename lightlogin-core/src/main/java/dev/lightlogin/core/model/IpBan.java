package dev.lightlogin.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A network ban entry. The {@code target} may be a single address or a CIDR block, so a single
 * model covers an individual IP and a whole hosting range.
 *
 * @param target           an IP literal or CIDR block
 * @param reason           operator-supplied reason
 * @param actor            who created the ban ("console", an admin name, or "auto")
 * @param createdAtMillis  creation time
 * @param expiresAtMillis  expiry time; {@code 0} means permanent
 * @param source           how the ban was produced (manual, brute-force, captcha)
 */
public record IpBan(String target, String reason, String actor, long createdAtMillis,
                    long expiresAtMillis, BanSource source) {

    /** How a ban was produced, for reporting and for automatic expiry rules. */
    public enum BanSource {
        /** Added by a moderator or the console. */
        MANUAL,
        /** Triggered automatically by repeated failed logins. */
        BRUTE_FORCE,
        /** Triggered automatically by repeated failed CAPTCHAs. */
        CAPTCHA,
        /** Triggered by the connection/join flood detector. */
        FLOOD
    }

    public IpBan {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        reason = reason == null ? "" : reason;
        actor = actor == null ? "system" : actor;
    }

    public static IpBan permanent(String target, String reason, String actor, long nowMillis, BanSource source) {
        return new IpBan(target, reason, actor, nowMillis, 0, source);
    }

    public static IpBan temporary(String target, String reason, String actor, long nowMillis,
                                  long durationMillis, BanSource source) {
        return new IpBan(target, reason, actor, nowMillis, nowMillis + durationMillis, source);
    }

    public boolean isPermanent() {
        return expiresAtMillis == 0;
    }

    public boolean isActive(long nowMillis) {
        return isPermanent() || nowMillis < expiresAtMillis;
    }

    public Optional<String> reasonOptional() {
        return reason.isBlank() ? Optional.empty() : Optional.of(reason);
    }
}