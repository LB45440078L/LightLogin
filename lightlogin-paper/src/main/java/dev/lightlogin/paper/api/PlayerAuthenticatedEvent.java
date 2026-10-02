package dev.lightlogin.paper.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired on the main thread after a player has successfully authenticated.
 *
 * <p>This is the hook for "let me do something once the player is actually in": give them a kit,
 * apply a group, teleport them, log the login somewhere else. The player is online, the
 * authentication gate has been released, and any effects LightLogin applies to the login flow have
 * already run.</p>
 *
 * <p><b>Not cancellable, deliberately.</b> By the time this fires the player is authenticated — the
 * session is remembered, the database has recorded the login, and the void-world teleport has been
 * reversed. A cancellable event would let one listener leave the server in a state where the player
 * is authenticated but the plugin thinks they are not, so a plugin that needs to influence the
 * outcome should listen to {@link PlayerAuthFailedEvent} or act before authentication instead.</p>
 *
 * @see PlayerRegisteredEvent
 * @see PlayerAuthFailedEvent
 */
public class PlayerAuthenticatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String ip;
    private final AuthMethod method;

    public PlayerAuthenticatedEvent(@NotNull Player player, @NotNull String ip, @NotNull AuthMethod method) {
        super(false);
        this.player = Objects.requireNonNull(player, "player");
        this.ip = ip == null ? "" : ip;
        this.method = Objects.requireNonNull(method, "method");
    }

    /** The authenticated player, who is online. */
    public @NotNull Player getPlayer() {
        return player;
    }

    /** The player's UUID. */
    public @NotNull UUID getUniqueId() {
        return player.getUniqueId();
    }

    /** The player's name. */
    public @NotNull String getName() {
        return player.getName();
    }

    /** The address the login came from. */
    public @NotNull String getIp() {
        return ip;
    }

    /** Whether the player typed a password, registered, or resumed a session. */
    public @NotNull AuthMethod getMethod() {
        return method;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}