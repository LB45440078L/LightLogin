package dev.lightlogin.core.config;

import dev.lightlogin.core.captcha.CaptchaEngine;

/**
 * Anti-bot configuration.
 *
 * @param enabled             master switch
 * @param mode                challenge strategy
 * @param maxAttempts         attempts before punishment
 * @param ttlMillis           challenge lifetime
 * @param cooldownMillis      minimum interval between re-issues for one subject
 * @param difficultyBits      proof-of-work difficulty (PoW mode only)
 * @param requireForRegister  whether registration requires a solved challenge
 * @param requireForLogin     whether login requires a solved challenge
 * @param punishOnFailure     whether exhausting attempts triggers the configured punishment
 */
public record CaptchaConfig(
        boolean enabled,
        CaptchaEngine.Mode mode,
        int maxAttempts,
        long ttlMillis,
        long cooldownMillis,
        int difficultyBits,
        boolean requireForRegister,
        boolean requireForLogin,
        boolean punishOnFailure) {

    public CaptchaConfig {
        if (mode == null) {
            mode = CaptchaEngine.Mode.ARITHMETIC;
        }
        if (maxAttempts < 1) {
            maxAttempts = 2;
        }
        if (ttlMillis < 10_000) {
            ttlMillis = 120_000;
        }
        if (cooldownMillis < 0) {
            cooldownMillis = 0;
        }
        if (difficultyBits < 8 || difficultyBits > 28) {
            difficultyBits = 16;
        }
    }

    public static CaptchaConfig defaults() {
        return new CaptchaConfig(true, CaptchaEngine.Mode.ARITHMETIC, 3, 120_000, 5_000, 16,
                true, false, true);
    }
}