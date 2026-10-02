package dev.lightlogin.paper.api;

import dev.lightlogin.core.model.Account;
import dev.lightlogin.paper.bootstrap.PluginContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Read-only view of LightLogin's authentication state, for other plugins.
 *
 * <p>Obtain it with {@link #get()}, or preferably by declaring a soft dependency and using
 * {@link #require()} so a missing LightLogin fails loudly at your plugin's startup instead of with a
 * {@code NullPointerException} on someone's first login. It is also published through Bukkit's
 * {@code ServicesManager}, which is the tidiest way for another plugin to consume it:</p>
 *
 * <pre>{@code
 * RegisteredServiceProvider<LightLoginApi> provider =
 *         Bukkit.getServicesManager().getRegistration(LightLoginApi.class);
 * if (provider != null) {
 *     boolean authed = provider.getProvider().isAuthenticated(player);
 * }
 * }</pre>
 *
 * <p>Everything here is either the plugin's own in-memory state, which is safe to read on the
 * server thread, or explicitly documented as blocking. That distinction matters: the server's main
 * thread must never wait on a database.</p>
 *
 * <p>The events in this package are the better hook for "react when someone logs in". This facade is
 * for asking questions at a moment of your choosing.</p>
 *
 * @see PlayerAuthenticatedEvent
 * @see PlayerRegisteredEvent
 * @see PlayerAuthFailedEvent
 */
public final class LightLoginApi {

    private static volatile LightLoginApi instance;

    private final PluginContext ctx;

    public LightLoginApi(@NotNull PluginContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    /**
     * The running instance, or {@code null} when LightLogin is not enabled.
     *
     * <p>Null rather than an exception because the usual caller is code running before or after
     * LightLogin's own lifecycle, where "not loaded" is a legitimate answer.</p>
     */
    public static @Nullable LightLoginApi get() {
        return instance;
    }

    /**
     * The running instance, or an exception naming the problem.
     *
     * @throws IllegalStateException when LightLogin is not enabled
     */
    public static @NotNull LightLoginApi require() {
        LightLoginApi api = instance;
        if (api == null) {
            throw new IllegalStateException(
                    "LightLogin is not enabled. Declare it as a depend/softdepend in your plugin.yml "
                            + "and read the API only after it has started.");
        }
        return api;
    }

    /**
     * Publishes the instance. Called by LightLogin during its own enable; other plugins do not need
     * this and should not call it.
     */
    public static void install(@NotNull LightLoginApi api) {
        instance = Objects.requireNonNull(api, "api");
    }

    /** Clears the instance. Called by LightLogin during its own disable. */
    public static void uninstall() {
        instance = null;
    }

    /** Whether the player has passed authentication. Safe on the main thread. */
    public boolean isAuthenticated(@NotNull Player player) {
        return isAuthenticated(player.getUniqueId());
    }

    /** Whether the player has passed authentication. Safe on the main thread. */
    public boolean isAuthenticated(@NotNull UUID uuid) {
        return ctx.authGate().isAuthenticated(uuid);
    }

    /**
     * Whether the player is still waiting to authenticate.
     *
     * <p>The inverse of {@link #isAuthenticated(UUID)} for any player the plugin has seen, including
     * one who is not connected. Safe on the main thread.</p>
     */
    public boolean isPending(@NotNull UUID uuid) {
        return ctx.authGate().isPending(uuid);
    }

    /** How many players are currently waiting to authenticate. Safe on the main thread. */
    public int pendingCount() {
        return ctx.authGate().pendingCount();
    }

    /**
     * When a remembered session for this player expires, or {@code 0} when there is none.
     *
     * <p>The session lets a quick reconnect skip the password prompt. Safe on the main thread.</p>
     */
    public long sessionExpiryMillis(@NotNull UUID uuid) {
        return ctx.authGate().autoLoginExpiry(uuid);
    }

    /** Whether the player has a live remembered session. Safe on the main thread. */
    public boolean hasSession(@NotNull UUID uuid) {
        return sessionExpiryMillis(uuid) > System.currentTimeMillis();
    }

    /**
     * Whether an account exists for this player.
     *
     * <p><b>Blocks on storage.</b> Call it from an asynchronous thread, never from the server's main
     * thread — the same rule the plugin holds itself to. It exists because "does this player have an
     * account" is a common question with no in-memory answer.</p>
     */
    public boolean isRegistered(@NotNull UUID uuid) {
        return ctx.accounts().findByUuid(uuid.toString()).filter(Account::isRegistered).isPresent();
    }
}