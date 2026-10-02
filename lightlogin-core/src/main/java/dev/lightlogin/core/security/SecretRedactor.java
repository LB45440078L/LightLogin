package dev.lightlogin.core.security;

import dev.lightlogin.core.crypto.ConstantTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Removes secrets from text before it can reach a log, a console line, an audit record or a web
 * page.
 *
 * <p>The original plugin relied solely on a Log4j filter, which misses anything logged through
 * another framework or written straight to the console. This redactor is applied at the point where
 * a line is produced, so every sink benefits. Registered secrets (session tokens, the internal
 * access token, the pepper) are matched literally and in constant time; password-shaped command
 * arguments are additionally masked by pattern.</p>
 */
public final class SecretRedactor {

    /** The replacement written in place of a secret. */
    public static final String MASK = "[REDACTED]";

    private static final java.util.regex.Pattern PASSWORD_COMMAND = java.util.regex.Pattern.compile(
            "(?i)(/?(?:login|register|changepassword|changepsw|resetpassword|verify)\\s+)(.*)$");

    private final Set<String> secrets = new CopyOnWriteArraySet<>();
    private final boolean maskCommands;

    public SecretRedactor(boolean maskCommands) {
        this.maskCommands = maskCommands;
    }

    /** Registers a literal secret to be masked wherever it appears. */
    public void registerSecret(String secret) {
        if (secret != null && secret.length() >= 8) {
            secrets.add(secret);
        }
    }

    /** Redacts a single line. */
    public String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String output = input;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty() && output.contains(secret)) {
                output = output.replace(secret, MASK);
            }
        }
        if (maskCommands) {
            output = PASSWORD_COMMAND.matcher(output).replaceAll("$1" + MASK);
        }
        return output;
    }

    /** Redacts each line of a collection, preserving order. */
    public List<String> redactAll(Iterable<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(redact(line));
        }
        return out;
    }

    /** Whether two candidate secrets are equal, in constant time (used by tests and callers). */
    public static boolean secretEquals(String a, String b) {
        return ConstantTime.equals(a, b);
    }

    /** Normalises a secret for case-insensitive comparison where appropriate. */
    public static String normalise(String secret) {
        return secret == null ? null : secret.toLowerCase(Locale.ROOT);
    }
}