package dev.lightlogin.paper.listener;

import dev.lightlogin.core.security.AuditAction;
import dev.lightlogin.paper.bootstrap.PluginContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Stops a password from reaching chat.
 *
 * <p>A player who has not authenticated yet has no legitimate reason to chat, and every chat line
 * from a pending player is a password typed into the wrong box — the single most common way a
 * password leaks. The message is cancelled before it is broadcast, so it never reaches other
 * players, the console or the log file, and the player is told to use the login command instead.</p>
 *
 * <p>Uses {@link AsyncPlayerChatEvent}, the asynchronous chat event the Bukkit API defines. Paper's
 * Adventure-based replacement is deliberately not used, because that type does not exist on Spigot
 * and this plugin builds against the common Bukkit surface only.</p>
 */
public final class ChatPasswordListener implements Listener {

    private final PluginContext ctx;

    public ChatPasswordListener(PluginContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!ctx.authGate().isPending(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String content = event.getMessage();
        int length = content == null ? 0 : content.length();
        // Record only that a message was blocked and its length: never its content. The audit row
        // is a database write and the chat event thread must not be made to wait on it.
        ctx.async().run(() -> ctx.auditService().record(player.getName(), AuditAction.LOGIN_BLOCKED,
                player.getUniqueId().toString(),
                "password-shaped chat blocked (length " + length + ')',
                addressOf(player)));
        // The message is sent back on the main thread because the chat event is asynchronous.
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (player.isOnline()) {
                ctx.messages().send(player, "chat.password-blocked");
            }
        });
    }

    private static String addressOf(Player player) {
        var address = player.getAddress();
        return address == null || address.getAddress() == null
                ? "" : address.getAddress().getHostAddress();
    }
}