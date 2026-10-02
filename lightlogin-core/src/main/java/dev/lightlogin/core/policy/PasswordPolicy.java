package dev.lightlogin.core.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates a candidate password against a {@link PasswordPolicyConfig}.
 *
 * <p>Pure and stateless, so it is trivially testable and cheap to call on a worker thread. It never
 * retains the password and reports every violation rather than only the first, so a player can fix
 * everything in one attempt instead of being drip-fed errors.</p>
 */
public final class PasswordPolicy {

    private final PasswordPolicyConfig config;

    public PasswordPolicy(PasswordPolicyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Validates a password.
     *
     * @param password the candidate
     * @param username the account username, or {@code null} when not applicable
     * @return an empty list when acceptable, otherwise every violation
     */
    public List<PolicyViolation> validate(char[] password, String username) {
        Objects.requireNonNull(password, "password");
        List<PolicyViolation> violations = new ArrayList<>();

        int length = password.length;
        if (length < config.minLength()) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.TOO_SHORT,
                    "Password must be at least " + config.minLength() + " characters"));
        }
        if (length > config.maxLength()) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.TOO_LONG,
                    "Password must be at most " + config.maxLength() + " characters"));
        }

        int upper = 0;
        int lower = 0;
        int digits = 0;
        int special = 0;
        boolean disallowed = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) {
                upper++;
            } else if (Character.isLowerCase(c)) {
                lower++;
            } else if (Character.isDigit(c)) {
                digits++;
            } else if (config.allowedSpecial().contains(c)) {
                special++;
            } else if (!Character.isWhitespace(c) && !isUnicodeLetterOrDigit(c)) {
                disallowed = true;
            }
        }

        if (upper < config.minUppercase()) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.NEEDS_UPPERCASE,
                    "Password needs at least " + config.minUppercase() + " uppercase letter(s)"));
        }
        if (lower < config.minLowercase()) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.NEEDS_LOWERCASE,
                    "Password needs at least " + config.minLowercase() + " lowercase letter(s)"));
        }
        if (digits < config.minDigits()) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.NEEDS_DIGIT,
                    "Password needs at least " + config.minDigits() + " digit(s)"));
        }
        if (special < config.minSpecial()) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.NEEDS_SPECIAL,
                    "Password needs at least " + config.minSpecial() + " special character(s)"));
        }
        if (disallowed) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.DISALLOWED_CHARACTER,
                    "Password contains a character that is not allowed"));
        }

        String lowered = new String(password).toLowerCase(Locale.ROOT);
        if (config.denyUsername() && username != null && username.length() >= 3
                && lowered.contains(username.toLowerCase(Locale.ROOT))) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.CONTAINS_USERNAME,
                    "Password must not contain your username"));
        }
        for (String banned : config.bannedSubstrings()) {
            if (!banned.isBlank() && lowered.contains(banned.toLowerCase(Locale.ROOT))) {
                violations.add(PolicyViolation.of(PolicyViolation.Code.CONTAINS_BANNED_SUBSTRING,
                        "Password contains a forbidden word"));
                break;
            }
        }
        if (config.denyCommon() && CommonPasswords.contains(password)) {
            violations.add(PolicyViolation.of(PolicyViolation.Code.COMMON_PASSWORD,
                    "This password is too common and easily guessed"));
        }

        return List.copyOf(violations);
    }

    private static boolean isUnicodeLetterOrDigit(char c) {
        return Character.isLetterOrDigit(c);
    }

    public PasswordPolicyConfig config() {
        return config;
    }
}