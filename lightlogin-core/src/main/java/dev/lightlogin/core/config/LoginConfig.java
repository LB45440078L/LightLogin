package dev.lightlogin.core.config;

import java.util.List;

/**
 * Player-facing login flow settings.
 *
 * @param timeoutMillis    how long a player may stay unauthenticated before being kicked
 * @param blindness       whether to apply a blindness effect while unauthenticated
 * @param titlesEnabled   whether to show a title prompt
 * @param actionBarEnabled whether to show the countdown in the action bar
 * @param reminderSeconds  interval between authentication reminders
 * @param allowedCommands  commands runnable while unauthenticated
 * @param commandDelayMillis delay before a login command is accepted (anti-autoclick)
 * @param teleportOnJoin  whether to teleport to a fixed login location
 * @param loginLocation   "world,x,y,z" or empty
 * @param returnLocation  "world,x,y,z", "latest", or empty
 * @param autoLoginAfterRegister whether a new registration logs the player in immediately
 */
public record LoginConfig(
        long timeoutMillis,
        boolean blindness,
        boolean titlesEnabled,
        boolean actionBarEnabled,
        int reminderSeconds,
        List<String> allowedCommands,
        long commandDelayMillis,
        boolean teleportOnJoin,
        String loginLocation,
        String returnLocation,
        boolean autoLoginAfterRegister) {

    public LoginConfig {
        if (timeoutMillis < 10_000) {
            timeoutMillis = 120_000;
        }
        if (reminderSeconds < 1) {
            reminderSeconds = 5;
        }
        allowedCommands = List.copyOf(allowedCommands == null ? List.of() : allowedCommands);
        if (commandDelayMillis < 0) {
            commandDelayMillis = 0;
        }
        loginLocation = loginLocation == null ? "" : loginLocation;
        returnLocation = returnLocation == null ? "" : returnLocation;
    }

    public static LoginConfig defaults() {
        return new LoginConfig(120_000, true, true, true, 5,
                List.of("login", "register", "verify", "email", "resetpassword"),
                1_000, false, "", "", true);
    }
}