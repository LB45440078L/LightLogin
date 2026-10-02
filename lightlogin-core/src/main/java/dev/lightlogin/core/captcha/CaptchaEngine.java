package dev.lightlogin.core.captcha;

import dev.lightlogin.core.crypto.TokenGenerator;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Issues and verifies {@link CaptchaChallenge}s, one outstanding challenge per subject.
 *
 * <p>The engine owns the policy that makes the CAPTCHA actually stop bots rather than merely
 * annoy humans: a bounded number of attempts, a hard expiry, a minimum interval between issues for
 * the same subject, and a periodic sweep so abandoned challenges cannot accumulate. It is
 * deliberately free of any server API so its behaviour is covered by unit tests.</p>
 */
public final class CaptchaEngine {

    /** Which challenge strategy to issue. */
    public enum Mode {
        /** Arithmetic question — human-friendly, the default. */
        ARITHMETIC,
        /** Hashcash proof-of-work — hostile to automated floods. */
        PROOF_OF_WORK
    }

    private final Mode mode;
    private final int maxAttempts;
    private final long ttlMillis;
    private final long issueCooldownMillis;
    private final int difficultyBits;
    private final TokenGenerator tokens;
    private final RandomGenerator random;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    private static final class State {
        final CaptchaChallenge challenge;
        int attempts;
        long lastIssuedMillis;

        State(CaptchaChallenge challenge, long nowMillis) {
            this.challenge = challenge;
            this.lastIssuedMillis = nowMillis;
        }
    }

    public CaptchaEngine(Mode mode, int maxAttempts, long ttlMillis, long issueCooldownMillis, int difficultyBits) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.ttlMillis = ttlMillis;
        this.issueCooldownMillis = Math.max(0, issueCooldownMillis);
        this.difficultyBits = difficultyBits;
        this.tokens = new TokenGenerator();
        this.random = RandomGeneratorFactory.getDefault().create();
    }

    /**
     * Issues (or re-issues) a challenge for a subject.
     *
     * <p>Re-issuing within the cooldown window returns the existing challenge so a player cannot
     * farm fresh easy questions by spamming a command.</p>
     */
    public CaptchaChallenge issue(String subject, long nowMillis) {
        State existing = states.get(subject);
        if (existing != null
                && !existing.challenge.isExpired(nowMillis)
                && nowMillis - existing.lastIssuedMillis < issueCooldownMillis) {
            return existing.challenge;
        }
        CaptchaChallenge challenge = create(subject, nowMillis);
        states.put(subject, new State(challenge, nowMillis));
        return challenge;
    }

    private CaptchaChallenge create(String subject, long nowMillis) {
        // TokenGenerator enforces a 128-bit minimum, so identifiers use 16 bytes.
        String id = subject + ':' + tokens.token(16);
        return switch (mode) {
            case ARITHMETIC -> CaptchaChallenge.Arithmetic.generate(id, random, ttlMillis, nowMillis);
            case PROOF_OF_WORK -> new CaptchaChallenge.ProofOfWork(
                    id, tokens.token(16), difficultyBits, nowMillis + ttlMillis);
        };
    }

    /** The outstanding challenge for a subject, or {@code null}. */
    public CaptchaChallenge current(String subject) {
        State state = states.get(subject);
        return state == null ? null : state.challenge;
    }

    /** Verifies an answer and applies the attempt policy. */
    public CaptchaResult verify(String subject, String answer, long nowMillis) {
        State state = states.get(subject);
        if (state == null) {
            return CaptchaResult.NO_CHALLENGE;
        }
        if (state.challenge.isExpired(nowMillis)) {
            states.remove(subject);
            return CaptchaResult.EXPIRED;
        }
        if (state.attempts >= maxAttempts) {
            states.remove(subject);
            return CaptchaResult.TOO_MANY_ATTEMPTS;
        }
        if (state.challenge.validate(answer)) {
            states.remove(subject);
            return CaptchaResult.VERIFIED;
        }
        state.attempts++;
        int remaining = maxAttempts - state.attempts;
        if (remaining <= 0) {
            states.remove(subject);
            return CaptchaResult.TOO_MANY_ATTEMPTS;
        }
        return new CaptchaResult.WrongAnswer(remaining);
    }

    /** Removes any state for a subject (e.g. on quit). */
    public void clear(String subject) {
        states.remove(subject);
    }

    /** Drops expired challenges. Safe to call from a periodic task. */
    public void sweep(long nowMillis) {
        states.entrySet().removeIf(e -> e.getValue().challenge.isExpired(nowMillis));
    }

    public int size() {
        return states.size();
    }

    public Mode mode() {
        return mode;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}