package dev.lightlogin.core.policy;

/**
 * A single failed password-policy rule, with a stable machine code and a human message.
 *
 * @param code    stable identifier, safe to match on
 * @param message human-readable explanation
 */
public record PolicyViolation(Code code, String message) {

    /** Stable rule identifiers. */
    public enum Code {
        TOO_SHORT,
        TOO_LONG,
        NEEDS_UPPERCASE,
        NEEDS_LOWERCASE,
        NEEDS_DIGIT,
        NEEDS_SPECIAL,
        DISALLOWED_CHARACTER,
        CONTAINS_USERNAME,
        COMMON_PASSWORD,
        CONTAINS_BANNED_SUBSTRING
    }

    public static PolicyViolation of(Code code, String message) {
        return new PolicyViolation(code, message);
    }
}