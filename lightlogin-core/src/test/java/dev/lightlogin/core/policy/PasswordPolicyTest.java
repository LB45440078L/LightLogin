package dev.lightlogin.core.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private static final PasswordPolicy POLICY = new PasswordPolicy(new PasswordPolicyConfig(
            8, 64, 1, 1, 1, 1,
            Set.of('!', '@', '#', '$', '%'),
            true, true, List.of("myserver")));

    private static boolean has(List<PolicyViolation> violations, PolicyViolation.Code code) {
        return violations.stream().anyMatch(v -> v.code() == code);
    }

    @Test
    @DisplayName("a compliant password passes")
    void compliant() {
        assertTrue(POLICY.validate("Str0ng!Pass".toCharArray(), "steve").isEmpty());
    }

    @Test
    @DisplayName("each missing character class is reported")
    void compositionRules() {
        List<PolicyViolation> violations = POLICY.validate("short".toCharArray(), "steve");
        assertTrue(has(violations, PolicyViolation.Code.TOO_SHORT));
        assertTrue(has(violations, PolicyViolation.Code.NEEDS_UPPERCASE));
        assertTrue(has(violations, PolicyViolation.Code.NEEDS_DIGIT));
        assertTrue(has(violations, PolicyViolation.Code.NEEDS_SPECIAL));
    }

    @Test
    @DisplayName("all violations are reported at once, not just the first")
    void reportsEverything() {
        List<PolicyViolation> violations = POLICY.validate("a".toCharArray(), "steve");
        assertTrue(violations.size() >= 4);
    }

    @Test
    @DisplayName("a password containing the username is rejected")
    void usernameDenied() {
        List<PolicyViolation> violations = POLICY.validate("Steve-Str0ng!".toCharArray(), "steve");
        assertTrue(has(violations, PolicyViolation.Code.CONTAINS_USERNAME));
    }

    @Test
    @DisplayName("a common password is rejected even when it meets composition rules")
    void commonPasswordDenied() {
        List<PolicyViolation> violations = POLICY.validate("Password1!".toCharArray(), "steve");
        assertTrue(has(violations, PolicyViolation.Code.COMMON_PASSWORD));
    }

    @Test
    @DisplayName("a banned substring is rejected")
    void bannedSubstring() {
        List<PolicyViolation> violations = POLICY.validate("MyServer-Str0ng!".toCharArray(), "steve");
        assertTrue(has(violations, PolicyViolation.Code.CONTAINS_BANNED_SUBSTRING));
    }

    @Test
    @DisplayName("a character outside the allowed special set is rejected")
    void disallowedCharacter() {
        List<PolicyViolation> violations = POLICY.validate("Str0ng~Pass".toCharArray(), "steve");
        assertTrue(has(violations, PolicyViolation.Code.DISALLOWED_CHARACTER));
    }

    @Test
    @DisplayName("the maximum length bounds the Argon2 input")
    void maxLength() {
        String longPassword = "A1!" + "x".repeat(200);
        List<PolicyViolation> violations = POLICY.validate(longPassword.toCharArray(), "steve");
        assertTrue(has(violations, PolicyViolation.Code.TOO_LONG));
    }

    @Test
    @DisplayName("the blocklist loaded from the classpath")
    void blocklistLoaded() {
        assertTrue(CommonPasswords.size() > 50, "expected a populated blocklist");
        assertTrue(CommonPasswords.contains("password".toCharArray()));
        assertFalse(CommonPasswords.contains("X9!qZ-unlikely".toCharArray()));
    }

    @Test
    @DisplayName("an invalid policy configuration is rejected")
    void invalidConfig() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PasswordPolicyConfig(4, 64, 1, 1, 1, 1, Set.of('!'), true, true, List.of()));
    }

    @Test
    @DisplayName("unicode letters count as letters, not as disallowed characters")
    void unicode() {
        List<PolicyViolation> violations = POLICY.validate("Pässw0rd!x".toCharArray(), "steve");
        assertFalse(has(violations, PolicyViolation.Code.DISALLOWED_CHARACTER));
    }

    @Test
    @DisplayName("the default configuration is usable out of the box")
    void defaults() {
        PasswordPolicy defaults = new PasswordPolicy(PasswordPolicyConfig.DEFAULT);
        assertEquals(0, defaults.config().minSpecial());
        assertTrue(defaults.validate("LongEnough1".toCharArray(), "steve").isEmpty());
    }
}