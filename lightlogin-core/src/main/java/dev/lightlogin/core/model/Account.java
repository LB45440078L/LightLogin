package dev.lightlogin.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A stored account.
 *
 * <p>Immutable: every mutation produces a new instance through the {@code withX} helpers, which
 * removes a whole class of aliasing bugs where a cached account is mutated behind a repository's
 * back. The password is stored only as a self-describing Argon2id hash; there is no plaintext or
 * separately-stored salt field.</p>
 *
 * @param uuid               the player's Mojang UUID, lowercase dashed string
 * @param username           the last known username (case-preserving)
 * @param usernameLower      lower-cased username for uniqueness lookups
 * @param passwordHash       the encoded Argon2id hash, or {@code null} for an unregistered account
 * @param email              optional recovery email
 * @param status             account lifecycle state
 * @param failedAttempts     consecutive failed logins since the last success
 * @param lockedUntilMillis  epoch millis until which the account is locked; 0 when not locked
 * @param createdAtMillis    epoch millis of registration
 * @param lastLoginMillis    epoch millis of the last successful login; 0 when never
 * @param lastIp             last successfully authenticated IP
 * @param registrationIp     IP recorded at registration (used for per-IP registration limits)
 */
public record Account(
        String uuid,
        String username,
        String usernameLower,
        String passwordHash,
        String email,
        AccountStatus status,
        int failedAttempts,
        long lockedUntilMillis,
        long createdAtMillis,
        long lastLoginMillis,
        String lastIp,
        String registrationIp) {

    public Account {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(usernameLower, "usernameLower");
        Objects.requireNonNull(status, "status");
    }

    /** Builds a brand new active account with a fresh password hash. */
    public static Account create(String uuid, String username, String passwordHash, String email,
                                 String registrationIp, long nowMillis) {
        return new Account(uuid, username, username.toLowerCase(java.util.Locale.ROOT), passwordHash,
                email, AccountStatus.ACTIVE, 0, 0, nowMillis, 0, null, registrationIp);
    }

    /** Whether the account has a password set (i.e. is registered). */
    public boolean isRegistered() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public Optional<String> emailOptional() {
        return Optional.ofNullable(email);
    }

    /** Whether the account is currently locked relative to {@code nowMillis}. */
    public boolean isLocked(long nowMillis) {
        return status == AccountStatus.LOCKED && nowMillis < lockedUntilMillis;
    }

    public Account withPasswordHash(String newHash) {
        return new Account(uuid, username, usernameLower, newHash, email, AccountStatus.ACTIVE,
                0, 0, createdAtMillis, lastLoginMillis, lastIp, registrationIp);
    }

    public Account withFailedAttempts(int attempts) {
        return new Account(uuid, username, usernameLower, passwordHash, email, status, attempts,
                lockedUntilMillis, createdAtMillis, lastLoginMillis, lastIp, registrationIp);
    }

    public Account withLockedUntil(long untilMillis) {
        AccountStatus newStatus = untilMillis > 0 ? AccountStatus.LOCKED : AccountStatus.ACTIVE;
        return new Account(uuid, username, usernameLower, passwordHash, email, newStatus,
                failedAttempts, untilMillis, createdAtMillis, lastLoginMillis, lastIp, registrationIp);
    }

    public Account withLogin(String ip, long nowMillis) {
        return new Account(uuid, username, usernameLower, passwordHash, email, AccountStatus.ACTIVE,
                0, 0, createdAtMillis, nowMillis, ip, registrationIp);
    }

    public Account withEmail(String newEmail) {
        return new Account(uuid, username, usernameLower, passwordHash, newEmail, status,
                failedAttempts, lockedUntilMillis, createdAtMillis, lastLoginMillis, lastIp, registrationIp);
    }

    public Account withUsername(String newUsername) {
        return new Account(uuid, newUsername, newUsername.toLowerCase(java.util.Locale.ROOT), passwordHash,
                email, status, failedAttempts, lockedUntilMillis, createdAtMillis, lastLoginMillis,
                lastIp, registrationIp);
    }
}