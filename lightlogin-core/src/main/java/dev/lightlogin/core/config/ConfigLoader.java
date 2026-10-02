package dev.lightlogin.core.config;

import dev.lightlogin.core.captcha.CaptchaEngine;
import dev.lightlogin.core.crypto.Argon2Parameters;
import dev.lightlogin.core.policy.PasswordPolicyConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a {@link LightLoginConfig} from any {@link ConfigSource}.
 *
 * <p>Every read supplies an explicit fallback, so a missing key, a section that does not exist and
 * an entirely absent file all yield the documented default rather than a zero or a crash. Values
 * that fail their own validation are corrected by the records' compact constructors, which is why
 * the loader can stay a flat list of reads.</p>
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    /** Loads the whole configuration tree. */
    public static LightLoginConfig load(ConfigSource source) {
        Objects.requireNonNull(source, "source");
        LightLoginConfig defaults = LightLoginConfig.defaults();
        return new LightLoginConfig(
                source.getInt("config-version", LightLoginConfig.CURRENT_VERSION),
                source.getString("language", defaults.language()),
                loadDatabase(source, defaults.database()),
                loadSecurity(source, defaults.security()),
                loadCaptcha(source, defaults.captcha()),
                loadRateLimit(source, defaults.rateLimit()),
                loadSafety(source, defaults.safety()),
                loadVoidWorld(source, defaults.voidWorld()),
                loadMail(source, defaults.mail()),
                loadWeb(source, defaults.web()),
                loadLogin(source, defaults.login()),
                loadLibraries(source, defaults.libraries()));
    }

    private static LibrariesConfig loadLibraries(ConfigSource s, LibrariesConfig d) {
        List<String> repositories = s.getStringList("libraries.repositories");
        return new LibrariesConfig(
                repositories.isEmpty() ? d.repositories() : repositories,
                s.getBoolean("libraries.auto-download", d.autoDownload()),
                s.getLong("libraries.connect-timeout-millis", d.connectTimeoutMillis()),
                s.getLong("libraries.read-timeout-millis", d.readTimeoutMillis()));
    }

    private static DatabaseConfig loadDatabase(ConfigSource s, DatabaseConfig d) {
        return new DatabaseConfig(
                DatabaseConfig.DatabaseType.parse(s.getString("database.type", d.type().name())),
                s.getString("database.host", d.host()),
                s.getInt("database.port", d.port()),
                s.getString("database.name", d.database()),
                s.getString("database.username", d.username()),
                s.getString("database.password", d.encryptedPassword()),
                s.getString("database.sqlite-file", d.sqliteFile()),
                s.getInt("database.pool-size", d.poolSize()),
                s.getLong("database.connection-timeout-millis", d.connectionTimeoutMillis()));
    }

    private static SecurityConfig loadSecurity(ConfigSource s, SecurityConfig d) {
        // Clamp each value up to the OWASP floor BEFORE constructing: the record's canonical
        // constructor rejects sub-floor values outright, so clamping afterwards would throw.
        Argon2Parameters floor = Argon2Parameters.FLOOR;
        Argon2Parameters argon = new Argon2Parameters(
                Math.max(s.getInt("security.argon2.memory-kib", d.argon().memoryKib()), floor.memoryKib()),
                Math.max(s.getInt("security.argon2.iterations", d.argon().iterations()), floor.iterations()),
                Math.clamp(s.getInt("security.argon2.parallelism", d.argon().parallelism()), 1, 16),
                Math.max(s.getInt("security.argon2.salt-bytes", d.argon().saltBytes()), floor.saltBytes()),
                Math.max(s.getInt("security.argon2.hash-bytes", d.argon().hashBytes()), floor.hashBytes()));

        PasswordPolicyConfig policy = loadPolicy(s, d.passwordPolicy());

        return new SecurityConfig(
                argon,
                s.getString("security.pepper-env", d.pepperEnvVar()),
                s.getString("security.key-file", d.keyFile()),
                s.getLong("security.session-ttl-millis", d.sessionTtlMillis()),
                s.getInt("security.max-failed-attempts", d.maxFailedAttempts()),
                s.getLong("security.lockout-millis", d.lockoutMillis()),
                s.getBoolean("security.mask-commands-in-logs", d.maskCommandsInLogs()),
                policy,
                s.getInt("security.audit-retention-days", d.auditRetentionDays()));
    }

    private static PasswordPolicyConfig loadPolicy(ConfigSource s, PasswordPolicyConfig d) {
        Set<Character> specials = new LinkedHashSet<>(d.allowedSpecial());
        List<String> configuredSpecials = s.getStringList("password-policy.allowed-special");
        if (!configuredSpecials.isEmpty()) {
            specials.clear();
            for (String entry : configuredSpecials) {
                if (!entry.isEmpty()) {
                    specials.add(entry.charAt(0));
                }
            }
        }
        return new PasswordPolicyConfig(
                s.getInt("password-policy.min-length", d.minLength()),
                s.getInt("password-policy.max-length", d.maxLength()),
                s.getInt("password-policy.min-uppercase", d.minUppercase()),
                s.getInt("password-policy.min-lowercase", d.minLowercase()),
                s.getInt("password-policy.min-digits", d.minDigits()),
                s.getInt("password-policy.min-special", d.minSpecial()),
                specials,
                s.getBoolean("password-policy.deny-username", d.denyUsername()),
                s.getBoolean("password-policy.deny-common", d.denyCommon()),
                s.getStringList("password-policy.banned-substrings"));
    }

    private static CaptchaConfig loadCaptcha(ConfigSource s, CaptchaConfig d) {
        CaptchaEngine.Mode mode;
        try {
            mode = CaptchaEngine.Mode.valueOf(s.getString("captcha.mode", d.mode().name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = d.mode();
        }
        return new CaptchaConfig(
                s.getBoolean("captcha.enabled", d.enabled()),
                mode,
                s.getInt("captcha.max-attempts", d.maxAttempts()),
                s.getLong("captcha.ttl-millis", d.ttlMillis()),
                s.getLong("captcha.cooldown-millis", d.cooldownMillis()),
                s.getInt("captcha.difficulty-bits", d.difficultyBits()),
                s.getBoolean("captcha.require-for-register", d.requireForRegister()),
                s.getBoolean("captcha.require-for-login", d.requireForLogin()),
                s.getBoolean("captcha.punish-on-failure", d.punishOnFailure()));
    }

    private static RateLimitConfig loadRateLimit(ConfigSource s, RateLimitConfig d) {
        return new RateLimitConfig(
                s.getDouble("rate-limit.connection-burst", d.connectionBurst()),
                s.getDouble("rate-limit.connection-rate", d.connectionRate()),
                s.getDouble("rate-limit.login-burst", d.loginBurst()),
                s.getDouble("rate-limit.login-rate", d.loginRate()),
                s.getDouble("rate-limit.command-burst", d.commandBurst()),
                s.getDouble("rate-limit.command-rate", d.commandRate()),
                s.getInt("rate-limit.max-concurrent-auth", d.maxConcurrentAuth()),
                s.getLong("rate-limit.join-throttle-millis", d.joinThrottleMillis()),
                s.getDouble("rate-limit.max-joins-per-second", d.maxJoinsPerSecond()),
                s.getBoolean("rate-limit.auto-ban-on-flood", d.autoBanOnFlood()));
    }

    private static SafetyConfig loadSafety(ConfigSource s, SafetyConfig d) {
        return new SafetyConfig(
                s.getInt("safety.max-players-per-ip", d.maxPlayersPerIp()),
                s.getInt("safety.max-registrations-per-ip", d.maxRegistrationsPerIp()),
                s.getBoolean("safety.ip-bans-enabled", d.ipBansEnabled()),
                s.getBoolean("safety.auto-ban-on-bruteforce", d.autoBanOnBruteForce()),
                s.getLong("safety.bruteforce-ban-millis", d.bruteForceBanMillis()),
                s.getBoolean("safety.country-blocking.enabled", d.countryBlockingEnabled()),
                upperCase(s.getStringList("safety.country-blocking.blocked")),
                upperCase(s.getStringList("safety.country-blocking.allowed")),
                s.getString("safety.country-blocking.geoip-database", d.geoIpDatabase()),
                s.getStringList("safety.whitelisted-ips"));
    }

    private static VoidWorldConfig loadVoidWorld(ConfigSource s, VoidWorldConfig d) {
        String mode = s.getString("void-world.mode", d.endStyleVoid() ? "THE_END" : "NORMAL");
        return new VoidWorldConfig(
                s.getBoolean("void-world.enabled", d.enabled()),
                s.getString("void-world.name", d.worldName()),
                "THE_END".equalsIgnoreCase(mode),
                s.getDouble("void-world.spawn-y", d.spawnY()),
                s.getBoolean("void-world.return-to-original", d.returnToOriginal()));
    }

    private static MailConfig loadMail(ConfigSource s, MailConfig d) {
        return new MailConfig(
                s.getBoolean("email.enabled", d.enabled()),
                s.getString("email.smtp-host", d.host()),
                s.getInt("email.smtp-port", d.port()),
                s.getBoolean("email.use-tls", d.useTls()),
                s.getString("email.account", d.account()),
                s.getString("email.password", d.encryptedPassword()),
                s.getString("email.sender-name", d.senderName()),
                s.getString("email.subject", d.subject()),
                s.getStringList("email.body"),
                s.getInt("email.recovery-password-length", d.recoveryLength()),
                s.getLong("email.recovery-cooldown-millis", d.cooldownMillis()),
                s.getLong("email.connection-timeout-millis", d.connectionTimeoutMillis()));
    }

    private static WebConfig loadWeb(ConfigSource s, WebConfig d) {
        return new WebConfig(
                s.getBoolean("web-panel.enabled", d.enabled()),
                s.getString("web-panel.bind-address", d.bindAddress()),
                s.getInt("web-panel.port", d.port()),
                s.getBoolean("web-panel.allow-external-access", d.allowExternalAccess()),
                s.getBoolean("web-panel.trust-proxy-headers", d.trustProxyHeaders()),
                s.getLong("web-panel.session-ttl-millis", d.sessionTtlMillis()),
                s.getInt("web-panel.max-login-attempts", d.maxLoginAttempts()),
                s.getLong("web-panel.login-lockout-millis", d.loginLockoutMillis()),
                s.getBoolean("web-panel.require-totp", d.requireTotp()),
                s.getInt("web-panel.page-size", d.pageSize()),
                s.getString("web-panel.external-redirect-url", d.externalRedirectUrl()));
    }

    private static LoginConfig loadLogin(ConfigSource s, LoginConfig d) {
        return new LoginConfig(
                s.getLong("login.timeout-millis", d.timeoutMillis()),
                s.getBoolean("login.blindness", d.blindness()),
                s.getBoolean("login.titles.enabled", d.titlesEnabled()),
                s.getBoolean("login.action-bar.enabled", d.actionBarEnabled()),
                s.getInt("login.reminder-seconds", d.reminderSeconds()),
                s.getStringList("login.allowed-commands"),
                s.getLong("login.command-delay-millis", d.commandDelayMillis()),
                s.getBoolean("login.teleport.enabled", d.teleportOnJoin()),
                s.getString("login.teleport.login-location", d.loginLocation()),
                s.getString("login.teleport.return-location", d.returnLocation()),
                s.getBoolean("login.auto-login-after-register", d.autoLoginAfterRegister()));
    }

    private static List<String> upperCase(List<String> values) {
        return values.stream().map(v -> v.toUpperCase(Locale.ROOT)).toList();
    }
}