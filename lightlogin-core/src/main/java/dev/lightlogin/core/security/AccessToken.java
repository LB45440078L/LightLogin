package dev.lightlogin.core.security;

import dev.lightlogin.core.crypto.ConstantTime;

import java.util.Objects;

/**
 * An opaque capability token guarding a privileged internal API.
 *
 * <p>Within a single JVM any code can reflect into any other code, so "isolation" cannot be
 * absolute. What a token buys is that privileged operations are not reachable by simply calling a
 * public method: a caller must first obtain a token from the {@link SecurityContext}, which is only
 * handed to components the plugin itself wires together at boot. A malicious plugin that discovers
 * a method by reflection still cannot supply a valid token without also reading it out of the
 * running JVM, and every attempt to present a wrong token is a detectable event.</p>
 *
 * @param value the secret token value
 */
public record AccessToken(String value) {

    public AccessToken {
        Objects.requireNonNull(value, "value");
        if (value.length() < 22) {
            throw new IllegalArgumentException("Access token is too short to be secure");
        }
    }

    /** Constant-time comparison, so a guess cannot be narrowed down by timing. */
    public boolean matches(AccessToken other) {
        return other != null && ConstantTime.equals(this.value, other.value);
    }

    /** Never render the token itself, even accidentally. */
    @Override
    public String toString() {
        return "AccessToken[redacted]";
    }
}