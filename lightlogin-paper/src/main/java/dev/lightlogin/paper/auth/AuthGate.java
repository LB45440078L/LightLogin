package dev.lightlogin.paper.auth;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are still waiting to authenticate, and the state needed to restore them.
 *
 * <p>A player is "pending" from the moment they join until they log in or leave; presence in the
 * map <em>is</em> the pending state, so there is no second set that can drift out of sync with the
 * first. The captured location, game mode and flight state are restored on success, which is what
 * makes the void-world feature non-destructive.</p>
 */
public final class AuthGate {

    /**
     * A player awaiting authentication.
     *
     * @param uuid          the player
     * @param name          the player's name
     * @param ip            the connecting address
     * @param joinedAt      when they joined (epoch millis)
     * @param registered    whether they have an account
     * @param captchaDone   whether a CAPTCHA has been solved this session
     * @param returnLocation where to return after login (may be null)
     * @param gameMode      game mode to restore
     * @param allowFlight   flight permission to restore
     * @param flying        flight state to restore
     */
    public record Pending(UUID uuid, String name, String ip, long joinedAt, boolean registered,
                          boolean captchaDone, Location returnLocation, GameMode gameMode,
                          boolean allowFlight, boolean flying) {

        public Pending withRegistered(boolean value) {
            return new Pending(uuid, name, ip, joinedAt, value, captchaDone, returnLocation, gameMode,
                    allowFlight, flying);
        }

        public Pending withCaptchaDone(boolean value) {
            return new Pending(uuid, name, ip, joinedAt, registered, value, returnLocation, gameMode,
                    allowFlight, flying);
        }
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, AutoLogin> autoLogins = new ConcurrentHashMap<>();

    /**
     * A recent successful login, letting a quick reconnect skip the password prompt.
     *
     * <p>Minecraft clients cannot hold a cookie, so the "session" is remembered server-side and
     * bound to the address it was created from. It is deliberately short-lived and is invalidated
     * by an explicit unlogin, a password change or a reset.</p>
     *
     * @param ip          the address the session was created from
     * @param untilMillis expiry
     */
    public record AutoLogin(String ip, long untilMillis) {
    }

    /** Remembers a successful login for session resumption. */
    public void remember(UUID uuid, String ip, long untilMillis) {
        autoLogins.put(uuid, new AutoLogin(ip, untilMillis));
    }

    /** Whether a reconnect from the same address may skip authentication. */
    public boolean canAutoLogin(UUID uuid, String ip) {
        AutoLogin auto = autoLogins.get(uuid);
        if (auto == null) {
            return false;
        }
        if (System.currentTimeMillis() >= auto.untilMillis()) {
            autoLogins.remove(uuid);
            return false;
        }
        return auto.ip().equals(ip);
    }

    /** Invalidates any remembered session for a player. */
    public void forgetAutoLogin(UUID uuid) {
        autoLogins.remove(uuid);
    }

    /**
     * When the remembered session for a player expires, or {@code 0} when there is none.
     *
     * <p>Read-only, and safe to call from the server thread; expired entries are treated as absent
     * without mutating the map, so this never races with {@link #remember}.</p>
     */
    public long autoLoginExpiry(UUID uuid) {
        AutoLogin auto = autoLogins.get(uuid);
        if (auto == null || System.currentTimeMillis() >= auto.untilMillis()) {
            return 0L;
        }
        return auto.untilMillis();
    }

    /**
     * The registered/auto-login decision carried from the asynchronous pre-login phase to the
     * main-thread join phase.
     *
     * @param registered whether the player has an account
     * @param autoLogin  whether a recent session allows skipping the password
     */
    public record Outcome(boolean registered, boolean autoLogin) {
    }

    private final Map<UUID, Outcome> outcomes = new ConcurrentHashMap<>();

    /** Records the pre-login decision. */
    public void rememberOutcome(UUID uuid, boolean registered, boolean autoLogin) {
        outcomes.put(uuid, new Outcome(registered, autoLogin));
    }

    /** Consumes the pre-login decision. */
    public Outcome consumeOutcome(UUID uuid) {
        return outcomes.remove(uuid);
    }

    /** Marks a player as awaiting authentication, capturing their pre-login state. */
    public Pending begin(Player player, String ip, boolean registered) {
        Pending state = new Pending(player.getUniqueId(), player.getName(), ip,
                System.currentTimeMillis(), registered, false,
                player.getLocation().clone(), player.getGameMode(),
                player.getAllowFlight(), player.isFlying());
        pending.put(player.getUniqueId(), state);
        return state;
    }

    /** Whether the player is waiting to authenticate. */
    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /** Whether the player has authenticated (or was never gated). */
    public boolean isAuthenticated(UUID uuid) {
        return !pending.containsKey(uuid);
    }

    /** The pending state for a player, if any. */
    public Optional<Pending> pending(UUID uuid) {
        return Optional.ofNullable(pending.get(uuid));
    }

    /** Updates the pending state for a player. */
    public void update(UUID uuid, java.util.function.UnaryOperator<Pending> operator) {
        pending.computeIfPresent(uuid, (key, value) -> operator.apply(value));
    }

    /** Removes the player from the pending set, returning the state that was held. */
    public Optional<Pending> complete(UUID uuid) {
        return Optional.ofNullable(pending.remove(uuid));
    }

    /** Removes the player without any further action (used on quit). */
    public void forget(UUID uuid) {
        pending.remove(uuid);
    }

    /** The number of players currently awaiting authentication. */
    public int pendingCount() {
        return pending.size();
    }

    /** A snapshot for the reminder task. */
    public java.util.Collection<Pending> all() {
        return java.util.List.copyOf(pending.values());
    }
}