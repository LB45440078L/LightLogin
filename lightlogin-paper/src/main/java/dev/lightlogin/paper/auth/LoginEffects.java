package dev.lightlogin.paper.auth;

import dev.lightlogin.core.config.LoginConfig;
import dev.lightlogin.paper.bootstrap.PluginContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies and reverses the effects that gate an unauthenticated player.
 *
 * <p>Kept in one place so that "what a pending player looks like" has exactly one definition, and
 * so that every path out of the pending state restores the same things — the original plugin
 * cleared blindness on login but left the player in the login world's game mode.</p>
 */
public final class LoginEffects {

    private LoginEffects() {
    }

    /** Applies the pre-login restrictions: blindness and the login-area teleport. */
    public static void apply(PluginContext ctx, Player player, AuthGate.Pending pending) {
        LoginConfig login = ctx.config().login();
        if (login.blindness()) {
            // Permanent until removed; ambient so it does not look like a normal potion.
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        if (ctx.voidWorld().isAvailable()) {
            ctx.voidWorld().teleportIn(player);
        } else if (login.teleportOnJoin() && !login.loginLocation().isBlank()) {
            Location target = parseLocation(login.loginLocation());
            if (target != null) {
                player.teleport(target);
            }
        }
    }

    /** Reverses the restrictions after a successful login. */
    public static void restore(PluginContext ctx, Player player, AuthGate.Pending pending) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (pending.gameMode() != null) {
            player.setGameMode(pending.gameMode());
        }
        player.setAllowFlight(pending.allowFlight());
        player.setFlying(pending.flying());

        if (ctx.voidWorld().isAvailable() && ctx.config().voidWorld().returnToOriginal()) {
            Location back = pending.returnLocation();
            if (back != null && back.getWorld() != null) {
                player.teleport(back);
            }
        } else if (ctx.config().login().teleportOnJoin() && !ctx.config().login().returnLocation().isBlank()) {
            Location target = parseLocation(ctx.config().login().returnLocation());
            if (target != null) {
                player.teleport(target);
            }
        }
        player.resetTitle();
    }

    /** Parses a {@code world,x,y,z} location string, returning null when malformed. */
    static Location parseLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }
        var world = org.bukkit.Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            return new Location(world,
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}