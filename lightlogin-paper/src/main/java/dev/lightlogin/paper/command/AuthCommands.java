package dev.lightlogin.paper.command;

import dev.lightlogin.core.captcha.CaptchaChallenge;
import dev.lightlogin.core.captcha.CaptchaResult;
import dev.lightlogin.core.crypto.ConstantTime;
import dev.lightlogin.core.model.AuthResult;
import dev.lightlogin.paper.api.AuthMethod;
import dev.lightlogin.paper.api.PlayerAuthFailedEvent;
import dev.lightlogin.paper.api.PlayerAuthenticatedEvent;
import dev.lightlogin.paper.api.PlayerRegisteredEvent;
import dev.lightlogin.paper.auth.AuthGate;
import dev.lightlogin.paper.auth.LoginEffects;
import dev.lightlogin.paper.bootstrap.PluginContext;
import dev.lightlogin.paper.gui.PasswordInput;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** {@code /login}, {@code /register} and {@code /verify}. */
public final class AuthCommands extends CommandSupport {

    private final PasswordInput passwordInput;
    private final Map<UUID, Long> lastAttemptMillis = new ConcurrentHashMap<>();

    public AuthCommands(PluginContext ctx, PasswordInput passwordInput) {
        super(ctx);
        this.passwordInput = passwordInput;
    }

    // ------------------------------------------------------------------ /login

