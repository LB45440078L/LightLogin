package dev.lightlogin.paper.bootstrap;

import dev.lightlogin.core.config.LightLoginConfig;
import dev.lightlogin.core.captcha.CaptchaEngine;
import dev.lightlogin.core.concurrent.AsyncExecutor;
import dev.lightlogin.core.crypto.PasswordHasher;
import dev.lightlogin.core.crypto.SecretBox;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.geo.CountryResolver;
import dev.lightlogin.core.mail.MailSender;
import dev.lightlogin.core.port.AccountRepository;
import dev.lightlogin.core.port.AdminAccountRepository;
import dev.lightlogin.core.port.AuditRepository;
import dev.lightlogin.core.port.IpBanRepository;
import dev.lightlogin.core.port.SessionRepository;
import dev.lightlogin.core.ratelimit.RateLimiterRegistry;
import dev.lightlogin.core.security.AccessToken;
import dev.lightlogin.core.security.SecretRedactor;
import dev.lightlogin.core.security.SecurityContext;
import dev.lightlogin.core.service.AuditService;
import dev.lightlogin.core.service.AuthService;
import dev.lightlogin.core.service.IpBanService;
import dev.lightlogin.core.service.RecoveryService;
import dev.lightlogin.core.service.SessionService;
import dev.lightlogin.core.web.AdminPanel;
import dev.lightlogin.core.web.AdminService;
import dev.lightlogin.paper.auth.AuthGate;
import dev.lightlogin.paper.log.ConsoleBanner;
import dev.lightlogin.paper.log.SecretLogFilter;
import dev.lightlogin.paper.messages.MessageService;
import dev.lightlogin.paper.world.VoidWorldService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * The composition root: every shared service, assembled once at startup and handed to the
 * components that need it.
 *
 * <p>Built with a builder rather than a twenty-argument constructor, and immutable once built, so
 * no component can mutate the wiring another component depends on. The configuration is the single
 * exception — it is {@code volatile} and swapped wholesale on reload, which is precisely how a
 * reload reaches every holder.</p>
 */
public final class PluginContext {

    private final JavaPlugin plugin;
    private volatile LightLoginConfig config;
    private final MessageService messages;
    private final ConsoleBanner banner;
    private final SecretRedactor redactor;
    private final SecurityContext security;
    private final AccessToken internalToken;
    private final PasswordHasher hasher;
    private final SecretBox secretBox;
    private final TokenGenerator tokens;
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final IpBanRepository bans;
    private final AuditRepository auditRepository;
    private final AdminAccountRepository adminAccounts;
    private final AuthService authService;
    private final SessionService sessionService;
    private final RecoveryService recoveryService;
    private final IpBanService ipBanService;
    private final AuditService auditService;
    private final CaptchaEngine captcha;
    private final AuthGate authGate;
    private final AsyncExecutor async;
    private final RateLimiterRegistry loginLimiter;
    private final RateLimiterRegistry commandLimiter;
    private final RateLimiterRegistry connectionLimiter;
    private final RateLimiterRegistry panelLimiter;
    private final VoidWorldService voidWorld;
    private final MailSender mail;
    private final CountryResolver countryResolver;
    private final AdminService adminService;
    private final AdminPanel adminPanel;
    private final SecretLogFilter logFilter;
    private final AutoCloseable persistenceCloser;

