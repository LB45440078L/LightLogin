package dev.lightlogin.core.web;

import dev.lightlogin.core.config.WebConfig;
import dev.lightlogin.core.crypto.Argon2Parameters;
import dev.lightlogin.core.crypto.Argon2idPasswordHasher;
import dev.lightlogin.core.crypto.Pepper;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AdminRole;
import dev.lightlogin.core.ratelimit.RateLimiterRegistry;
import dev.lightlogin.core.security.SecurityContext;
import dev.lightlogin.core.security.SecretRedactor;
import dev.lightlogin.core.service.AuditService;
import dev.lightlogin.core.support.InMemoryAccountRepository;
import dev.lightlogin.core.support.InMemoryAdminAccountRepository;
import dev.lightlogin.core.support.InMemoryAuditRepository;
import dev.lightlogin.core.support.InMemoryIpBanRepository;
import dev.lightlogin.core.support.InMemorySessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the admin panel: starts the real HTTP server on an ephemeral port and drives
 * the full login → dashboard → action flow with a real client.
 */
class AdminPanelTest {

    private static final Pattern CSRF = Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"");

    private AdminPanel panel;
    private InMemoryAccountRepository accounts;
    private InMemoryAdminAccountRepository admins;
    private String bootstrapPassword;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new InMemoryAccountRepository();
        admins = new InMemoryAdminAccountRepository();
        InMemoryIpBanRepository bans = new InMemoryIpBanRepository();
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        InMemoryAuditRepository auditRepo = new InMemoryAuditRepository();

        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(Argon2Parameters.OWASP_MINIMUM, Pepper.none());
        SecurityContext security = new SecurityContext();
        AuditService audit = new AuditService(auditRepo, new SecretRedactor(true));
        TokenGenerator tokens = new TokenGenerator();

        AdminService service = new AdminService(security, accounts, bans, auditRepo, sessions, hasher,
                tokens, audit);
        AdminSessionStore store = new AdminSessionStore(tokens, 30 * 60 * 1000L);
        WebConfig config = new WebConfig(true, "127.0.0.1", 0, false, false,
                30 * 60 * 1000L, 5, 60_000, false, 25, "");

        panel = new AdminPanel(config, service, store, admins, hasher,
                new RateLimiterRegistry(100, 100, 60_000_000_000L), audit, security, security.bootToken());
        panel.start();
        baseUrl = "http://127.0.0.1:" + panel.port();

        bootstrapPassword = panel.ensureBootstrapAdmin().orElseThrow();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void tearDown() {
        panel.close();
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookieFrom(HttpResponse<?> response, String name) {
        Optional<String> header = response.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "=")).findFirst();
        return header.map(h -> h.split(";")[0]).orElse(null);
    }

    @Test
    @DisplayName("an unauthenticated request is redirected to the login page")
    void unauthenticatedRedirect() throws Exception {
        HttpResponse<String> response = get("/", null);
        assertEquals(303, response.statusCode());
        assertEquals("/login", response.headers().firstValue("Location").orElse(""));
    }

    @Test
    @DisplayName("the login page carries a CSRF cookie and security headers")
    void loginPage() throws Exception {
        HttpResponse<String> response = get("/login", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("LightLogin administration"));
        assertFalse(response.headers().allValues("Set-Cookie").isEmpty());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertTrue(response.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'none'"));
    }

    @Test
    @DisplayName("the full login flow issues a session and reaches the dashboard")
    void loginFlow() throws Exception {
        String csrfCookie = cookieFrom(get("/login", null), "ll_csrf");
        assertTrue(csrfCookie != null);

        HttpResponse<String> login = post("/login",
                "csrf=" + csrfCookie.split("=")[1] + "&username=admin&password=" + bootstrapPassword, csrfCookie);
        assertEquals(303, login.statusCode());

        String sessionCookie = cookieFrom(login, AdminPanel.COOKIE);
        assertTrue(sessionCookie != null, "a session cookie must be issued");

        HttpResponse<String> dashboard = get("/", sessionCookie);
        assertEquals(200, dashboard.statusCode());
        assertTrue(dashboard.body().contains("Dashboard"));
        assertTrue(dashboard.body().contains("Accounts"));
    }

    @Test
    @DisplayName("wrong credentials are refused")
    void wrongCredentials() throws Exception {
        String csrfCookie = cookieFrom(get("/login", null), "ll_csrf");
        HttpResponse<String> login = post("/login",
                "csrf=" + csrfCookie.split("=")[1] + "&username=admin&password=wrong", csrfCookie);
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("Invalid credentials"));
    }

    @Test
    @DisplayName("a missing CSRF token on login is refused")
    void loginCsrfEnforced() throws Exception {
        cookieFrom(get("/login", null), "ll_csrf");
        HttpResponse<String> login = post("/login", "username=admin&password=" + bootstrapPassword, "ll_csrf=nope");
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("expired"));
    }

    @Test
    @DisplayName("a moderator action requires a valid session CSRF token")
    void actionRequiresCsrf() throws Exception {
        String sessionCookie = loginAsAdmin();

        HttpResponse<String> noCsrf = post("/action", "action=ban&target=1.2.3.4&csrf=wrong", sessionCookie);
        assertEquals(403, noCsrf.statusCode());
    }

    @Test
    @DisplayName("a moderator can ban and unban an address")
    void banAndUnban() throws Exception {
        String sessionCookie = loginAsAdmin();
        String csrf = sessionCsrf(sessionCookie);

        HttpResponse<String> ban = post("/action",
                "action=ban&target=203.0.113.50&reason=testing&duration=0&csrf=" + csrf, sessionCookie);
        assertEquals(303, ban.statusCode());

        HttpResponse<String> bansPage = get("/bans", sessionCookie);
        assertTrue(bansPage.body().contains("203.0.113.50"));

        HttpResponse<String> unban = post("/action", "action=unban&target=203.0.113.50&csrf=" + csrf, sessionCookie);
        assertEquals(303, unban.statusCode());
        assertFalse(get("/bans", sessionCookie).body().contains("203.0.113.50"));
    }

    @Test
    @DisplayName("a moderator can reset a player's password")
    void resetPlayerPassword() throws Exception {
        accounts.save(Account.create("11111111-1111-1111-1111-111111111111", "steve", "hash", null,
                "1.2.3.4", System.currentTimeMillis()));
        String sessionCookie = loginAsAdmin();
        String csrf = sessionCsrf(sessionCookie);

        HttpResponse<String> reset = post("/action",
                "action=reset&uuid=11111111-1111-1111-1111-111111111111&csrf=" + csrf, sessionCookie);
        assertEquals(303, reset.statusCode());

        String flash = reset.headers().firstValue("Location").orElse("");
        assertTrue(flash.contains("New+temporary+password"), "flash should carry the new password: " + flash);
    }

    @Test
    @DisplayName("the players page lists accounts")
    void playersPage() throws Exception {
        accounts.save(Account.create("11111111-1111-1111-1111-111111111111", "steve", "hash", null,
                "1.2.3.4", System.currentTimeMillis()));
        String sessionCookie = loginAsAdmin();
        HttpResponse<String> page = get("/players", sessionCookie);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("steve"));
    }

    @Test
    @DisplayName("signing out destroys the session")
    void logout() throws Exception {
        String sessionCookie = loginAsAdmin();
        assertEquals(303, get("/logout", sessionCookie).statusCode());
        assertEquals(303, get("/", sessionCookie).statusCode());
    }

    @Test
    @DisplayName("the bootstrap admin is only created once")
    void bootstrapOnce() {
        assertTrue(panel.ensureBootstrapAdmin().isEmpty());
        assertEquals(1, admins.count());
    }

    @Test
    @DisplayName("a non-loopback bind is refused unless external access is enabled")
    void externalBindRefused() {
        WebConfig config = new WebConfig(true, "0.0.0.0", 0, false, false, 60_000, 5, 1000, false, 25, "");
        AdminPanel strict = new AdminPanel(config, null, null, null, null, null, null, null, null);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, strict::start);
    }

    private String loginAsAdmin() throws Exception {
        String csrfCookie = cookieFrom(get("/login", null), "ll_csrf");
        HttpResponse<String> login = post("/login",
                "csrf=" + csrfCookie.split("=")[1] + "&username=admin&password=" + bootstrapPassword, csrfCookie);
        return cookieFrom(login, AdminPanel.COOKIE);
    }

    private String sessionCsrf(String sessionCookie) throws Exception {
        Matcher matcher = CSRF.matcher(get("/bans", sessionCookie).body());
        assertTrue(matcher.find(), "the bans page must embed a CSRF token");
        return matcher.group(1);
    }
}