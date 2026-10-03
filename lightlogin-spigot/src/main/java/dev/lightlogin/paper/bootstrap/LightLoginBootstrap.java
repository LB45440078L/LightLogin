package dev.lightlogin.paper.bootstrap;

import dev.lightlogin.core.captcha.CaptchaEngine;
import dev.lightlogin.core.concurrent.AsyncExecutor;
import dev.lightlogin.core.config.ConfigLoader;
import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.config.LightLoginConfig;
import dev.lightlogin.core.crypto.Argon2Parameters;
import dev.lightlogin.core.crypto.Argon2idPasswordHasher;
import dev.lightlogin.core.crypto.MasterKey;
import dev.lightlogin.core.crypto.Pepper;
import dev.lightlogin.core.crypto.SecretBox;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.geo.CountryResolver;
import dev.lightlogin.core.mail.MailSender;
import dev.lightlogin.core.policy.PasswordPolicy;
import dev.lightlogin.core.ratelimit.RateLimiterRegistry;
import dev.lightlogin.core.security.IntegrityGuard;
import dev.lightlogin.core.security.SecretRedactor;
import dev.lightlogin.core.security.SecurityContext;
import dev.lightlogin.core.service.AuditService;
import dev.lightlogin.core.service.AuthService;
import dev.lightlogin.core.service.IpBanService;
import dev.lightlogin.core.service.RecoveryService;
import dev.lightlogin.core.service.SessionService;
import dev.lightlogin.core.web.AdminPanel;
import dev.lightlogin.core.web.AdminService;
import dev.lightlogin.core.web.AdminSessionStore;
import dev.lightlogin.paper.command.AccountCommands;
import dev.lightlogin.paper.command.AdminCommands;
import dev.lightlogin.paper.command.AuthCommands;
import dev.lightlogin.paper.command.CommandRegistry;
import dev.lightlogin.paper.api.LightLoginApi;
import dev.lightlogin.paper.config.BukkitConfigSource;
import dev.lightlogin.paper.geo.MaxMindCountryResolver;
import dev.lightlogin.paper.library.Libraries;
import dev.lightlogin.paper.library.LibraryManager;
import dev.lightlogin.paper.gui.ModerationGui;
import dev.lightlogin.paper.gui.PasswordInput;
import dev.lightlogin.paper.listener.AuthRestrictionListener;
import dev.lightlogin.paper.listener.ChatPasswordListener;
import dev.lightlogin.paper.listener.ConnectionListener;
import dev.lightlogin.paper.log.ConsoleBanner;
import dev.lightlogin.paper.log.SecretLogFilter;
import dev.lightlogin.paper.mail.SmtpMailSender;
import dev.lightlogin.paper.messages.MessageService;
import dev.lightlogin.paper.task.MaintenanceTask;
import dev.lightlogin.paper.world.VoidWorldService;
import dev.lightlogin.persistence.PersistenceBootstrap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Optional;

/**
 * Brings the whole plugin up, in order, and takes it down again.
 *
 * <p>Startup is a fixed sequence with a visible console step for each stage, so a slow or failing
 * stage is obvious. Each stage either succeeds, degrades to a documented fallback (no pepper, no
 * mail, no web panel, no GeoIP) or fails the enable with a clear message — it never leaves a
 * half-wired context behind.</p>
 */
public final class LightLoginBootstrap {

    private final JavaPlugin plugin;
    private PluginContext ctx;
    private CommandRegistry commandRegistry;
    private MaintenanceTask maintenanceTask;
    private PasswordInput passwordInput;
    private ModerationGui moderationGui;
    private CountryResolver countryResolver = CountryResolver.DISABLED;

