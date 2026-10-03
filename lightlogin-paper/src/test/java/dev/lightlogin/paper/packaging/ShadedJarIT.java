package dev.lightlogin.paper.packaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test against the <em>shaded jar</em>, not the test classpath.
 *
 * <p>A shaded jar fails in ways no ordinary unit test can see. This suite guards the three things
 * that have actually gone wrong or would silently regress:</p>
 *
 * <ul>
 *   <li><b>Size.</b> The jar must stay small, which means the drivers and the GeoIP reader stay out
 *       of it and BouncyCastle is trimmed to the classes Argon2 reaches. A build that quietly starts
 *       bundling them again is caught here rather than by an operator noticing a 24 MB upload.</li>
 *   <li><b>Relocation versus multi-release content.</b> Shade rewrites the classes it copies but not
 *       the {@code META-INF/versions/N/...} copies, so relocating a multi-release library produces
 *       two inconsistent halves. That is what once broke SQLite with an
 *       {@code UnsatisfiedLinkError} on startup. The jar is now expected to contain no
 *       multi-release content at all.</li>
 *   <li><b>The trimmed BouncyCastle actually works.</b> Keeping only the classes in Argon2's runtime
 *       closure is only safe if hashing and verification really do succeed using nothing but what
 *       the jar contains — so this test hashes and verifies a password through the packaged jar,
 *       loaded in isolation.</li>
 * </ul>
 *
 * <p>Runs in the {@code integration-test} phase, after {@code package}. Run it with {@code mvn verify}.</p>
 */
class ShadedJarIT {

    /** The ceiling the jar must stay under, in bytes. */
    private static final long MAX_JAR_BYTES = 4L * 1024 * 1024;

    @Test
    @DisplayName("the shaded jar stays below the 4 MB size ceiling")
    void jarStaysSmall() throws Exception {
        long size = Files.size(shadedJar());
        assertTrue(size <= MAX_JAR_BYTES,
                () -> "the shaded jar is %.2f MB, above the %.0f MB ceiling; the drivers or the GeoIP "
                        .formatted(size / 1048576.0, MAX_JAR_BYTES / 1048576.0)
                        + "reader were bundled again instead of being resolved at runtime");
    }

