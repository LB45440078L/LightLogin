package dev.lightlogin.core.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of an authentication or registration attempt.
 *
 * <p>A sealed hierarchy rather than an enum plus out-parameters: each case carries exactly the data
 * its handler needs and the compiler enforces that every case is handled. This is the single
 * source of truth for the async auth pipeline's result.</p>
 */
public sealed interface AuthResult
        permits AuthResult.Success, AuthResult.WrongPassword, AuthResult.NotRegistered,
                AuthResult.AlreadyRegistered, AuthResult.Locked, AuthResult.CaptchaRequired,
                AuthResult.RateLimited, AuthResult.PolicyRejected, AuthResult.IpBanned,
                AuthResult.RegistrationLimitReached, AuthResult.Error {

    /** Authentication succeeded. */
    record Success(String uuid, boolean passwordUpgraded) implements AuthResult {
        public Success {
            Objects.requireNonNull(uuid, "uuid");
        }
    }

    /** The password did not match; {@code attemptsRemaining} before a lockout. */
    record WrongPassword(int attemptsRemaining) implements AuthResult {
    }

    /** No account exists for this player. */
    record NotRegistered() implements AuthResult {
    }

    /** Registration attempted for an already-registered account. */
    record AlreadyRegistered() implements AuthResult {
    }

    /** The account is locked until {@code untilMillis}. */
    record Locked(long untilMillis) implements AuthResult {
    }

    /** A CAPTCHA must be completed first. */
    record CaptchaRequired() implements AuthResult {
    }

    /** The caller is rate limited; {@code retryAfterMillis} is the wait. */
    record RateLimited(long retryAfterMillis) implements AuthResult {
    }

    /** The password failed the configured policy. */
    record PolicyRejected(List<String> violations) implements AuthResult {
        public PolicyRejected {
            violations = List.copyOf(violations);
        }
    }

    /** The source address is banned. */
    record IpBanned(String target, String reason) implements AuthResult {
    }

    /** The per-IP registration limit for this address has been reached. */
    record RegistrationLimitReached(int limit) implements AuthResult {
    }

    /** An unexpected failure occurred. */
    record Error(String message) implements AuthResult {
        public Error {
            message = message == null ? "unknown error" : message;
        }
    }
}