package dev.lightlogin.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Authenticated symmetric encryption (AES-256-GCM) for secrets that must be recoverable: SMTP
 * credentials, the database password and other configuration values that cannot be hashed.
 *
 * <p>AES-GCM is an AEAD construction: it provides confidentiality <em>and</em> integrity, so a
 * tampered ciphertext fails to decrypt rather than yielding attacker-chosen plaintext. Each
 * encryption uses a fresh 96-bit nonce, which is the GCM-recommended size; the nonce is prepended
 * to the ciphertext and the whole is Base64-encoded.</p>
 */
public final class SecretBox {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** Marks an encrypted value so configuration can mix plaintext and ciphertext safely. */
    public static final String PREFIX = "enc:";

    private final SecretKeySpec key;
    private final SecureRandom random;

    public SecretBox(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != MasterKey.KEY_BYTES) {
            throw new IllegalArgumentException("SecretBox requires a " + MasterKey.KEY_BYTES + "-byte key");
        }
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
        this.random = new SecureRandom();
    }

    /** Encrypts a UTF-8 string and returns a prefixed, Base64-encoded envelope. */
    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(ciphertext, 0, envelope, nonce.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt(String)}. A value without the {@value #PREFIX}
     * marker is returned unchanged, allowing a migration from plaintext configuration.
     *
     * @throws GeneralSecurityException when the ciphertext is corrupt or the tag does not verify
     */
    public String decrypt(String stored) throws GeneralSecurityException {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        byte[] envelope = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        if (envelope.length <= NONCE_BYTES) {
            throw new GeneralSecurityException("Ciphertext too short");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        System.arraycopy(envelope, 0, nonce, 0, NONCE_BYTES);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        byte[] plaintext = cipher.doFinal(envelope, NONCE_BYTES, envelope.length - NONCE_BYTES);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /** Reports whether a stored value is an encrypted envelope. */
    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}