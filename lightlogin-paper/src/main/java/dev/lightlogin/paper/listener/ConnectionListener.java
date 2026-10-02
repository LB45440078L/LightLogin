package dev.lightlogin.paper.listener;

import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.security.AuditAction;
import dev.lightlogin.core.service.IpBanService;
import dev.lightlogin.paper.api.AuthMethod;
import dev.lightlogin.paper.api.PlayerAuthenticatedEvent;
import dev.lightlogin.paper.auth.AuthGate;
import dev.lightlogin.paper.auth.LoginEffects;
import dev.lightlogin.paper.bootstrap.PluginContext;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gatekeeping at the connection boundary: flood limiting, IP/country bans, session resumption and
 * the pending-login state.
 *
 * <p>The pre-login phase runs off the main thread and is where every check that can refuse a
 * connection belongs, so a flood is rejected before it ever creates a player entity. The join
 * phase runs on the main thread and only applies effects.</p>
 */
public final class ConnectionListener implements Listener {

    private final PluginContext ctx;

    public ConnectionListener(PluginContext ctx) {
        this.ctx = ctx;
    }

    /** Runs before a player is admitted to the server. */
    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress() == null ? "" : event.getAddress().getHostAddress();

        // 1. Per-IP connection rate limiting: the first line of defence against a join flood.
        if (!ctx.connectionLimiter().tryAcquire(ip)) {
            ctx.auditService().record("system", AuditAction.CONNECTION_THROTTLED, event.getName(), "connection rate", ip);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    LegacyComponentSerializer.legacyAmpersand().deserialize(
                            ctx.messages().raw("connection.throttled")));
            return;
        }

        // 2. Explicit bans and country blocking.
        IpBanService.Verdict verdict = ctx.ipBanService().check(ip);
        if (verdict instanceof IpBanService.Verdict.Banned banned) {
            ctx.auditService().record("system", AuditAction.LOGIN_BLOCKED, event.getName(), "ip banned", ip);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    LegacyComponentSerializer.legacyAmpersand().deserialize(
                            ctx.messages().raw("connection.ip-banned").replace("{REASON}", banned.reason())));
            return;
        }
        if (verdict instanceof IpBanService.Verdict.CountryBlocked blocked) {
            ctx.auditService().record("system", AuditAction.COUNTRY_BLOCKED, event.getName(),
                    "country " + blocked.country(), ip);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    LegacyComponentSerializer.legacyAmpersand().deserialize(
                            ctx.messages().raw("connection.country-blocked")));
            return;
        }

        // 3. Per-IP concurrent player cap (counted from stored state, so it is authoritative).
        int maxPerIp = ctx.config().safety().maxPlayersPerIp();
        if (maxPerIp > 0) {
            long online = org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .filter(p -> ip.equals(addressOf(p)))
                    .count();
            if (online >= maxPerIp) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        LegacyComponentSerializer.legacyAmpersand().deserialize(
                                ctx.messages().raw("connection.too-many-accounts")));
                return;
            }
        }

        // 4. Session resumption: a recent successful login from the same address skips the password.
        java.util.UUID playerId = event.getUniqueId();
        boolean registered;
        try {
            Account account = ctx.accounts().findByUuid(playerId.toString()).orElse(null);
            registered = account != null && account.isRegistered();
        } catch (RuntimeException e) {
            // Storage trouble must not lock everyone out; fall back to requiring a password.
            ctx.plugin().getLogger().warning("Could not check login state for " + event.getName()
                    + ": " + e.getMessage());
            registered = false;
        }
        ctx.authGate().rememberOutcome(playerId, registered, ctx.authGate().canAutoLogin(playerId, ip));
    }

    /** Runs on the main thread once the player entity exists. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        java.util.UUID playerId = player.getUniqueId();
        String uuid = playerId.toString();
        String ip = addressOf(player);

        AuthGate.Outcome outcome = ctx.authGate().consumeOutcome(playerId);
        boolean registered = outcome != null && outcome.registered();
        boolean autoLogin = outcome != null && outcome.autoLogin();

        if (!registered) {
            // New player: they must register (and pass the CAPTCHA if it is enabled).
            ctx.authGate().begin(player, ip, false);
            LoginEffects.apply(ctx, player, ctx.authGate().pending(playerId).orElseThrow());
            ctx.messages().send(player, "register.prompt");
            return;
        }
        if (autoLogin) {
            // A recent session means no password prompt at all. The audit row is a database write,
            // so it must not run on the server thread.
            ctx.async().run(() -> ctx.auditService().record(player.getName(), AuditAction.LOGIN_SUCCESS,
                    uuid, "session resumed", ip));
            fireEvent(new PlayerAuthenticatedEvent(player, ip, AuthMethod.SESSION));
            return;
        }
        AuthGate.Pending pending = ctx.authGate().begin(player, ip, true);
        LoginEffects.apply(ctx, player, pending);
        ctx.messages().send(player, "login.prompt");
    }

    /** Cleans up state when a player leaves. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        ctx.authGate().forget(uuid);
        ctx.captcha().clear(uuid.toString());
    }

    private static String addressOf(org.bukkit.entity.Player player) {
        var address = player.getAddress();
        return address == null || address.getAddress() == null
                ? "" : address.getAddress().getHostAddress();
    }

    /**
     * Fires an API event on the server thread.
     *
     * <p>Wrapped because a throwing listener must not be able to break a player's join.</p>
     */
    private void fireEvent(org.bukkit.event.Event event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException e) {
            ctx.plugin().getLogger().warning("An API listener threw for "
                    + event.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}