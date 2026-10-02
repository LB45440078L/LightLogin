package dev.lightlogin.core.security;

/**
 * Thrown when a privileged internal API is called without a valid {@link AccessToken}.
 *
 * <p>This is deliberately a distinct type: the plugin installs a global exception handler and
 * raises a high-severity alert when one is observed, because in a healthy server it can only mean
 * a foreign plugin is probing the authentication internals.</p>
 */
public class SecurityViolationException extends RuntimeException {

    public SecurityViolationException(String message) {
        super(message);
    }
}