package dev.lightlogin.core.config;

/**
 * Local administration web panel settings.
 *
 * <p>Defaults bind to the loopback interface only. Exposing the panel on a public interface is a
 * deliberate, documented decision an operator must make, and it is strongly recommended to sit
 * behind a TLS-terminating reverse proxy in that case.</p>
 *
 * @param enabled             master switch
 * @param bindAddress         interface to bind ("127.0.0.1" by default)
 * @param port                TCP port
 * @param allowExternalAccess whether a non-loopback bind is permitted at all
 * @param trustProxyHeaders   whether to trust {@code X-Forwarded-For} (only behind a known proxy)
 * @param sessionTtlMillis    panel session lifetime
 * @param maxLoginAttempts    failed panel logins before a temporary lockout
 * @param loginLockoutMillis  lockout duration for the panel
 * @param requireTotp         whether admins must have a TOTP secret configured
 * @param pageSize            rows per page in list views
 * @param externalRedirectUrl optional URL to redirect unauthenticated visitors to (for an
 *                            externally hosted front end)
 */
public record WebConfig(
        boolean enabled,
        String bindAddress,
        int port,
        boolean allowExternalAccess,
        boolean trustProxyHeaders,
        long sessionTtlMillis,
        int maxLoginAttempts,
        long loginLockoutMillis,
        boolean requireTotp,
        int pageSize,
        String externalRedirectUrl) {

    public WebConfig {
        bindAddress = bindAddress == null || bindAddress.isBlank() ? "127.0.0.1" : bindAddress;
        // Port 0 requests an ephemeral port (useful for tests); anything else out of range is
        // reset to the default rather than failing startup.
        if (port < 0 || port > 65535) {
            port = 8099;
        }
        if (sessionTtlMillis < 60_000) {
            sessionTtlMillis = 30 * 60 * 1000L;
        }
        if (maxLoginAttempts < 1) {
            maxLoginAttempts = 5;
        }
        if (loginLockoutMillis < 0) {
            loginLockoutMillis = 10 * 60 * 1000L;
        }
        if (pageSize < 5 || pageSize > 200) {
            pageSize = 25;
        }
        externalRedirectUrl = externalRedirectUrl == null ? "" : externalRedirectUrl;
    }

    public static WebConfig defaults() {
        return new WebConfig(false, "127.0.0.1", 8099, false, false,
                30 * 60 * 1000L, 5, 10 * 60 * 1000L, false, 25, "");
    }

    /** Whether the configured bind address is a loopback address. */
    public boolean isLoopbackBind() {
        return bindAddress.equals("127.0.0.1") || bindAddress.equals("::1")
                || bindAddress.equals("localhost");
    }
}