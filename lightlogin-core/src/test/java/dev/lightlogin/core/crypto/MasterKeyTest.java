package dev.lightlogin.core.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterKeyTest {

    @Test
    @DisplayName("a missing key file is generated, persisted and reloaded identically")
    void generateAndReload(@TempDir Path dir) throws IOException {
        Path keyFile = dir.resolve("lightlogin.key");

        byte[] first = MasterKey.loadOrCreate(keyFile);
        assertEquals(MasterKey.KEY_BYTES, first.length);
        assertTrue(Files.exists(keyFile));

        byte[] second = MasterKey.loadOrCreate(keyFile);
        assertArrayEquals(first, second, "reloading must return the same key");
    }

    @Test
    @DisplayName("a corrupt key file is rejected rather than silently truncated")
    void corruptKeyFile(@TempDir Path dir) throws IOException {
        Path keyFile = dir.resolve("bad.key");
        Files.write(keyFile, new byte[8]);
        assertThrows(IOException.class, () -> MasterKey.loadOrCreate(keyFile));
    }
}