package dev.lightlogin.core.config;

/**
 * Anti-flood and backpressure settings.
 *
 * @param connectionBurst        per-IP connection burst allowance
 * @param connectionRate         per-IP sustained connections per second
 * @param loginBurst             per-IP login-attempt burst
 * @param loginRate              per-IP sustained login attempts per second
 * @param commandBurst           per-player command burst
 * @param commandRate            per-player sustained commands per second
 * @param maxConcurrentAuth      ceiling on concurrent authentication tasks
 * @param joinThrottleMillis     minimum spacing between joins globally
 * @param maxJoinsPerSecond      global join-rate ceiling; excess joins are delayed
 * @param autoBanOnFlood         whether a sustained flood auto-bans the source
 */
public record RateLimitConfig(
        double connectionBurst,
        double connectionRate,
        double loginBurst,
        double loginRate,
        double commandBurst,
        double commandRate,
        int maxConcurrentAuth,
        long joinThrottleMillis,
        double maxJoinsPerSecond,
        boolean autoBanOnFlood) {

    public RateLimitConfig {
        if (connectionBurst <= 0) {
            connectionBurst = 8;
        }
        if (connectionRate <= 0) {
            connectionRate = 1.5;
        }
        if (loginBurst <= 0) {
            loginBurst = 6;
        }
        if (loginRate <= 0) {
            loginRate = 0.5;
        }
        if (commandBurst <= 0) {
            commandBurst = 6;
        }
        if (commandRate <= 0) {
            commandRate = 2;
        }
        if (maxConcurrentAuth < 1) {
            maxConcurrentAuth = 8;
        }
        if (joinThrottleMillis < 0) {
            joinThrottleMillis = 0;
        }
        if (maxJoinsPerSecond <= 0) {
            maxJoinsPerSecond = 20;
        }
    }

    public static RateLimitConfig defaults() {
        return new RateLimitConfig(8, 1.5, 6, 0.5, 6, 2, 8, 0, 20, true);
    }
}