    private PluginContext(Builder builder) {
        this.plugin = require(builder.plugin, "plugin");
        this.config = require(builder.config, "config");
        this.messages = require(builder.messages, "messages");
        this.banner = require(builder.banner, "banner");
        this.redactor = require(builder.redactor, "redactor");
        this.security = require(builder.security, "security");
        this.internalToken = require(builder.internalToken, "internalToken");
        this.hasher = require(builder.hasher, "hasher");
        this.secretBox = builder.secretBox;
        this.tokens = require(builder.tokens, "tokens");
        this.accounts = require(builder.accounts, "accounts");
        this.sessions = require(builder.sessions, "sessions");
        this.bans = require(builder.bans, "bans");
        this.auditRepository = require(builder.auditRepository, "auditRepository");
        this.adminAccounts = require(builder.adminAccounts, "adminAccounts");
        this.authService = require(builder.authService, "authService");
        this.sessionService = require(builder.sessionService, "sessionService");
        this.recoveryService = builder.recoveryService;
        this.ipBanService = require(builder.ipBanService, "ipBanService");
        this.auditService = require(builder.auditService, "auditService");
        this.captcha = require(builder.captcha, "captcha");
        this.authGate = require(builder.authGate, "authGate");
        this.async = require(builder.async, "async");
        this.loginLimiter = require(builder.loginLimiter, "loginLimiter");
        this.commandLimiter = require(builder.commandLimiter, "commandLimiter");
        this.connectionLimiter = require(builder.connectionLimiter, "connectionLimiter");
        this.panelLimiter = require(builder.panelLimiter, "panelLimiter");
        this.voidWorld = require(builder.voidWorld, "voidWorld");
        this.mail = require(builder.mail, "mail");
        this.countryResolver = require(builder.countryResolver, "countryResolver");
        this.adminService = require(builder.adminService, "adminService");
        this.adminPanel = builder.adminPanel;
        this.logFilter = builder.logFilter;
        this.persistenceCloser = builder.persistenceCloser;
    }

    private static <T> T require(T value, String name) {
        return Objects.requireNonNull(value, "PluginContext." + name + " was not provided");
    }

    /** Replaces the configuration after a reload. */
    public void setConfig(LightLoginConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "newConfig");
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public LightLoginConfig config() {
        return config;
    }

    public MessageService messages() {
        return messages;
    }

    public ConsoleBanner banner() {
        return banner;
    }

    public SecretRedactor redactor() {
        return redactor;
    }

    public SecurityContext security() {
        return security;
    }

    public AccessToken internalToken() {
        return internalToken;
    }

    public PasswordHasher hasher() {
        return hasher;
    }

    public SecretBox secretBox() {
        return secretBox;
    }

    public TokenGenerator tokens() {
        return tokens;
    }

    public AccountRepository accounts() {
        return accounts;
    }

    public SessionRepository sessions() {
        return sessions;
    }

    public IpBanRepository bans() {
        return bans;
    }

    public AuditRepository auditRepository() {
        return auditRepository;
    }

    public AdminAccountRepository adminAccounts() {
        return adminAccounts;
    }

    public AuthService authService() {
        return authService;
    }

    public SessionService sessionService() {
        return sessionService;
    }

    public RecoveryService recoveryService() {
        return recoveryService;
    }

    public IpBanService ipBanService() {
        return ipBanService;
    }

    public AuditService auditService() {
        return auditService;
    }

    public CaptchaEngine captcha() {
        return captcha;
    }

    public AuthGate authGate() {
        return authGate;
    }

    public AsyncExecutor async() {
        return async;
    }

    public RateLimiterRegistry loginLimiter() {
        return loginLimiter;
    }

    public RateLimiterRegistry commandLimiter() {
        return commandLimiter;
    }

    public RateLimiterRegistry connectionLimiter() {
        return connectionLimiter;
    }

    public RateLimiterRegistry panelLimiter() {
        return panelLimiter;
    }

    public VoidWorldService voidWorld() {
        return voidWorld;
    }

    public MailSender mail() {
        return mail;
    }

    public CountryResolver countryResolver() {
        return countryResolver;
    }

    public AdminService adminService() {
        return adminService;
    }

    public AdminPanel adminPanel() {
        return adminPanel;
    }

    public SecretLogFilter logFilter() {
        return logFilter;
    }

