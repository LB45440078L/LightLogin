package dev.lightlogin.core.config;

import dev.lightlogin.core.crypto.Argon2Parameters;
import dev.lightlogin.core.policy.PasswordPolicyConfig;

import java.util.Objects;

/**
 * Security-critical settings: hashing strength, session lifetime, lockout policy and secret
 * sourcing.
 *
 * @param argon               Argon2id work factors
 * @param pepperEnvVar        name of an environment variable holding the pepper, or empty
 * @param keyFile             path to the 256-bit master key file
 * @param sessionTtlMillis    how long a login session lasts
 * @param maxFailedAttempts   failed logins before a lockout
 * @param lockoutMillis       lockout duration
 * @param maskCommandsInLogs  whether password-bearing commands are masked everywhere
 * @param passwordPolicy      composition rules
 * @param auditRetentionDays  audit entries older than this are purged
 */
public record SecurityConfig(
        Argon2Parameters argon,
        String pepperEnvVar,
        String keyFile,
        long sessionTtlMillis,
        int maxFailedAttempts,
        long lockoutMillis,
        boolean maskCommandsInLogs,
        PasswordPolicyConfig passwordPolicy,
        int auditRetentionDays) {

    public SecurityConfig {
        Objects.requireNonNull(argon, "argon");
        Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        pepperEnvVar = pepperEnvVar == null ? "LIGHTLOGIN_PEPPER" : pepperEnvVar;
        keyFile = keyFile == null ? "lightlogin.key" : keyFile;
        if (sessionTtlMillis < 60_000) {
            sessionTtlMillis = 60_000;
        }
        if (maxFailedAttempts < 1) {
            maxFailedAttempts = 5;
        }
        if (lockoutMillis < 0) {
            lockoutMillis = 0;
        }
        if (auditRetentionDays < 1) {
            auditRetentionDays = 90;
        }
    }

    public static SecurityConfig defaults() {
        return new SecurityConfig(Argon2Parameters.BALANCED, "LIGHTLOGIN_PEPPER", "lightlogin.key",
                12 * 60 * 60 * 1000L, 5, 15 * 60 * 1000L, true, PasswordPolicyConfig.DEFAULT, 90);
    }
}