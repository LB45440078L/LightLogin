package dev.lightlogin.paper.listener;

import dev.lightlogin.core.security.AuditAction;
import dev.lightlogin.paper.bootstrap.PluginContext;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Stops a password from reaching chat.
 *
 * <p>A player who has not authenticated yet has no legitimate reason to chat, and every chat line
 * from a pending player is a password typed into the wrong box — the single most common way a
 * password leaks. The message is cancelled before it is broadcast, so it never reaches other
 * players, the console or the log file, and the player is told to use the login command instead.</p>
 */
public final class ChatPasswordListener implements Listener {

    private final PluginContext ctx;

    public ChatPasswordListener(PluginContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!ctx.authGate().isPending(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String content = PlainTextComponentSerializer.plainText().serialize(event.message());
        // Record only that a message was blocked and its length: never its content.
        ctx.auditService().record(player.getName(), AuditAction.LOGIN_BLOCKED, player.getUniqueId().toString(),
                "password-shaped chat blocked (length " + content.length() + ')',
                addressOf(player));
        // The message is sent back on the main thread because the chat event is asynchronous.
        org.bukkit.Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
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