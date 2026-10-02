package dev.lightlogin.core.config;

import java.util.Objects;

/**
 * The complete, immutable configuration tree.
 *
 * <p>Produced once by {@link ConfigLoader} at startup and passed by reference to the components that
 * need it. Because it is immutable, a reload builds a brand new tree and swaps the reference, which
 * avoids the original plugin's class of bug where a service held a stale configuration object and
 * silently ignored reloads.</p>
 */
public record LightLoginConfig(
        int configVersion,
        String language,
        DatabaseConfig database,
        SecurityConfig security,
        CaptchaConfig captcha,
        RateLimitConfig rateLimit,
        SafetyConfig safety,
        VoidWorldConfig voidWorld,
        MailConfig mail,
        WebConfig web,
        LoginConfig login,
        LibrariesConfig libraries) {

    /** The configuration schema version this build understands. */
    public static final int CURRENT_VERSION = 3;

    public LightLoginConfig {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(security, "security");
        Objects.requireNonNull(captcha, "captcha");
        Objects.requireNonNull(rateLimit, "rateLimit");
        Objects.requireNonNull(safety, "safety");
        Objects.requireNonNull(voidWorld, "voidWorld");
        Objects.requireNonNull(mail, "mail");
        Objects.requireNonNull(web, "web");
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(libraries, "libraries");
        language = language == null || language.isBlank() ? "en" : language;
    }

    /** A complete default tree, used when the configuration file is absent or empty. */
    public static LightLoginConfig defaults() {
        return new LightLoginConfig(CURRENT_VERSION, "en", DatabaseConfig.sqliteDefault(),
                SecurityConfig.defaults(), CaptchaConfig.defaults(), RateLimitConfig.defaults(),
                SafetyConfig.defaults(), VoidWorldConfig.defaults(), MailConfig.defaults(),
                WebConfig.defaults(), LoginConfig.defaults(), LibrariesConfig.defaults());
    }
}