    @Test
    @DisplayName("the drivers and the GeoIP reader are not bundled")
    void heavyLibrariesAreNotBundled() throws Exception {
        Set<String> unexpected = new LinkedHashSet<>();
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            jar.stream().map(ZipEntry::getName).filter(name -> name.endsWith(".class"))
                    .filter(name -> name.startsWith("org/sqlite/")
                            || name.startsWith("org/postgresql/")
                            || name.startsWith("org/mariadb/")
                            || name.startsWith("com/maxmind/")
                            || name.startsWith("com/fasterxml/"))
                    .forEach(unexpected::add);
        }
        assertTrue(unexpected.isEmpty(),
                "these are resolved at runtime and must not be bundled: " + unexpected);
    }

    @Test
    @DisplayName("bundled libraries are relocated into the plugin's own namespace")
    void bundledLibrariesAreNamespaced() throws Exception {
        Set<String> unnamespaced = new LinkedHashSet<>();
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            jar.stream().map(ZipEntry::getName).filter(name -> name.endsWith(".class"))
                    .filter(name -> name.startsWith("org/slf4j/")
                            || name.startsWith("com/zaxxer/hikari/")
                            || name.startsWith("jakarta/mail/")
                            || name.startsWith("org/bouncycastle/")
                            || name.startsWith("org/eclipse/angus/"))
                    .forEach(unnamespaced::add);

            assertNotNull(jar.getEntry("dev/lightlogin/libs/com/zaxxer/hikari/HikariDataSource.class"),
                    "HikariCP should be present under its relocated name");
            assertNotNull(jar.getEntry("dev/lightlogin/libs/jakarta/mail/Session.class"),
                    "jakarta.mail should be present under its relocated name");
            assertNotNull(jar.getEntry(
                            "dev/lightlogin/libs/org/bouncycastle/crypto/generators/Argon2BytesGenerator.class"),
                    "BouncyCastle should be present under its relocated name");
        }
        assertTrue(unnamespaced.isEmpty(),
                "these libraries should have been relocated into dev/lightlogin/libs/: " + unnamespaced);
    }

    @Test
    @DisplayName("the jar carries no multi-release content, so nothing can be relocated inconsistently")
    void noMultiReleaseContent() throws Exception {
        Set<String> multiRelease = new LinkedHashSet<>();
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            jar.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("META-INF/versions/") && name.endsWith(".class"))
                    .forEach(multiRelease::add);
        }
        assertTrue(multiRelease.isEmpty(),
                "shade does not rewrite META-INF/versions/**, so a relocated multi-release library "
                        + "ends up inconsistent and fails at runtime; found: " + multiRelease);
    }

    @Test
    @DisplayName("every META-INF/services entry names a class that exists in the jar")
    void serviceFilesResolve() throws Exception {
        Set<String> broken = new LinkedHashSet<>();
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            jar.stream()
                    .filter(entry -> entry.getName().startsWith("META-INF/services/") && !entry.isDirectory())
                    .forEach(entry -> {
                        try (var in = jar.getInputStream(entry)) {
                            new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                                    .lines()
                                    .map(String::strip)
                                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                                    .forEach(className -> {
                                        String path = className.replace('.', '/') + ".class";
                                        if (jar.getEntry(path) == null) {
                                            broken.add(entry.getName() + " -> " + className);
                                        }
                                    });
                        } catch (Exception e) {
                            broken.add(entry.getName() + " (unreadable: " + e.getMessage() + ')');
                        }
                    });
        }
        assertTrue(broken.isEmpty(),
                "META-INF/services entries point at classes that are not in the jar: " + broken);
    }

    @Test
    @DisplayName("Argon2id hashes and verifies using only what the jar contains")
    void argon2WorksFromTheTrimmedJar() throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{shadedJar().toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        try {
            Class<?> hasherType = Class.forName("dev.lightlogin.core.crypto.Argon2idPasswordHasher", true, loader);
            Object hasher = hasherType.getMethod("createDefault").invoke(null);
            var hash = hasherType.getMethod("hash", char[].class);
            var verify = hasherType.getMethod("verify", char[].class, String.class);
            var needsRehash = hasherType.getMethod("needsRehash", String.class);

            char[] password = "MarcoCampari2003!".toCharArray();
            String encoded = (String) hash.invoke(hasher, (Object) password);
            assertTrue(encoded.startsWith("$argon2id$"), "unexpected encoding: " + encoded);

            assertEquals(Boolean.TRUE, verify.invoke(hasher, "MarcoCampari2003!".toCharArray(), encoded),
                    "the correct password must verify against a hash produced by the packaged jar");
            assertEquals(Boolean.FALSE, verify.invoke(hasher, "not-the-password".toCharArray(), encoded),
                    "a wrong password must not verify");
            assertEquals(Boolean.FALSE, needsRehash.invoke(hasher, encoded),
                    "a hash written with the current parameters must not need a rehash");
            assertEquals(Boolean.TRUE, needsRehash.invoke(hasher, "not-a-hash"),
                    "anything that is not a well-formed argon2id hash must be rehashed");

            // Independent salts: same password, different encoding, both verifying.
            String second = (String) hash.invoke(hasher, (Object) "MarcoCampari2003!".toCharArray());
            assertFalse(second.equals(encoded), "each hash must use a fresh salt");
            assertEquals(Boolean.TRUE, verify.invoke(hasher, "MarcoCampari2003!".toCharArray(), second));
        } finally {
            loader.close();
        }
    }

    /** The shaded jar: the one that is not the pre-shade {@code original-} artifact. */
    private static Path shadedJar() throws Exception {
        Path target = Path.of("target");
        assertTrue(Files.isDirectory(target),
                "expected the module target directory at " + target.toAbsolutePath());
        try (Stream<Path> stream = Files.list(target)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().startsWith("original-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no shaded jar in " + target.toAbsolutePath()
                            + "; run `mvn verify` so the package phase runs before this test"));
        }
    }
}