    public void login(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender).orElse(null);
        if (player == null) {
            return;
        }
        if (!ctx.commandLimiter().tryAcquire("command:" + player.getUniqueId())) {
            send(player, "rate-limited");
            return;
        }
        AuthGate.Pending pending = ctx.authGate().pending(player.getUniqueId()).orElse(null);
        if (pending == null) {
            send(player, "login.already-authenticated");
            return;
        }
        if (args.length == 0) {
            passwordInput.open(player, "&7Type your password, then click the result slot",
                    (target, value) -> attemptLogin(target, value.toCharArray()));
            return;
        }
        if (!commandDelayElapsed(player)) {
            send(player, "password.too-fast");
            return;
        }
        attemptLogin(player, args[0].toCharArray());
    }

    private boolean commandDelayElapsed(Player player) {
        long delay = ctx.config().login().commandDelayMillis();
        if (delay <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastAttemptMillis.put(player.getUniqueId(), now);
        return last == null || now - last >= delay;
    }

    private void attemptLogin(Player player, char[] password) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = ipOf(player);
        // asyncAuth claims the password on this thread before submitting, so the worker cannot
        // observe the caller's array after it has been wiped.
        asyncAuth(password, owned -> ctx.authService().login(uuid.toString(), name, owned, ip),
                result -> handleLoginResult(player, result));
    }

    private void handleLoginResult(Player player, AuthResult result) {
        switch (result) {
            case AuthResult.Success ignored -> {
                AuthGate.Pending pending = ctx.authGate().complete(player.getUniqueId()).orElse(null);
                if (pending != null) {
                    LoginEffects.restore(ctx, player, pending);
                }
                // Remember the session so a quick reconnect needs no password.
                ctx.authGate().remember(player.getUniqueId(), ipOf(player),
                        System.currentTimeMillis() + ctx.config().security().sessionTtlMillis());
                send(player, "login.success", of("PLAYER", player.getName()));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                // Announced last, so a listener sees a fully authenticated player: the gate is
                // released, the session is remembered and the login world has already been reversed.
                fire(new PlayerAuthenticatedEvent(player, ipOf(player), AuthMethod.PASSWORD));
            }
            case AuthResult.WrongPassword wrong -> {
                send(player, "login.wrong-password",
                        of("ATTEMPTS", String.valueOf(wrong.attemptsRemaining())));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 0.8f, 1f);
                fire(new PlayerAuthFailedEvent(player, ipOf(player),
                        PlayerAuthFailedEvent.Reason.WRONG_PASSWORD, wrong.attemptsRemaining()));
            }
            case AuthResult.NotRegistered ignored -> {
                send(player, "login.not-registered");
                fire(new PlayerAuthFailedEvent(player, ipOf(player),
                        PlayerAuthFailedEvent.Reason.NOT_REGISTERED, -1));
            }
            case AuthResult.Locked ignored -> {
                send(player, "login.locked");
                fire(new PlayerAuthFailedEvent(player, ipOf(player),
                        PlayerAuthFailedEvent.Reason.LOCKED, 0));
            }
            case AuthResult.RateLimited ignored -> {
                send(player, "rate-limited");
                fire(new PlayerAuthFailedEvent(player, ipOf(player),
                        PlayerAuthFailedEvent.Reason.RATE_LIMITED, -1));
            }
            case AuthResult.IpBanned banned -> player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    "&cYou are banned from this server. &7" + banned.reason()));
            case AuthResult.Error error -> {
                ctx.plugin().getLogger().warning("Login failed for " + player.getName() + ": " + error.message());
                send(player, "login.not-registered");
            }
            default -> send(player, "login.not-registered");
        }
    }

    // --------------------------------------------------------------- /register

    public void register(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender).orElse(null);
        if (player == null) {
            return;
        }
        if (!ctx.commandLimiter().tryAcquire("command:" + player.getUniqueId())) {
            send(player, "rate-limited");
            return;
        }
        AuthGate.Pending pending = ctx.authGate().pending(player.getUniqueId()).orElse(null);
        if (pending == null) {
            send(player, "register.already-registered");
            return;
        }
        if (pending.registered()) {
            send(player, "register.already-registered");
            return;
        }
        if (captchaRequired() && !pending.captchaDone()) {
            issueCaptcha(player);
            return;
        }
        if (args.length == 0) {
            // Two-step private input: enter, then confirm.
            passwordInput.open(player, "&7Choose a password", (target, first) ->
                    passwordInput.open(target, "&7Type it again to confirm", (confirmed, second) -> {
                        if (!first.equals(second)) {
                            send(confirmed, "register.mismatch");
                            return;
                        }
                        attemptRegister(confirmed, first.toCharArray());
                    }));
            return;
        }
        if (args.length == 1) {
            attemptRegister(player, args[0].toCharArray());
            return;
        }
        if (!args[0].equals(args[1])) {
            send(player, "register.mismatch");
            return;
        }
        attemptRegister(player, args[0].toCharArray());
    }

    private void attemptRegister(Player player, char[] password) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = ipOf(player);
        asyncAuth(password, owned -> ctx.authService().register(uuid.toString(), name, owned, ip),
                result -> handleRegisterResult(player, result));
    }

    private void handleRegisterResult(Player player, AuthResult result) {
        switch (result) {
            case AuthResult.Success ignored -> {
                send(player, "register.success", of("PLAYER", player.getName()));
                // Fired before authentication: the account row exists, but the player has not been
                // let through the gate yet, so this is the right place for first-join housekeeping.
                fire(new PlayerRegisteredEvent(player, ipOf(player)));
                if (ctx.config().login().autoLoginAfterRegister()) {
                    AuthGate.Pending pending = ctx.authGate().complete(player.getUniqueId()).orElse(null);
                    if (pending != null) {
                        LoginEffects.restore(ctx, player, pending);
                        send(player, "login.auto");
                        fire(new PlayerAuthenticatedEvent(player, ipOf(player), AuthMethod.REGISTRATION));
                    }
                } else {
                    ctx.authGate().update(player.getUniqueId(), p -> p.withRegistered(true));
                    send(player, "login.prompt");
                }
            }
            case AuthResult.AlreadyRegistered ignored -> send(player, "register.already-registered");
            case AuthResult.PolicyRejected rejected -> {
                send(player, "password.unsafe");
                for (String violation : rejected.violations()) {
                    send(player, "password.unsafe-line", of("REASON", violation));
                }
            }
            case AuthResult.RegistrationLimitReached ignored -> send(player, "register.limit-reached");
            case AuthResult.IpBanned banned -> player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    "&cYou are banned from this server. &7" + banned.reason()));
            case AuthResult.Error error -> {
                ctx.plugin().getLogger().warning("Registration failed for " + player.getName() + ": " + error.message());
                send(player, "register.mismatch");
            }
            default -> send(player, "register.mismatch");
        }
    }

    // ----------------------------------------------------------------- /verify

    public void verify(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender).orElse(null);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            send(player, "captcha.prompt", of("PROMPT", currentPrompt(player)));
            return;
        }
        CaptchaResult result = ctx.captcha().verify(player.getUniqueId().toString(), args[0],
                System.currentTimeMillis());
        switch (result) {
            case CaptchaResult.Verified ignored -> {
                ctx.authGate().update(player.getUniqueId(), p -> p.withCaptchaDone(true));
                send(player, "captcha.correct");
                send(player, "register.prompt");
            }
            case CaptchaResult.WrongAnswer wrong -> send(player, "captcha.wrong",
                    of("ATTEMPTS", String.valueOf(wrong.attemptsRemaining())));
            case CaptchaResult.TooManyAttempts ignored -> {
                send(player, "captcha.failed");
                if (ctx.config().captcha().punishOnFailure()) {
                    String ip = ipOf(player);
                    String name = player.getName();
                    // The ban and its audit row are database writes: keep them off the server thread.
                    asyncRun(() -> ctx.authService().banForBruteForce(ip, name));
                    player.kickPlayer(ChatColor.RED + "You failed the CAPTCHA too many times.");
                }
            }
            case CaptchaResult.Expired ignored -> issueCaptcha(player);
            case CaptchaResult.NoChallenge ignored -> issueCaptcha(player);
        }
    }

    private boolean captchaRequired() {
        return ctx.config().captcha().enabled() && ctx.config().captcha().requireForRegister();
    }

    private void issueCaptcha(Player player) {
        CaptchaChallenge challenge = ctx.captcha().issue(player.getUniqueId().toString(),
                System.currentTimeMillis());
        send(player, "captcha.prompt", of("PROMPT", challenge.prompt()));
    }

    private String currentPrompt(Player player) {
        CaptchaChallenge challenge = ctx.captcha().current(player.getUniqueId().toString());
        return challenge == null ? "" : challenge.prompt();
    }

    /** Tab completion for /login and /register deliberately offers nothing. */
    public List<String> noCompletion(CommandSender sender, String[] args) {
        return List.of();
    }
}