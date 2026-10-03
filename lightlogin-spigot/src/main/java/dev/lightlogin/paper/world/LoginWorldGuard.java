package dev.lightlogin.paper.world;

import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Keeps the ender dragon out of the login world.
 *
 * <p>A world created in the End dimension is the End as far as the server is concerned, so the dragon
 * fight is a live possibility there. A dragon in a world whose entire purpose is to hold one player
 * still for thirty seconds is a hazard, and the plugin's own game rules cannot switch it off: there is
 * no game rule for the dragon.</p>
 *
 * <p>Two layers, because they fail differently. The listener rejects a dragon at the spawn event,
 * which is precise and cheap. {@link #sweep()} removes any that arrived some other way — the dragon
 * fight does not necessarily raise a spawn event for the entity it adds — and is called from the
 * maintenance task. {@link EntitySpawnEvent} is handled rather than {@link
 * org.bukkit.event.entity.CreatureSpawnEvent} because the former receives the latter.</p>
 */
public final class LoginWorldGuard implements Listener {

    private final VoidWorldService loginWorld;

    public LoginWorldGuard(VoidWorldService loginWorld) {
        this.loginWorld = loginWorld;
    }

    /** Refuses an ender dragon the moment one is created in the login world. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof EnderDragon
                && loginWorld.isLoginWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Removes any dragon that reached the login world by another route. */
    public void sweep() {
        loginWorld.removeDragons();
    }
}