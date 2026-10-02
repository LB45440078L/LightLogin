package dev.lightlogin.core.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.lightlogin.core.config.WebConfig;
import dev.lightlogin.core.crypto.ConstantTime;
import dev.lightlogin.core.crypto.PasswordHasher;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AdminAccount;
import dev.lightlogin.core.model.AdminRole;
import dev.lightlogin.core.model.AuditEntry;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.port.AdminAccountRepository;
import dev.lightlogin.core.ratelimit.RateLimiterRegistry;
import dev.lightlogin.core.security.AccessToken;
import dev.lightlogin.core.security.AuditAction;
import dev.lightlogin.core.security.SecurityContext;
import dev.lightlogin.core.security.Totp;
import dev.lightlogin.core.service.AuditService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * The plugin-hosted administration website.
 *
 * <p>Served from the JDK's built-in HTTP server, so the panel adds no third-party dependency to the
 * shaded jar. It binds to the loopback interface by default and refuses a non-loopback bind unless
 * the operator has explicitly enabled external access, which makes accidental exposure a
 * deliberate act rather than a default.</p>
 *
 * <p>Defences: Argon2id admin credentials separate from player accounts, per-IP login rate
 * limiting, optional TOTP, HttpOnly/SameSite session cookies, per-session CSRF tokens required on
 * every mutating request, a strict Content-Security-Policy and full HTML escaping.</p>
 */
public final class AdminPanel implements AutoCloseable {

    /** Cookie name for the panel session. */
    public static final String COOKIE = "ll_panel";

    private final WebConfig config;
    private final AdminService service;
    private final AdminSessionStore sessions;
    private final AdminAccountRepository admins;
    private final PasswordHasher hasher;
    private final RateLimiterRegistry loginLimiter;
    private final AuditService audit;
    private final AccessToken token;
    private final boolean secureCookie;
    private HttpServer server;

    public AdminPanel(WebConfig config,
                      AdminService service,
                      AdminSessionStore sessions,
                      AdminAccountRepository admins,
                      PasswordHasher hasher,
                      RateLimiterRegistry loginLimiter,
                      AuditService audit,
                      SecurityContext security,
                      AccessToken token) {
        this.config = config;
        this.service = service;
        this.sessions = sessions;
        this.admins = admins;
        this.hasher = hasher;
        this.loginLimiter = loginLimiter;
        this.audit = audit;
        this.token = token;
        this.secureCookie = !config.isLoopbackBind();
    }

