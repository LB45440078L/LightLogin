package dev.lightlogin.core.config;

import java.util.Locale;

/**
 * Blank login world settings.
 *
 * <p>The world is always generated <em>empty</em> — no terrain layers, no structures, no mob
 * spawning, no random ticking — so nothing an unauthenticated player can see or reach belongs to the
 * real map. Only the dimension differs between the two variants:</p>
 *
 * <ul>
 *   <li>{@link Dimension#END} generates the blank world in the End: the dark sky, lighting and
 *       ambience of that dimension, with no terrain. This is the default because it is the most
 *       obviously "not the server" backdrop for a login screen.</li>
 *   <li>{@link Dimension#NORMAL} generates the same blank world in the overworld: a normal sky and
 *       weather cycle over the same void.</li>
 * </ul>
 *
 * <p>Both are custom worlds of the configured name; neither is the server's real end or overworld,
 * and neither shares chunks with them.</p>
 *
 * @param enabled          whether players are moved to a separate world while authenticating
 * @param worldName        the world to create or reuse
 * @param dimension        the dimension the blank world is generated in
 * @param spawnY           the Y at which the login platform sits
 * @param returnToOriginal whether to teleport the player back to their join location after login
 */
public record VoidWorldConfig(
        boolean enabled,
        String worldName,
        Dimension dimension,
        double spawnY,
        boolean returnToOriginal) {

    /** The dimensions a blank login world may be generated in. Both variants are empty. */
    public enum Dimension {

        /** The End: dark sky and End ambience, no terrain. */
        END,

        /** The overworld: normal sky and weather, no terrain. */
        NORMAL;

        /**
         * Parses a configured value, falling back on anything unrecognised.
         *
         * <p>{@code OVERWORLD} and {@code THE_END} are accepted as synonyms so a value written in the
         * server's own vocabulary is not silently ignored.</p>
         */
        public static Dimension fromString(String value, Dimension fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "END", "THE_END" -> END;
                case "NORMAL", "OVERWORLD" -> NORMAL;
                default -> fallback;
            };
        }
    }

    public VoidWorldConfig {
        worldName = worldName == null || worldName.isBlank() ? "lightlogin_void" : worldName;
        dimension = dimension == null ? Dimension.END : dimension;
    }

    public static VoidWorldConfig defaults() {
        return new VoidWorldConfig(false, "lightlogin_void", Dimension.END, 100.0, true);
    }
}