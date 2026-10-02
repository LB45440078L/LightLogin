package dev.lightlogin.core.config;

/**
 * Blank login world settings.
 *
 * @param enabled      whether players are moved to a void world while authenticating
 * @param worldName    the world to create/use
 * @param endStyleVoid whether to use the End's void aesthetics rather than a plain blank world
 * @param spawnY       the Y at which the login platform sits
 * @param returnToOriginal whether to teleport the player back to their join location after login
 */
public record VoidWorldConfig(
        boolean enabled,
        String worldName,
        boolean endStyleVoid,
        double spawnY,
        boolean returnToOriginal) {

    public VoidWorldConfig {
        worldName = worldName == null || worldName.isBlank() ? "lightlogin_void" : worldName;
    }

    public static VoidWorldConfig defaults() {
        return new VoidWorldConfig(false, "lightlogin_void", false, 100.0, true);
    }
}