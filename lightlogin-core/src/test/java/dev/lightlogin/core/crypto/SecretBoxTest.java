package dev.lightlogin.core.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretBoxTest {

    private static byte[] key() {
        byte[] k = new byte[32];
        for (int i = 0; i < k.length; i++) {
            k[i] = (byte) (i + 1);
        }
        return k;
    }

    @Test
    @DisplayName("a secret round-trips through AES-GCM")
    void roundTrip() throws Exception {
        SecretBox box = new SecretBox(key());
        String secret = "smtp-p@ssw0rd-with-ünicode";
        String sealed = box.encrypt(secret);

        assertTrue(sealed.startsWith(SecretBox.PREFIX));
        assertTrue(SecretBox.isEncrypted(sealed));
        assertEquals(secret, box.decrypt(sealed));
    }

    @Test
    @DisplayName("each encryption uses a fresh nonce, so ciphertexts differ")
    void freshNonce() {
        SecretBox box = new SecretBox(key());
        assertNotEquals(box.encrypt("same"), box.encrypt("same"));
    }

    @Test
    @DisplayName("tampered ciphertext fails authentication instead of decrypting")
    void tamperDetection() {
        SecretBox box = new SecretBox(key());
        String sealed = box.encrypt("important");

        byte[] raw = Base64.getDecoder().decode(sealed.substring(SecretBox.PREFIX.length()));
        raw[raw.length - 1] ^= 0x01; // flip a bit in the GCM tag
        String tampered = SecretBox.PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThrows(Exception.class, () -> box.decrypt(tampered));
    }

    @Test
    @DisplayName("plaintext values pass through decrypt so config can be migrated gradually")
    void plaintextPassthrough() throws Exception {
        SecretBox box = new SecretBox(key());
        assertEquals("plain", box.decrypt("plain"));
        assertFalse(SecretBox.isEncrypted("plain"));
    }

    @Test
    @DisplayName("a wrong key cannot decrypt another key's ciphertext")
    void wrongKeyFails() {
        SecretBox a = new SecretBox(key());
        byte[] other = key();
        other[0] ^= 0x7f;
        SecretBox b = new SecretBox(other);

        String sealed = a.encrypt("secret");
        assertThrows(Exception.class, () -> b.decrypt(sealed));
    }

    @Test
    @DisplayName("the box refuses a key that is not 256 bits")
    void keyLength() {
        assertThrows(IllegalArgumentException.class, () -> new SecretBox(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new SecretBox(null));
    }
}