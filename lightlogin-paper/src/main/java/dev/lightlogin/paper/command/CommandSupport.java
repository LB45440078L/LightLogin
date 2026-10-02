package dev.lightlogin.paper.command;

import dev.lightlogin.paper.bootstrap.PluginContext;
import net.kyori.adventure.audience.Audience;
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
    protected void send(Audience audience, String key, Map<String, String> placeholders) {
        ctx.messages().send(audience, key, placeholders);
    }

    protected void send(Audience audience, String key) {
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