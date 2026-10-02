package dev.lightlogin.paper.api;

/**
 * How a player came to be authenticated.
 *
 * <p>Exposed on {@link PlayerAuthenticatedEvent} so a listener can distinguish a login the player
 * performed from one the plugin performed on their behalf.</p>
 */
public enum AuthMethod {

    /** The player typed their password. */
    PASSWORD,

    /** The player registered, and registration logs them straight in. */
    REGISTRATION,

    /** A recent session from the same address skipped the password. */
    SESSION
}