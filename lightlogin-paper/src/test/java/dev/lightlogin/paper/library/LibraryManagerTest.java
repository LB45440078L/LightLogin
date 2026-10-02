package dev.lightlogin.paper.library;

import dev.lightlogin.core.config.LibrariesConfig;
import dev.lightlogin.core.port.StorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline tests for the runtime library resolver.
 *
 * <p>Uses a {@code file:} repository, which is both a legitimate deployment option (an operator can
 * point at an internal mirror or a local directory) and what makes the download, verification and
 * caching paths testable without a network.</p>
 */
class LibraryManagerTest {

    private static final Logger LOGGER = Logger.getLogger("lightlogin-test");
    private static final byte[] JAR_BYTES = "pretend-this-is-a-jar".getBytes(StandardCharsets.UTF_8);

    /** A library whose probe class cannot be loaded, so resolution proceeds to disk. */
    private static Library libraryWithDigest(String digest) {
        return new Library("com.example", "demo", "1.0.0", digest,
                "com.example.NotOnAnyClasspath", "test fixture");
    }

    private static LibrariesConfig configFor(Path repository, boolean autoDownload) {
        return new LibrariesConfig(List.of(repository.toUri().toString()), autoDownload, 5_000, 10_000);
    }

    /** Lays out a repository directory containing one jar, and returns that jar's digest. */
    private static String seedRepository(Path repository, Library library) throws Exception {
        Path jar = repository.resolve(library.repositoryPath());
        Files.createDirectories(jar.getParent());
        Files.write(jar, JAR_BYTES);
        return LibraryManager.sha256(jar);
    }

    @Test
    @DisplayName("a library is downloaded from a file: repository, verified and cached")
    void downloadsAndCaches(@TempDir Path root) throws Exception {
        Path repository = root.resolve("repo");
        Path libs = root.resolve("libs");
        Library probe = libraryWithDigest("placeholder");
        String digest = seedRepository(repository, probe);
        Library library = libraryWithDigest(digest);

        LibraryManager manager = new LibraryManager(libs, configFor(repository, true), LOGGER);

        LibraryManager.Resolution first = manager.resolve(library);
        assertEquals(LibraryManager.Origin.DOWNLOADED, first.origin());
        assertArrayEquals(JAR_BYTES, Files.readAllBytes(first.file()));

        // A second resolve must reuse the verified file rather than fetching it again.
        LibraryManager.Resolution second = manager.resolve(library);
        assertEquals(LibraryManager.Origin.CACHED, second.origin());
        assertEquals(first.file(), second.file());
    }

    @Test
    @DisplayName("a cached file whose digest no longer matches is replaced")
    void replacesTamperedCache(@TempDir Path root) throws Exception {
        Path repository = root.resolve("repo");
        Path libs = root.resolve("libs");
        String digest = seedRepository(repository, libraryWithDigest("placeholder"));
        Library library = libraryWithDigest(digest);
        LibraryManager manager = new LibraryManager(libs, configFor(repository, true), LOGGER);

        Path cached = manager.resolve(library).file();
        Files.write(cached, "tampered".getBytes(StandardCharsets.UTF_8));

        LibraryManager.Resolution again = manager.resolve(library);
        assertEquals(LibraryManager.Origin.DOWNLOADED, again.origin(),
                "a file that no longer matches its digest must be fetched again");
        assertArrayEquals(JAR_BYTES, Files.readAllBytes(again.file()));
    }

    @Test
    @DisplayName("a download whose digest does not match the pinned value is rejected")
    void rejectsWrongDigest(@TempDir Path root) throws Exception {
        Path repository = root.resolve("repo");
        Path libs = root.resolve("libs");
        seedRepository(repository, libraryWithDigest("placeholder"));

        Library library = libraryWithDigest("0".repeat(64));
        LibraryManager manager = new LibraryManager(libs, configFor(repository, true), LOGGER);

        StorageException failure = assertThrows(StorageException.class, () -> manager.resolve(library));
        assertTrue(failure.getMessage().contains("digest"), failure.getMessage());
        assertFalse(Files.exists(libs.resolve(library.fileName())),
                "nothing that failed verification may be left in libs/");
    }

    @Test
    @DisplayName("with downloads disabled, a missing library fails with an actionable message")
    void refusesToDownloadWhenDisabled(@TempDir Path root) throws Exception {
        Path repository = root.resolve("repo");
        Path libs = root.resolve("libs");
        String digest = seedRepository(repository, libraryWithDigest("placeholder"));
        Library library = libraryWithDigest(digest);

        LibraryManager manager = new LibraryManager(libs, configFor(repository, false), LOGGER);
        StorageException failure = assertThrows(StorageException.class, () -> manager.resolve(library));
        assertTrue(failure.getMessage().contains("libraries.auto-download"), failure.getMessage());
        assertTrue(failure.getMessage().contains("libs/"), failure.getMessage());
    }

    @Test
    @DisplayName("a library the server already provides is used as-is, without a new loader")
    void usesTheServersOwnCopy(@TempDir Path root) {
        LibraryManager manager = new LibraryManager(root.resolve("libs"),
                configFor(root, false), LOGGER);
        // java.lang.String is on the parent loader by definition, so this library needs nothing.
        Library present = new Library("org.example", "already-there", "1.0.0", "unused",
                "java.lang.String", "stand-in for a server-provided library");

        List<LibraryManager.Resolution> resolutions =
                manager.resolveAll(getClass().getClassLoader(), List.of(present));
        assertEquals(1, resolutions.size());
        assertEquals(LibraryManager.Origin.SERVER, resolutions.get(0).origin());

        ClassLoader parent = getClass().getClassLoader();
        assertSame(parent, manager.loaderFor(parent, List.of(present)),
                "when nothing has to be added, the parent loader must be returned unchanged");
    }

    @Test
    @DisplayName("the SHA-256 helper matches a known digest")
    void sha256IsCorrect(@TempDir Path root) throws Exception {
        Path file = root.resolve("sample.bin");
        Files.write(file, JAR_BYTES);
        // sha256sum of "pretend-this-is-a-jar" with no trailing newline.
        assertEquals("f310cc54e11a37d63911e0d14ae77a6084151f7fe63ddc2bd051165040b717be",
                LibraryManager.sha256(file));
    }
}