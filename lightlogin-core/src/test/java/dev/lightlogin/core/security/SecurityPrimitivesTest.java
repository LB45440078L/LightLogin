package dev.lightlogin.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPrimitivesTest {

    @Test
    @DisplayName("a valid token authorises; a wrong or absent one raises a violation")
    void accessTokenGate() {
        SecurityContext context = new SecurityContext();
        context.require(context.bootToken()); // does not throw

        assertThrows(SecurityViolationException.class, () -> context.require(null));
        assertThrows(SecurityViolationException.class,
                () -> context.require(new AccessToken("AAAAAAAAAAAAAAAAAAAAAA")));
        assertEquals(2, context.violationCount());
        assertTrue(context.isValid(context.bootToken()));
        assertFalse(context.isValid(null));
    }

    @Test
    @DisplayName("tokens never render their value")
    void tokenRedaction() {
        SecurityContext context = new SecurityContext();
        assertEquals("AccessToken[redacted]", context.bootToken().toString());
    }

    @Test
    @DisplayName("the redactor masks registered secrets and password-bearing commands")
    void redactor() {
        SecretRedactor redactor = new SecretRedactor(true);
        redactor.registerSecret("super-secret-token-value");

        String line = "user authenticated with super-secret-token-value from 1.2.3.4";
        assertFalse(redactor.redact(line).contains("super-secret-token-value"));
        assertTrue(redactor.redact(line).contains(SecretRedactor.MASK));

        assertEquals("/login " + SecretRedactor.MASK, redactor.redact("/login MyPassword1!"));
        assertEquals("/register " + SecretRedactor.MASK, redactor.redact("/register hunter2 hunter2"));
        assertEquals("/changepassword " + SecretRedactor.MASK, redactor.redact("/changepassword old new"));
        assertTrue(redactor.redact("a normal chat line").equals("a normal chat line"));
    }

    @Test
    @DisplayName("short secrets are not registered (avoids masking ordinary words)")
    void shortSecretsIgnored() {
        SecretRedactor redactor = new SecretRedactor(false);
        redactor.registerSecret("abc");
        assertEquals("abc", redactor.redact("abc"));
    }

    @Test
    @DisplayName("redactAll preserves line order and count")
    void redactAll() {
        SecretRedactor redactor = new SecretRedactor(true);
        List<String> out = redactor.redactAll(List.of("/login pw1", "hello", "/verify 42"));
        assertEquals(3, out.size());
        assertFalse(out.get(0).contains("pw1"));
        assertEquals("hello", out.get(1));
        assertFalse(out.get(2).contains("42"));
    }

    @Test
    @DisplayName("TOTP codes are generated and verified with a skew window")
    void totp() {
        String secret = "JBSWY3DPEHPK3PXP"; // a well-known test secret
        assertTrue(Totp.isWellFormed(secret));
        long now = 1_700_000_000_000L;
        String code = Totp.code(secret, now);

        assertEquals(6, code.length());
        assertTrue(Totp.verify(secret, code, now));
        assertTrue(Totp.verify(secret, code, now + 30_000), "one step of skew is tolerated");
        assertFalse(Totp.verify(secret, "000000", now + 600_000), "far future code is rejected");
        assertFalse(Totp.verify(secret, null, now));
    }

    @Test
    @DisplayName("a malformed TOTP secret is rejected")
    void totpValidation() {
        assertFalse(Totp.isWellFormed("short"));
        assertFalse(Totp.isWellFormed("1111111111111111")); // 1 is not in the base32 alphabet
        assertFalse(Totp.isWellFormed(null));
    }
}