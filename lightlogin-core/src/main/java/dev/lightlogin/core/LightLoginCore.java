package dev.lightlogin.core;

/**
 * Marker facade for the platform-independent core module.
 *
 * <p>The core module contains the domain model, cryptography, the captcha engine, rate limiting
 * and the repository ports. It has no dependency on any Minecraft server API, which keeps the
 * whole of it unit-testable on a plain JVM.</p>
 */
public final class LightLoginCore {

    private LightLoginCore() {
    }

    /** The semantic version of the core module. */
    public static final String VERSION = "3.0.0";
}