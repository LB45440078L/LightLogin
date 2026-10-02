package dev.lightlogin.core.captcha;

/**
 * The outcome of verifying a CAPTCHA answer.
 *
 * <p>Modelled as a sealed hierarchy so callers switch exhaustively over the cases; there is no
 * ambiguous boolean that a caller can misinterpret.</p>
 */
public sealed interface CaptchaResult
        permits CaptchaResult.Verified, CaptchaResult.WrongAnswer, CaptchaResult.Expired,
                CaptchaResult.TooManyAttempts, CaptchaResult.NoChallenge {

    /** The answer was correct. */
    record Verified() implements CaptchaResult {
    }

    /** The answer was wrong; {@code attemptsRemaining} is at least 1. */
    record WrongAnswer(int attemptsRemaining) implements CaptchaResult {
    }

    /** The challenge expired before a correct answer was given. */
    record Expired() implements CaptchaResult {
    }

    /** The attempt budget was exhausted; the caller should apply its punishment. */
    record TooManyAttempts() implements CaptchaResult {
    }

    /** No challenge was outstanding for the subject. */
    record NoChallenge() implements CaptchaResult {
    }

    CaptchaResult VERIFIED = new Verified();
    CaptchaResult EXPIRED = new Expired();
    CaptchaResult TOO_MANY_ATTEMPTS = new TooManyAttempts();
    CaptchaResult NO_CHALLENGE = new NoChallenge();
}