    /**
     * Starts the panel.
     *
     * @throws IllegalStateException when a non-loopback bind is attempted without opting in
     * @throws IOException           when the socket cannot be bound
     */
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        if (!config.isLoopbackBind() && !config.allowExternalAccess()) {
            throw new IllegalStateException(
                    "web-panel.bind-address is not loopback but allow-external-access is false; "
                            + "refusing to expose the administration panel");
        }
        server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::dispatch);
        server.start();
    }

    /** Stops the panel. */
    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** The bound port, useful when 0 was requested in tests. */
    public int port() {
        return server == null ? config.port() : server.getAddress().getPort();
    }

    public boolean isRunning() {
        return server != null;
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (!config.externalRedirectUrl().isEmpty() && path.equals("/")) {
                HttpSupport.redirect(exchange, config.externalRedirectUrl());
                return;
            }
            if (path.equals("/login")) {
                if (method.equals("POST")) {
                    handleLogin(exchange);
                } else {
                    renderLogin(exchange, null);
                }
                return;
            }
            if (path.equals("/logout")) {
                handleLogout(exchange);
                return;
            }

            Optional<AdminSessionStore.AdminSession> session = currentSession(exchange);
            if (session.isEmpty()) {
                HttpSupport.redirect(exchange, "/login");
                return;
            }
            AdminSessionStore.AdminSession active = session.get();

            switch (path) {
                case "/" -> handleDashboard(exchange, active);
                case "/players" -> handlePlayers(exchange, active);
                case "/player" -> handlePlayerDetail(exchange, active);
                case "/bans" -> handleBans(exchange, active);
                case "/logs" -> handleLogs(exchange, active);
                case "/action" -> handleAction(exchange, active);
                default -> HttpSupport.sendHtml(exchange, 404,
                        Templates.error(404, "Page not found"), null);
            }
        } catch (RuntimeException e) {
            HttpSupport.sendHtml(exchange, 500,
                    Templates.error(500, "Internal error: " + e.getMessage()), null);
        }
    }

    private Optional<AdminSessionStore.AdminSession> currentSession(HttpExchange exchange) {
        return sessions.validate(HttpSupport.cookie(exchange, COOKIE));
    }

    private void renderLogin(HttpExchange exchange, String error) throws IOException {
        String csrf = new TokenGenerator().token(24);
        // A short-lived CSRF token for the unauthenticated form, carried in a cookie.
        HttpSupport.sendHtml(exchange, 200, Templates.login(error, csrf),
                Map.of("Set-Cookie", "ll_csrf=" + csrf + "; HttpOnly; SameSite=Strict; Path=/"));
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> form = HttpSupport.readForm(exchange);
        String ip = HttpSupport.clientIp(exchange, config.trustProxyHeaders());

        // Login CSRF: the token in the form must match the one issued with the login page cookie.
        String formCsrf = form.get("csrf");
        String cookieCsrf = HttpSupport.cookie(exchange, "ll_csrf");
        if (!ConstantTime.equals(formCsrf, cookieCsrf)) {
            audit.record("panel", AuditAction.SECURITY_VIOLATION, form.getOrDefault("username", ""),
                    "login csrf mismatch", ip);
            renderLogin(exchange, "Your session expired. Please try again.");
            return;
        }

        if (!loginLimiter.tryAcquire(ip)) {
            audit.record("panel", AuditAction.ADMIN_PANEL_LOGIN_FAILURE, form.getOrDefault("username", ""),
                    "rate limited", ip);
            renderLogin(exchange, "Too many attempts. Try again shortly.");
            return;
        }

        String username = form.getOrDefault("username", "");
        String password = form.getOrDefault("password", "");
        String totp = form.getOrDefault("totp", "");

        Optional<AdminAccount> found = admins.findByUsername(username);
        char[] chars = password.toCharArray();
        boolean ok;
        try {
            ok = found.isPresent() && hasher.verify(chars, found.get().passwordHash());
        } finally {
            ConstantTime.wipe(chars);
        }
        if (!ok) {
            audit.record("panel", AuditAction.ADMIN_PANEL_LOGIN_FAILURE, username, "bad credentials", ip);
            renderLogin(exchange, "Invalid credentials.");
            return;
        }
        AdminAccount admin = found.get();
        if (admin.hasTotp() && !Totp.verify(admin.totpSecret(), totp, System.currentTimeMillis())) {
            audit.record("panel", AuditAction.ADMIN_PANEL_LOGIN_FAILURE, username, "bad 2FA", ip);
            renderLogin(exchange, "Invalid two-factor code.");
            return;
        }
        if (config.requireTotp() && !admin.hasTotp()) {
            renderLogin(exchange, "Two-factor authentication is required but not configured for this account.");
            return;
        }

        AdminSessionStore.AdminSession session = sessions.create(admin.username(), admin.role());
        admins.recordLogin(admin.username(), System.currentTimeMillis());
        audit.record(admin.username(), AuditAction.ADMIN_PANEL_LOGIN, admin.username(), "panel login", ip);
        // Set the session cookie BEFORE sending the redirect: headers cannot be added once the
        // response has been committed.
        exchange.getResponseHeaders().set("Set-Cookie", cookieHeader(session.id()));
        HttpSupport.redirect(exchange, "/");
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String raw = HttpSupport.cookie(exchange, COOKIE);
        currentSession(exchange).ifPresent(s -> {
            sessions.destroy(raw);
            audit.record(s.username(), AuditAction.LOGOUT, s.username(), "panel logout", "");
        });
        exchange.getResponseHeaders().set("Set-Cookie", COOKIE + "=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
        HttpSupport.redirect(exchange, "/login");
    }

    private void handleDashboard(HttpExchange exchange, AdminSessionStore.AdminSession session) throws IOException {
        AdminService.Stats stats = service.stats(token);
        List<AuditEntry> recent = service.logs(token, 0, 25);
        HttpSupport.sendHtml(exchange, 200,
                Templates.page("Dashboard", Templates.dashboard(stats, recent), session.csrfToken()), null);
    }

    private void handlePlayers(HttpExchange exchange, AdminSessionStore.AdminSession session) throws IOException {
        int page = parseInt(HttpSupport.queryParam(exchange.getRequestURI(), "page"), 0);
        String query = HttpSupport.queryParam(exchange.getRequestURI(), "q");
        int size = config.pageSize();
        List<Account> accounts = (query == null || query.isBlank())
                ? service.players(token, page * size, size)
                : service.searchPlayers(token, query, page * size, size);
        HttpSupport.sendHtml(exchange, 200,
                Templates.page("Players", Templates.players(accounts, page, size, query), session.csrfToken()), null);
    }

    private void handlePlayerDetail(HttpExchange exchange, AdminSessionStore.AdminSession session) throws IOException {
        String uuid = HttpSupport.queryParam(exchange.getRequestURI(), "uuid");
        Optional<Account> account = uuid == null ? Optional.empty() : service.player(token, uuid);
        if (account.isEmpty()) {
            HttpSupport.sendHtml(exchange, 404, Templates.error(404, "Player not found"), null);
            return;
        }
        String flash = HttpSupport.queryParam(exchange.getRequestURI(), "flash");
        List<AuditEntry> history = service.logsFor(token, uuid, 50);
        HttpSupport.sendHtml(exchange, 200, Templates.page(account.get().username(),
                Templates.playerDetail(account.get(), session.csrfToken(), history, flash),
                session.csrfToken()), null);
    }

    private void handleBans(HttpExchange exchange, AdminSessionStore.AdminSession session) throws IOException {
        List<IpBan> bans = service.bans(token);
        String flash = HttpSupport.queryParam(exchange.getRequestURI(), "flash");
        HttpSupport.sendHtml(exchange, 200,
                Templates.page("Bans", Templates.bans(bans, session.csrfToken(), flash), session.csrfToken()), null);
    }

    private void handleLogs(HttpExchange exchange, AdminSessionStore.AdminSession session) throws IOException {
        int page = parseInt(HttpSupport.queryParam(exchange.getRequestURI(), "page"), 0);
        int size = config.pageSize();
        List<AuditEntry> entries = service.logs(token, page * size, size);
        HttpSupport.sendHtml(exchange, 200,
                Templates.page("Audit log", Templates.logs(entries, page, size), session.csrfToken()), null);
    }

    private void handleAction(HttpExchange exchange, AdminSessionStore.AdminSession session) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            HttpSupport.sendHtml(exchange, 405, Templates.error(405, "Method not allowed"), null);
            return;
        }
        Map<String, String> form = HttpSupport.readForm(exchange);
        if (!ConstantTime.equals(form.get("csrf"), session.csrfToken())) {
            audit.record(session.username(), AuditAction.SECURITY_VIOLATION, "panel", "csrf mismatch", "");
            HttpSupport.sendHtml(exchange, 403, Templates.error(403, "Invalid CSRF token"), null);
            return;
        }
        if (!AdminService.allows(session.role(), AdminRole.MODERATOR)) {
            HttpSupport.sendHtml(exchange, 403, Templates.error(403, "Insufficient permissions"), null);
            return;
        }

        String action = form.getOrDefault("action", "");
        String actor = session.username();
        String flash;
        switch (action) {
            case "reset" -> {
                Optional<String> password = service.resetPassword(token, form.get("uuid"), actor);
                flash = password.map(p -> "New temporary password: " + p).orElse("Player not found");
            }
            case "unregister" -> {
                service.unregister(token, form.get("uuid"), actor);
                flash = "Account unregistered";
            }
            case "ban" -> {
                long duration = parseLong(form.get("duration"), 0);
                service.ban(token, form.getOrDefault("target", ""), form.getOrDefault("reason", ""),
                        duration, actor);
                flash = "Ban added";
            }
            case "ban-last-ip" -> {
                Optional<Account> account = service.player(token, form.get("uuid"));
                if (account.isPresent() && account.get().lastIp() != null) {
                    service.ban(token, account.get().lastIp(), "Banned from panel", 0, actor);
                    flash = "Banned " + account.get().lastIp();
                } else {
                    flash = "No last IP recorded";
                }
            }
            case "unban" -> {
                boolean existed = service.unban(token, form.getOrDefault("target", ""), actor);
                flash = existed ? "Ban removed" : "No such ban";
            }
            default -> {
                HttpSupport.sendHtml(exchange, 400, Templates.error(400, "Unknown action"), null);
                return;
            }
        }
        String back = action.equals("ban") || action.equals("unban") ? "/bans" : "/player?uuid=" + form.get("uuid");
        HttpSupport.redirect(exchange, back + (back.contains("?") ? "&" : "?") + "flash="
                + java.net.URLEncoder.encode(flash, java.nio.charset.StandardCharsets.UTF_8));
    }

    private String cookieHeader(String value) {
        StringBuilder builder = new StringBuilder(COOKIE).append('=').append(value)
                .append("; HttpOnly; SameSite=Strict; Path=/");
        if (secureCookie) {
            builder.append("; Secure");
        }
        return builder.toString();
    }

    /**
     * Creates a bootstrap administrator when none exists.
     *
     * @return the generated password to print once, or empty when an admin already exists
     */
    public Optional<String> ensureBootstrapAdmin() {
        if (admins.count() > 0) {
            return Optional.empty();
        }
        TokenGenerator generator = new TokenGenerator();
        String password = generator.temporaryPassword(16);
        char[] chars = password.toCharArray();
        String hash;
        try {
            hash = hasher.hash(chars);
        } finally {
            ConstantTime.wipe(chars);
        }
        admins.save(new AdminAccount("admin", hash, AdminRole.ADMIN,
                System.currentTimeMillis(), 0, null));
        audit.record("system", AuditAction.STARTUP, "admin", "bootstrap administrator created", "");
        return Optional.of(password);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Retained for symmetry with other components that expose their configuration. */
    public WebConfig config() {
        return config;
    }

    /** A no-op map used by tests to satisfy the dispatch signature. */
    static Map<String, String> emptyHeaders() {
        return new HashMap<>();
    }
}