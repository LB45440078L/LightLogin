package dev.lightlogin.paper.packaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Properties;
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
 * <p>This exists because a shaded jar can fail in ways no ordinary unit test can see. A relocation
 * that moves a class away from the resources it loads at runtime is invisible to the compiler, to
 * surefire (which runs against the unshaded classes) and to a successful build — and then the
 * plugin dies on the server. That is exactly what happened once: {@code org.sqlite} was relocated,
 * so the driver could no longer find its own native library and startup failed with
 * {@code UnsatisfiedLinkError}.</p>
 *
 * <p>Runs in the {@code integration-test} phase, after {@code package}, so the jar exists. Run it
 * with {@code mvn verify}.</p>
 */
class ShadedJarIT {

    @Test
    @DisplayName("the shaded jar can open a SQLite database and create the file")
    void shadedJarOpensSqlite() throws Exception {
        Path jar = shadedJar();
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());

        Class<?> driverType = Class.forName("org.sqlite.JDBC", true, loader);
        Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();

        Path database = Files.createTempDirectory("lightlogin-it").resolve("probe.db");
        try (Connection connection = driver.connect("jdbc:sqlite:" + database, new Properties())) {
            assertNotNull(connection, "the bundled SQLite driver must return a connection");
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE probe (id INTEGER PRIMARY KEY, value TEXT)");
                statement.execute("INSERT INTO probe (value) VALUES ('ok')");
                try (ResultSet rows = statement.executeQuery("SELECT value FROM probe")) {
                    assertTrue(rows.next());
                    assertEquals("ok", rows.getString(1));
                }
            }
        }
        assertTrue(Files.exists(database),
                "opening the database must create the file at " + database);
    }

    @Test
    @DisplayName("no library that ships multi-release content is relocated")
    void multiReleaseLibrariesAreNotRelocated() throws IOException {
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            // Packages that appear under META-INF/versions/N/... cannot survive relocation,
            // because shade does not rewrite those copies.
            Set<String> multiReleasePackages = new LinkedHashSet<>();
            jar.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("META-INF/versions/") && name.endsWith(".class"))
                    .forEach(name -> {
                        String[] parts = name.split("/");
                        if (parts.length >= 5) {
                            multiReleasePackages.add(parts[3] + "/" + parts[4]);
                        }
                    });
            assertFalse(multiReleasePackages.isEmpty(),
                    "expected the jar to contain multi-release content; the check would be vacuous");

            Set<String> relocatedViolations = new LinkedHashSet<>();
            jar.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("dev/lightlogin/libs/") && name.endsWith(".class"))
                    .forEach(name -> {
                        String rest = name.substring("dev/lightlogin/libs/".length());
                        for (String pkg : multiReleasePackages) {
                            if (rest.startsWith(pkg + "/")) {
                                relocatedViolations.add(rest);
                            }
                        }
                    });
            assertTrue(relocatedViolations.isEmpty(),
                    "these classes belong to a multi-release library but were relocated, which breaks "
                            + "them at runtime: " + relocatedViolations);
        }
    }

    @Test
    @DisplayName("the SQLite driver sits at the coordinates the code asks for")
    void driverCoordinatesMatch() throws IOException {
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            // DatabaseType.driverClass() names org.sqlite.JDBC; the class must actually be there.
            assertNotNull(jar.getEntry("org/sqlite/JDBC.class"),
                    "org/sqlite/JDBC.class must be present at its own coordinates");
            assertEquals(null, jar.getEntry("dev/lightlogin/libs/org/sqlite/JDBC.class"),
                    "org.sqlite must not be relocated");
            // The native libraries the driver extracts at runtime must sit alongside it.
            boolean hasNative = jar.stream().map(ZipEntry::getName)
                    .anyMatch(name -> name.startsWith("org/sqlite/native/")
                            && (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")));
            assertTrue(hasNative, "the SQLite native libraries must be present under org/sqlite/native/");
        }
    }

    @Test
    @DisplayName("bundled libraries do not leak into the server's shared namespace")
    void bundledLibrariesAreNamespaced() throws IOException {
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            Set<String> unnamespaced = new LinkedHashSet<>();
            jar.stream().map(ZipEntry::getName)
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> name.startsWith("org/slf4j/")
                            || name.startsWith("com/zaxxer/hikari/")
                            || name.startsWith("com/maxmind/")
                            || name.startsWith("jakarta/mail/"))
                    .forEach(unnamespaced::add);
            assertTrue(unnamespaced.isEmpty(),
                    "these libraries should have been relocated into dev/lightlogin/libs/: " + unnamespaced);
        }
    }

    @Test
    @DisplayName("every META-INF/services entry names a class that exists in the jar")
    void serviceFilesResolve() throws IOException {
        try (ZipFile jar = new ZipFile(shadedJar().toFile())) {
            Set<String> broken = new LinkedHashSet<>();
            jar.stream()
                    .filter(entry -> entry.getName().startsWith("META-INF/services/")
                            && !entry.isDirectory())
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
                        } catch (IOException e) {
                            broken.add(entry.getName() + " (unreadable: " + e.getMessage() + ')');
                        }
                    });
            assertTrue(broken.isEmpty(),
                    "META-INF/services entries point at classes that are not in the jar: " + broken);
        }
    }

    /** The shaded jar: the one that is not the pre-shade {@code original-} artifact. */
    private static Path shadedJar() throws IOException {
        Path target = Path.of("target");
        assertTrue(Files.isDirectory(target), "expected the module target directory at " + target.toAbsolutePath());
        try (Stream<Path> stream = Files.list(target)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().startsWith("original-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "no shaded jar in " + target.toAbsolutePath()
                                    + "; run `mvn verify` so the package phase runs before this test"));
        }
    }
}