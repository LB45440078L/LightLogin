package dev.lightlogin.core.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenGeneratorTest {

    private final TokenGenerator generator = new TokenGenerator();

    @Test
    @DisplayName("tokens are unique and url-safe")
    void uniqueTokens() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = generator.token();
            assertTrue(seen.add(token), "duplicate token generated");
            assertFalse(token.contains("+") || token.contains("/") || token.contains("="));
        }
    }

    @Test
    @DisplayName("token length follows the requested entropy")
    void tokenLength() {
        // 32 bytes -> 43 base64 chars without padding.
        assertEquals(43, generator.token(32).length());
        assertEquals(22, generator.token(16).length());
    }

    @Test
    @DisplayName("entropy below 128 bits is refused")
    void entropyFloor() {
        assertThrows(IllegalArgumentException.class, () -> generator.token(8));
    }

    @Test
    @DisplayName("temporary passwords avoid ambiguous characters")
    void temporaryPasswords() {
        for (int i = 0; i < 200; i++) {
            String password = generator.temporaryPassword(16);
            assertEquals(16, password.length());
            assertFalse(password.matches(".*[0O1lI].*"), "ambiguous character in " + password);
        }
        assertThrows(IllegalArgumentException.class, () -> generator.temporaryPassword(4));
    }

    @Test
    @DisplayName("constant-time comparison is correct")
    void constantTimeComparison() {
        assertTrue(ConstantTime.equals("abc", "abc"));
        assertFalse(ConstantTime.equals("abc", "abd"));
        assertFalse(ConstantTime.equals("abc", "ab"));
        assertTrue(ConstantTime.equals((String) null, null));
        assertFalse(ConstantTime.equals("a", null));
    }

    @Test
    @DisplayName("wiping clears arrays")
    void wiping() {
        byte[] bytes = {1, 2, 3};
        ConstantTime.wipe(bytes);
        assertTrue(java.util.Arrays.equals(new byte[3], bytes));

        char[] chars = {'a', 'b'};
        ConstantTime.wipe(chars);
        assertEquals(0, chars[0]);
        assertEquals(0, chars[1]);
    }
}