    public LightLoginBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** The wired context, available once {@link #enable()} has completed. */
    public PluginContext context() {
        return ctx;
    }

    /** Enables the plugin. Throws to abort the enable when a critical stage fails. */
    public void enable() {
        ConsoleBanner banner = new ConsoleBanner(plugin);
        plugin.saveDefaultConfig();
        saveBundledResource("messages.yml");
        saveBundledResource("gui.yml");

        LightLoginConfig config = loadConfig();
        banner.print(plugin.getDescription().getVersion(), config.configVersion());
        banner.step("Loading configuration...");
        banner.ok("Configuration schema " + config.configVersion() + " loaded.");

        // Integrity check first: everything after this assumes the JVM is not instrumented.
        IntegrityGuard guard = IntegrityGuard.inspect();
        if (guard.isInstrumented()) {
            guard.warnings().forEach(banner::warn);
        }

        SecurityContext security = new SecurityContext();
        TokenGenerator tokens = new TokenGenerator();
        SecretRedactor redactor = new SecretRedactor(config.security().maskCommandsInLogs());

        banner.step("Preparing secrets...");
        SecretBox secretBox = createSecretBox(config);
        String pepperEnv = System.getenv(config.security().pepperEnvVar());
        Pepper pepper;
        if (pepperEnv != null && pepperEnv.length() >= 32) {
            pepper = Pepper.of(pepperEnv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            redactor.registerSecret(pepperEnv);
            banner.ok("Server-side pepper loaded from " + config.security().pepperEnvVar() + '.');
        } else {
            pepper = Pepper.NONE;
            banner.warn("No pepper configured. Set the " + config.security().pepperEnvVar()
                    + " environment variable (32+ characters) so a database leak cannot be cracked offline.");
        }

        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(config.security().argon(), pepper);
        banner.ok("Argon2id hashing ready: " + config.security().argon() + '.');

        banner.step("Resolving runtime libraries...");
        LibraryManager libraries = new LibraryManager(
                plugin.getDataFolder().toPath().resolve("libs"), config.libraries(), plugin.getLogger());

        banner.step("Connecting to the database...");
        DatabaseConfig database = config.database();
        String databasePassword = decrypt(secretBox, database.encryptedPassword());
        encryptInPlace(secretBox, "database.password", database.encryptedPassword());
        // The driver is not bundled: make it available (server classpath, libs/, or a verified
        // download) and hand the loader to persistence, which loads the driver through it.
        ClassLoader driverLoader = libraries.loaderFor(
                plugin.getClass().getClassLoader(), Libraries.forDatabase(database.type()));
        PersistenceBootstrap persistence = PersistenceBootstrap.start(database,
                databasePassword, plugin.getDataFolder().toPath(), driverLoader);
        banner.ok("Database ready (" + database.type() + ").");

        banner.step("Building services...");
        AuditService auditService = new AuditService(persistence.audit(), redactor);
        auditService.setFailureListener((action, cause) -> plugin.getLogger()
                .warning("Audit write failed for " + action + ": " + cause.getMessage()));
        redactor.registerSecret(security.bootToken().value());

        countryResolver = openCountryResolver(config, libraries);

        PasswordPolicy policy = new PasswordPolicy(config.security().passwordPolicy());
        IpBanService ipBanService = new IpBanService(persistence.bans(), config.safety(), countryResolver);
        RateLimiterRegistry loginLimiter = new RateLimiterRegistry(
                config.rateLimit().loginBurst(), config.rateLimit().loginRate(), 10 * 60 * 1_000_000_000L);
        RateLimiterRegistry commandLimiter = new RateLimiterRegistry(
                config.rateLimit().commandBurst(), config.rateLimit().commandRate(), 60 * 1_000_000_000L);
        RateLimiterRegistry connectionLimiter = new RateLimiterRegistry(
                config.rateLimit().connectionBurst(), config.rateLimit().connectionRate(), 60 * 1_000_000_000L);
        RateLimiterRegistry panelLimiter = new RateLimiterRegistry(
                config.web().maxLoginAttempts(), 0.05, 10 * 60 * 1_000_000_000L);

        AuthService authService = new AuthService(persistence.accounts(), hasher, policy, loginLimiter,
                ipBanService, auditService, config.security(), config.rateLimit());
        SessionService sessionService = new SessionService(persistence.sessions(), tokens, config.security());

        MailSender mailSender = createMailSender(config, secretBox);
        RecoveryService recoveryService = new RecoveryService(persistence.accounts(), hasher,
                sessionService, mailSender, config.mail(), tokens, auditService);

        CaptchaEngine captcha = new CaptchaEngine(config.captcha().mode(), config.captcha().maxAttempts(),
                config.captcha().ttlMillis(), config.captcha().cooldownMillis(), config.captcha().difficultyBits());

        AsyncExecutor async = new AsyncExecutor(config.rateLimit().maxConcurrentAuth(), "lightlogin-auth");
        VoidWorldService voidWorld = new VoidWorldService(plugin, config.voidWorld());
        MessageService messages = new MessageService(plugin, new File(plugin.getDataFolder(), "messages.yml"));

        AdminService adminService = new AdminService(security, persistence.accounts(), persistence.bans(),
                persistence.audit(), persistence.sessions(), hasher, tokens, auditService);

        banner.step("Preparing the login world...");
        if (voidWorld.initialise()) {
            banner.ok("Void login world '" + voidWorld.worldName() + "' is ready.");
        } else if (config.voidWorld().enabled()) {
            banner.warn("The void login world could not be prepared; players will authenticate in place.");
        }

        SecretLogFilter logFilter = installLogFilter(redactor);

        PluginContext context = PluginContext.builder()
                .plugin(plugin)
                .config(config)
                .messages(messages)
                .banner(banner)
                .redactor(redactor)
                .security(security)
                .internalToken(security.bootToken())
                .hasher(hasher)
                .secretBox(secretBox)
                .tokens(tokens)
                .accounts(persistence.accounts())
                .sessions(persistence.sessions())
                .bans(persistence.bans())
                .auditRepository(persistence.audit())
                .adminAccounts(persistence.admins())
                .authService(authService)
                .sessionService(sessionService)
                .recoveryService(recoveryService)
                .ipBanService(ipBanService)
                .auditService(auditService)
                .captcha(captcha)
                .authGate(new dev.lightlogin.paper.auth.AuthGate())
                .async(async)
                .loginLimiter(loginLimiter)
                .commandLimiter(commandLimiter)
                .connectionLimiter(connectionLimiter)
                .panelLimiter(panelLimiter)
                .voidWorld(voidWorld)
                .mail(mailSender)
                .countryResolver(countryResolver)
                .adminService(adminService)
                .logFilter(logFilter)
                .persistenceCloser(persistence)
                .build();
        this.ctx = context;

        // Publish the API before anything can consume it. Two routes on purpose: a static accessor
        // for a plugin that declares a soft dependency, and the services manager, which is the
        // idiomatic way to find a capability without holding a hard reference to this plugin.
        LightLoginApi api = new LightLoginApi(context);
        LightLoginApi.install(api);
        Bukkit.getServicesManager().register(LightLoginApi.class, api, plugin,
                org.bukkit.plugin.ServicePriority.Normal);

        banner.step("Registering commands and listeners...");
        registerCommands(context);
        registerListeners(context);

        banner.step("Starting the administration panel...");
        startAdminPanel(context, security, adminService, panelLimiter, tokens);

        banner.step("Scheduling maintenance...");
        maintenanceTask = new MaintenanceTask(context);
        maintenanceTask.start();

        auditService.record("system", dev.lightlogin.core.security.AuditAction.STARTUP, "plugin",
                "version " + plugin.getDescription().getVersion(), "");
        banner.ok("LightLogin is ready.");
        reportMissingCommandRegistrations();
    }

    /** Disables the plugin, in reverse order of startup. */
    public void disable() {
        if (maintenanceTask != null) {
            maintenanceTask.stop();
        }
        if (ctx != null) {
            if (ctx.adminPanel() != null) {
                ctx.adminPanel().close();
            }
            ctx.auditService().record("system", dev.lightlogin.core.security.AuditAction.SHUTDOWN,
                    "plugin", "shutdown", "");
            if (countryResolver instanceof MaxMindCountryResolver maxMind) {
                maxMind.close();
            }
            if (ctx.mail() != null) {
                ctx.mail().close();
            }
            ctx.async().close();
            ctx.closePersistence();
            // Retire the API last, so a listener that runs during shutdown still finds it.
            LightLoginApi.uninstall();
        }
    }

    /** Reloads the settings that can safely change at runtime. */
    public LightLoginConfig reload() {
        LightLoginConfig config = loadConfig();
        ctx.setConfig(config);
        ctx.messages().reload();
        if (moderationGui != null) {
            moderationGui.reload();
        }
        ctx.banner().ok("Reloaded messages, GUI and login-flow settings.");
        ctx.banner().warn("Changes to security, database, CAPTCHA and rate-limit settings need a restart.");
        return config;
    }

    // ------------------------------------------------------------------ helpers

    private LightLoginConfig loadConfig() {
        return ConfigLoader.load(new BukkitConfigSource(plugin.getConfig()));
    }

    /**
     * Opens the GeoIP database when nation blocking is configured.
     *
     * <p>The reader is not bundled — it and its Jackson dependencies are several megabytes used by
     * one optional feature — so it is resolved through the library manager. A failure to resolve it
     * disables nation blocking instead of failing startup, which is the port's contract.</p>
     */
    private CountryResolver openCountryResolver(LightLoginConfig config, LibraryManager libraries) {
        String database = config.safety().geoIpDatabase();
        if (database == null || database.isBlank()) {
            return CountryResolver.DISABLED;
        }
        try {
            ClassLoader geoLoader = libraries.loaderFor(
                    plugin.getClass().getClassLoader(), Libraries.GEOIP);
            return MaxMindCountryResolver.open(plugin, database, geoLoader);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Nation blocking is disabled: " + e.getMessage());
            return CountryResolver.DISABLED;
        }
    }

    private void saveBundledResource(String name) {
        if (!new File(plugin.getDataFolder(), name).exists()) {
            plugin.saveResource(name, false);
        }
    }

    private SecretBox createSecretBox(LightLoginConfig config) {
        try {
            Path keyFile = plugin.getDataFolder().toPath().resolve(config.security().keyFile());
            byte[] key = MasterKey.loadOrCreate(keyFile);
            plugin.getLogger().info("Master key ready at " + keyFile + " (" + MasterKey.expectedPermissions() + ")");
            return new SecretBox(key);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not prepare the master key (" + e.getMessage()
                    + "); secrets in the configuration will be stored in plaintext.");
            return null;
        }
    }

    private String decrypt(SecretBox box, String value) {
        if (box == null || value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        try {
            return box.decrypt(value);
        } catch (GeneralSecurityException e) {
            plugin.getLogger().severe("Could not decrypt a configuration secret: " + e.getMessage());
            return "";
        }
    }

    /** Encrypts a plaintext secret in the config file so the next run reads ciphertext. */
    private void encryptInPlace(SecretBox box, String path, String currentValue) {
        if (box == null || currentValue == null || currentValue.isBlank()
                || SecretBox.isEncrypted(currentValue)) {
            return;
        }
        try {
            plugin.getConfig().set(path, box.encrypt(currentValue));
            plugin.saveConfig();
            plugin.getLogger().info("Encrypted " + path + " with the master key.");
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not encrypt " + path + ": " + e.getMessage());
        }
    }

    private MailSender createMailSender(LightLoginConfig config, SecretBox box) {
        String password = decrypt(box, config.mail().encryptedPassword());
        encryptInPlace(box, "email.password", config.mail().encryptedPassword());
        SmtpMailSender sender = new SmtpMailSender(config.mail(), password);
        if (config.mail().enabled() && !sender.isEnabled()) {
            plugin.getLogger().warning("Email recovery is enabled but SMTP is not fully configured.");
        }
        return sender;
    }

    private SecretLogFilter installLogFilter(SecretRedactor redactor) {
        SecretLogFilter filter = new SecretLogFilter();
        try {
            var rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.addFilter(filter);
            plugin.getLogger().info("Installed the credential-redacting log filter.");
            return filter;
        } catch (Throwable e) {
            // A different logging backend: the plugin's own paths still redact via SecretRedactor.
            plugin.getLogger().warning("Could not install the log filter (" + e.getMessage()
                    + "); credential commands may appear in the server log.");
            return null;
        }
    }

    private void registerCommands(PluginContext context) {
        CommandRegistry registry = new CommandRegistry(plugin);
        PasswordInput input = new PasswordInput(context);
        this.passwordInput = input;
        this.moderationGui = new ModerationGui(context);

        AuthCommands auth = new AuthCommands(context, input);
        AccountCommands account = new AccountCommands(context, input);
        AdminCommands admin = new AdminCommands(context, moderationGui, this::reload);

        registry.register("login", auth::login);
        registry.register("register", auth::register);
        registry.register("verify", auth::verify);
        registry.register("changepassword", account::changePassword);
        registry.register("unregister", account::unregister);
        registry.register("unlogin", account::unlogin);
        registry.register("email", account::email);
        registry.register("resetpassword", account::resetPassword);
        registry.register("temppassword", admin::tempPassword);
        registry.register("login-data", admin::loginData);
        registry.register("lightlogin", admin::admin);

        this.commandRegistry = registry;
    }

    private void registerListeners(PluginContext context) {
        var manager = Bukkit.getPluginManager();
        manager.registerEvents(new ConnectionListener(context), plugin);
        manager.registerEvents(new AuthRestrictionListener(context), plugin);
        manager.registerEvents(new ChatPasswordListener(context), plugin);
        manager.registerEvents(passwordInput, plugin);
        manager.registerEvents(moderationGui, plugin);
        // Refuses an ender dragon in the login world. No game rule covers the dragon, so this is the
        // only layer that can stop one the moment it is created.
        manager.registerEvents(new dev.lightlogin.paper.world.LoginWorldGuard(context.voidWorld()), plugin);
    }

    private void startAdminPanel(PluginContext context, SecurityContext security, AdminService adminService,
                                 RateLimiterRegistry panelLimiter, TokenGenerator tokens) {
        if (!context.config().web().enabled()) {
            context.banner().warn("The administration panel is disabled.");
            return;
        }
        try {
            AdminPanel panel = new AdminPanel(context.config().web(), adminService,
                    new AdminSessionStore(tokens, context.config().web().sessionTtlMillis()),
                    context.adminAccounts(), context.hasher(), panelLimiter, context.auditService(),
                    security, security.bootToken());
            panel.start();
            Optional<String> bootstrap = panel.ensureBootstrapAdmin();
            context.banner().ok("Administration panel listening on http://"
                    + context.config().web().bindAddress() + ':' + panel.port());
            bootstrap.ifPresent(password -> {
                context.banner().warn("A bootstrap administrator was created.");
                context.banner().warn("Username: admin    Password: " + password);
                context.banner().warn("Sign in and change this password immediately.");
            });
            // The panel is not part of the immutable context, so hold it on the bootstrap.
            this.adminPanel = panel;
        } catch (Exception e) {
            context.banner().error("The administration panel could not start: " + e.getMessage());
        }
    }

    private AdminPanel adminPanel;

    private void reportMissingCommandRegistrations() {
        if (commandRegistry == null) {
            return;
        }
        if (!commandRegistry.failures().isEmpty()) {
            commandRegistry.failures().forEach((name, reason) ->
                    plugin.getLogger().warning("Command '" + name + "' could not be registered: " + reason));
        }
    }
}