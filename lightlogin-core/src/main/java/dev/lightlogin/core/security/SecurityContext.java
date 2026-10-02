package dev.lightlogin.core.security;

import dev.lightlogin.core.crypto.TokenGenerator;

import java.util.Objects;

/**
 * Issues and verifies {@link AccessToken}s and is the single gate for privileged internal APIs.
 *
 * <p>The boot token is generated once per plugin instance from a CSPRNG and never persisted or
 * logged. Components that need to expose a privileged operation take a token in their method
 * signature and call {@link #require(AccessToken)} first; the token therefore acts as a capability
 * that cannot be forged from a public API surface alone.</p>
 */
public final class SecurityContext {

    private final AccessToken bootToken;
    private final TokenGenerator tokens;
    private volatile int violationCount;

    public SecurityContext() {
        this(new TokenGenerator());
    }

    public SecurityContext(TokenGenerator tokens) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.bootToken = new AccessToken(tokens.token(32));
    }

    /** The token held by the plugin's own wired components. */
    public AccessToken bootToken() {
        return bootToken;
    }

    /** Mints an additional token for a trusted component. */
    public AccessToken issueToken() {
        return new AccessToken(tokens.token(32));
    }

    /**
     * Asserts that {@code candidate} is a token this context issued.
     *
     * @throws SecurityViolationException when the token is missing or wrong
     */
    public void require(AccessToken candidate) {
        if (candidate == null || !bootToken.matches(candidate)) {
            violationCount++;
            throw new SecurityViolationException(
                    "Privileged LightLogin API called without a valid internal access token");
        }
    }

    /** Whether {@code candidate} is valid, without throwing. */
    public boolean isValid(AccessToken candidate) {
        return candidate != null && bootToken.matches(candidate);
    }

    /** The number of rejected privileged calls since boot; surfaced as a security metric. */
    public int violationCount() {
        return violationCount;
    }
}