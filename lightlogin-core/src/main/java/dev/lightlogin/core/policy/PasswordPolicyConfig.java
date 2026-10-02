package dev.lightlogin.core.policy;

import java.util.Objects;

/**
 * Configurable password composition rules.
 *
 * @param minLength          minimum length
 * @param maxLength          maximum length (bounds the Argon2 input and prevents DoS by huge input)
 * @param minUppercase       minimum uppercase letters
 * @param minLowercase       minimum lowercase letters
 * @param minDigits          minimum digits
 * @param minSpecial         minimum special characters
 * @param allowedSpecial     the set of characters counted as special
 * @param denyUsername       whether the password may contain the username
 * @param denyCommon         whether a common-password blocklist is enforced
 * @param bannedSubstrings   case-insensitive substrings that are refused (server name, etc.)
 */
public record PasswordPolicyConfig(
        int minLength,
        int maxLength,
        int minUppercase,
        int minLowercase,
        int minDigits,
        int minSpecial,
        java.util.Set<Character> allowedSpecial,
        boolean denyUsername,
        boolean denyCommon,
        java.util.List<String> bannedSubstrings) {

    /** A sensible default policy. */
    public static final PasswordPolicyConfig DEFAULT = new PasswordPolicyConfig(
            8, 64, 1, 1, 1, 0,
            java.util.Set.of('!', '@', '#', '$', '%', '^', '&', '*', '-', '_', '?', '+', '='),
            true, true, java.util.List.of());

    public PasswordPolicyConfig {
        if (minLength < 8) {
            throw new IllegalArgumentException("minLength must be at least 8");
        }
        if (maxLength > 256) {
            throw new IllegalArgumentException("maxLength must be at most 256");
        }
        if (minLength > maxLength) {
            throw new IllegalArgumentException("minLength cannot exceed maxLength");
        }
        allowedSpecial = java.util.Set.copyOf(Objects.requireNonNull(allowedSpecial, "allowedSpecial"));
        bannedSubstrings = java.util.List.copyOf(Objects.requireNonNull(bannedSubstrings, "bannedSubstrings"));
    }
}