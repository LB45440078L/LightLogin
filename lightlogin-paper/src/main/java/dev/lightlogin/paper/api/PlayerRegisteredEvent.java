package dev.lightlogin.paper.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired on the main thread after a player has successfully registered an account.
 *
 * <p>Fires before the player is authenticated, so it is the place for "a brand new account exists"
 * housekeeping: first-join rewards, adding them to a group, notifying staff. The account row is
 * already written and stored.</p>
 *
 * <p>Not cancellable: the password has been hashed and the account persisted, so there is no
 * meaningful state to roll back to. Listen to this to react, not to veto.</p>
 *
 * @see PlayerAuthenticatedEvent
 */
public class PlayerRegisteredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String ip;

    public PlayerRegisteredEvent(@NotNull Player player, @NotNull String ip) {
        super(false);
        this.player = Objects.requireNonNull(player, "player");
        this.ip = ip == null ? "" : ip;
    }

    /** The player who registered, who is online. */
    public @NotNull Player getPlayer() {
        return player;
    }

    /** The player's UUID. */
    public @NotNull UUID getUniqueId() {
        return player.getUniqueId();
    }

    /** The player's name, as it was stored. */
    public @NotNull String getName() {
        return player.getName();
    }

    /** The address the registration came from. */
    public @NotNull String getIp() {
        return ip;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}