    /** Closes the persistence layer, when one was created. */
    public void closePersistence() {
        if (persistenceCloser != null) {
            try {
                persistenceCloser.close();
            } catch (Exception ignored) {
                // Shutdown is best-effort.
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Assembler for {@link PluginContext}. */
    public static final class Builder {
        private JavaPlugin plugin;
        private LightLoginConfig config;
        private MessageService messages;
        private ConsoleBanner banner;
        private SecretRedactor redactor;
        private SecurityContext security;
        private AccessToken internalToken;
        private PasswordHasher hasher;
        private SecretBox secretBox;
        private TokenGenerator tokens;
        private AccountRepository accounts;
        private SessionRepository sessions;
        private IpBanRepository bans;
        private AuditRepository auditRepository;
        private AdminAccountRepository adminAccounts;
        private AuthService authService;
        private SessionService sessionService;
        private RecoveryService recoveryService;
        private IpBanService ipBanService;
        private AuditService auditService;
        private CaptchaEngine captcha;
        private AuthGate authGate;
        private AsyncExecutor async;
        private RateLimiterRegistry loginLimiter;
        private RateLimiterRegistry commandLimiter;
        private RateLimiterRegistry connectionLimiter;
        private RateLimiterRegistry panelLimiter;
        private VoidWorldService voidWorld;
        private MailSender mail;
        private CountryResolver countryResolver;
        private AdminService adminService;
        private AdminPanel adminPanel;
        private SecretLogFilter logFilter;
        private AutoCloseable persistenceCloser;

        public Builder plugin(JavaPlugin v) {
            this.plugin = v;
            return this;
        }

        public Builder config(LightLoginConfig v) {
            this.config = v;
            return this;
        }

        public Builder messages(MessageService v) {
            this.messages = v;
            return this;
        }

        public Builder banner(ConsoleBanner v) {
            this.banner = v;
            return this;
        }

        public Builder redactor(SecretRedactor v) {
            this.redactor = v;
            return this;
        }

        public Builder security(SecurityContext v) {
            this.security = v;
            return this;
        }

        public Builder internalToken(AccessToken v) {
            this.internalToken = v;
            return this;
        }

        public Builder hasher(PasswordHasher v) {
            this.hasher = v;
            return this;
        }

        public Builder secretBox(SecretBox v) {
            this.secretBox = v;
            return this;
        }

        public Builder tokens(TokenGenerator v) {
            this.tokens = v;
            return this;
        }

        public Builder accounts(AccountRepository v) {
            this.accounts = v;
            return this;
        }

        public Builder sessions(SessionRepository v) {
            this.sessions = v;
            return this;
        }

        public Builder bans(IpBanRepository v) {
            this.bans = v;
            return this;
        }

        public Builder auditRepository(AuditRepository v) {
            this.auditRepository = v;
            return this;
        }

        public Builder adminAccounts(AdminAccountRepository v) {
            this.adminAccounts = v;
            return this;
        }

        public Builder authService(AuthService v) {
            this.authService = v;
            return this;
        }

        public Builder sessionService(SessionService v) {
            this.sessionService = v;
            return this;
        }

        public Builder recoveryService(RecoveryService v) {
            this.recoveryService = v;
            return this;
        }

        public Builder ipBanService(IpBanService v) {
            this.ipBanService = v;
            return this;
        }

        public Builder auditService(AuditService v) {
            this.auditService = v;
            return this;
        }

        public Builder captcha(CaptchaEngine v) {
            this.captcha = v;
            return this;
        }

        public Builder authGate(AuthGate v) {
            this.authGate = v;
            return this;
        }

        public Builder async(AsyncExecutor v) {
            this.async = v;
            return this;
        }

        public Builder loginLimiter(RateLimiterRegistry v) {
            this.loginLimiter = v;
            return this;
        }

        public Builder commandLimiter(RateLimiterRegistry v) {
            this.commandLimiter = v;
            return this;
        }

        public Builder connectionLimiter(RateLimiterRegistry v) {
            this.connectionLimiter = v;
            return this;
        }

        public Builder panelLimiter(RateLimiterRegistry v) {
            this.panelLimiter = v;
            return this;
        }

        public Builder voidWorld(VoidWorldService v) {
            this.voidWorld = v;
            return this;
        }

        public Builder mail(MailSender v) {
            this.mail = v;
            return this;
        }

        public Builder countryResolver(CountryResolver v) {
            this.countryResolver = v;
            return this;
        }

        public Builder adminService(AdminService v) {
            this.adminService = v;
            return this;
        }

        public Builder adminPanel(AdminPanel v) {
            this.adminPanel = v;
            return this;
        }

        public Builder logFilter(SecretLogFilter v) {
            this.logFilter = v;
            return this;
        }

        public Builder persistenceCloser(AutoCloseable v) {
            this.persistenceCloser = v;
            return this;
        }

        public PluginContext build() {
            return new PluginContext(this);
        }
    }
}