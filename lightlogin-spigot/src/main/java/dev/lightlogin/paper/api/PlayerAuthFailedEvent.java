package dev.lightlogin.paper.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired on the main thread after a failed authentication attempt.
 *
 * <p>Informational: useful for alerting staff, feeding an external anti-cheat, or counting attempts
 * per player. The reason is a stable, short code rather than a localised sentence, so a listener can
 * branch on it without parsing prose.</p>
 *
 * <p>Not cancellable. The attempt has already been counted against the player's rate limit and
 * failure budget by the time this fires, and cancelling would let a listener silently disable
 * lockout.</p>
 */
public class PlayerAuthFailedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /** Why the attempt failed. */
    public enum Reason {

        /** The password did not match. */
        WRONG_PASSWORD,

        /** No account exists for this player. */
        NOT_REGISTERED,

        /** The account is temporarily locked after too many failures. */
        LOCKED,

        /** The attempt was refused by rate limiting. */
        RATE_LIMITED
    }

    private final Player player;
    private final String ip;
    private final Reason reason;
    private final int attemptsRemaining;

    public PlayerAuthFailedEvent(@NotNull Player player, @NotNull String ip, @NotNull Reason reason,
                                 int attemptsRemaining) {
        super(false);
        this.player = Objects.requireNonNull(player, "player");
        this.ip = ip == null ? "" : ip;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.attemptsRemaining = attemptsRemaining;
    }

    /** The player whose attempt failed, who is still online and unauthenticated. */
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

    /** The address the attempt came from. */
    public @NotNull String getIp() {
        return ip;
    }

    /** A stable code describing the failure. */
    public @NotNull Reason getReason() {
        return reason;
    }

    /** Attempts left before lockout, or {@code -1} when that is not meaningful for the reason. */
    public int getAttemptsRemaining() {
        return attemptsRemaining;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}