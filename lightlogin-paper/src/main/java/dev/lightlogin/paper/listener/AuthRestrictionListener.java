package dev.lightlogin.paper.listener;

import dev.lightlogin.paper.bootstrap.PluginContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * Prevents an unauthenticated player from interacting with the world.
 *
 * <p>Freezing movement, blocking block and entity interaction, cancelling damage and hiding every
 * command but the authentication ones are all facets of one rule: a pending player can do nothing
 * except authenticate. Hiding commands from the client's tab completion also means the login
 * commands are not advertised to a player who has not authenticated.</p>
 *
 * <p>Every handler checks the gate first and returns immediately when the player is authenticated,
 * so an authenticated server pays only a map lookup per event.</p>
 */
public final class AuthRestrictionListener implements Listener {

    private final PluginContext ctx;

    public AuthRestrictionListener(PluginContext ctx) {
        this.ctx = ctx;
    }

    private boolean pending(Player player) {
        return ctx.authGate().isPending(player.getUniqueId());
    }

    /** Freezes movement while pending, without fighting the login teleport. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!pending(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        // Allow head rotation but not translation.
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks any command that is not on the allowed list while pending.
     *
     * <p>The allowed list is compared against the command root (before the first space and with any
     * namespace removed), so {@code /minecraft:login} is treated as {@code /login}.</p>
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!pending(player)) {
            return;
        }
        String message = event.getMessage();
        if (message.isEmpty()) {
            return;
        }
        String withoutSlash = message.startsWith("/") ? message.substring(1) : message;
        int space = withoutSlash.indexOf(' ');
        String root = (space < 0 ? withoutSlash : withoutSlash.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) {
            root = root.substring(colon + 1);
        }
        for (String allowed : ctx.config().login().allowedCommands()) {
            if (allowed.equalsIgnoreCase(root)) {
                return;
            }
        }
        event.setCancelled(true);
        ctx.messages().send(player, "login.prompt");
    }

    /**
     * Hides every command but the authentication ones from the client while pending.
     *
     * <p>This is the "commands are not readable or accessible" requirement: the client is never
     * told the server's command list, and the authentication commands themselves are offered
     * without any argument hints.</p>
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!pending(event.getPlayer())) {
            return;
        }
        java.util.List<String> allowed = ctx.config().login().allowedCommands();
        event.getCommands().removeIf(command -> {
            String name = command;
            int colon = name.indexOf(':');
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }
            for (String permitted : allowed) {
                if (permitted.equalsIgnoreCase(name)) {
                    return false;
                }
            }
            return true;
        });
    }
}