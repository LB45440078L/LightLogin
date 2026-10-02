package dev.lightlogin.core.model;

import java.util.Objects;

/**
 * An administrator of the local web panel. Separate from player accounts and from Minecraft
 * permissions: the panel has its own credential store so a compromised player account cannot reach
 * the administrative surface.
 *
 * @param username        unique login name
 * @param passwordHash    Argon2id hash
 * @param role            authorisation level
 * @param createdAtMillis creation time
 * @param lastLoginMillis last successful panel login; 0 when never
 * @param totpSecret      optional base32 TOTP secret for two-factor authentication
 */
public record AdminAccount(String username, String passwordHash, AdminRole role,
                           long createdAtMillis, long lastLoginMillis, String totpSecret) {

    public AdminAccount {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
    }

    public boolean hasTotp() {
        return totpSecret != null && !totpSecret.isBlank();
    }

    public AdminAccount withLastLogin(long nowMillis) {
        return new AdminAccount(username, passwordHash, role, createdAtMillis, nowMillis, totpSecret);
    }

    public AdminAccount withPasswordHash(String newHash) {
        return new AdminAccount(username, newHash, role, createdAtMillis, lastLoginMillis, totpSecret);
    }
}