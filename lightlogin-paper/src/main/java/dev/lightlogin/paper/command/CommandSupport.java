package dev.lightlogin.paper.command;

import dev.lightlogin.paper.bootstrap.PluginContext;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Shared behaviour for command handlers: player resolution, messaging and the async hop.
 *
 * <p>The async helper is the one place the "never block the main thread, never touch the world off
 * it" rule is expressed: the supplier runs on a virtual thread, and its result is delivered back on
 * the main thread where it is safe to teleport, message and mutate inventory.</p>
 */
public abstract class CommandSupport {

    protected final PluginContext ctx;

    protected CommandSupport(PluginContext ctx) {
        this.ctx = ctx;
    }

    /** The sender as a player, or empty (having sent the player-only message). */
    protected Optional<Player> requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        ctx.messages().send(sender, "player-only");
        return Optional.empty();
    }

    /** Sends a message. */
    protected void send(CommandSender audience, String key, Map<String, String> placeholders) {
        ctx.messages().send(audience, key, placeholders);
    }

    protected void send(CommandSender audience, String key) {
        ctx.messages().send(audience, key);
    }

    /** Whether the sender holds a permission (console always does). */
    protected boolean hasPermission(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.hasPermission(permission);
    }

    /** Whether the sender holds any of the permissions (console always does). */
    protected boolean hasAnyPermission(CommandSender sender, String... permissions) {
        if (!(sender instanceof Player)) {
            return true;
        }
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank() && sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs blocking work off the main thread and delivers the result on the main thread.
     *
     * @param work        the blocking supplier
     * @param onMainThread consumer of the result, invoked on the server thread
     */
    protected <T> void async(Callable<T> work, Consumer<T> onMainThread) {
        ctx.async().submit(work).whenComplete((result, error) -> {
            if (error != null) {
                ctx.plugin().getLogger().warning("Async command task failed: " + error.getMessage());
                return;
            }
            Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                try {
                    onMainThread.accept(result);
                } catch (RuntimeException e) {
                    ctx.plugin().getLogger().warning("Command result handling failed: " + e.getMessage());
                }
            });
        });
    }

    /** Runs a side-effecting blocking task off the main thread. */
    protected void asyncRun(Runnable work) {
        ctx.async().run(work);
    }

    /**
     * Fires an API event.
     *
     * <p>Callers are already on the server thread (every result handler is delivered there), which is
     * the thread Bukkit requires for a synchronous event. A listener that throws is caught and
     * logged: one misbehaving plugin must not be able to fail a player's login.</p>
     */
    protected void fire(org.bukkit.event.Event event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException e) {
            ctx.plugin().getLogger().warning("An API listener threw for "
                    + event.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Runs password-using work off the main thread with the password's ownership handed over
     * safely.
     *
     * <p>The password is claimed <em>here</em>, on the calling (main) thread, which copies it and
     * wipes the caller's array before anything is submitted. Copying inside the submitted task
     * would be too late: the caller's array is already cleared by then, and the task would hash an
     * empty password — silently, and only some of the time.</p>
     *
     * @param password    the caller's array, consumed by this call
     * @param work        runs on a virtual thread with the owned password
     * @param onMainThread receives the result on the server thread
     */
    protected <T> void asyncAuth(char[] password, java.util.function.Function<char[], T> work,
                                 Consumer<T> onMainThread) {
        dev.lightlogin.core.crypto.OwnedPassword owned =
                dev.lightlogin.core.crypto.OwnedPassword.claim(password);
        async(() -> owned.use(work), onMainThread);
    }

    /** The player's connecting address as a string. */
    protected static String ipOf(Player player) {
        var address = player.getAddress();
        return address == null || address.getAddress() == null
                ? "" : address.getAddress().getHostAddress();
    }

    /** A single-element placeholder map. */
    protected static Map<String, String> of(String key, String value) {
        return Map.of(key, value);
    }

    protected static Map<String, String> of(String k1, String v1, String k2, String v2) {
        return Map.of(k1, v1, k2, v2);
    }
}