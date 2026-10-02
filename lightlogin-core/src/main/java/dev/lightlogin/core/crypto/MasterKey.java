package dev.lightlogin.core.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;

/**
 * Loads (or lazily creates) the 256-bit master key used to encrypt secrets at rest.
 *
 * <p>The key lives in a file outside the plugin's configuration directory and is created with
 * owner-only permissions ({@code 0600}) where the filesystem supports POSIX modes. This is the
 * boundary that protects the SMTP credentials, the database password and the password pepper if
 * the configuration or database is exfiltrated without the filesystem.</p>
 */
public final class MasterKey {

    /** Length of the AES-256 key. */
    public static final int KEY_BYTES = 32;

    private MasterKey() {
    }

    /**
     * Loads the key from {@code keyFile}, generating and persisting a fresh one when absent.
     *
     * @throws IOException when the file cannot be read, written or is malformed
     */
    public static byte[] loadOrCreate(Path keyFile) throws IOException {
        Path parent = keyFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(keyFile)) {
            byte[] key = Files.readAllBytes(keyFile);
            if (key.length != KEY_BYTES) {
                throw new IOException("Master key at " + keyFile + " must be exactly " + KEY_BYTES
                        + " bytes, found " + key.length);
            }
            return key;
        }
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        Files.write(keyFile, key);
        restrictPermissions(keyFile);
        return key;
    }

    /** Best-effort tightening of file permissions to owner read/write only. */
    public static void restrictPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows or a non-POSIX filesystem: permissions are not enforceable here.
        }
    }

    /** Renders owner-only permissions for documentation and diagnostics. */
    public static String expectedPermissions() {
        return PosixFilePermissions.toString(EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
    }
}