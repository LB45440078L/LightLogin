package dev.lightlogin.paper.library;

import dev.lightlogin.core.config.LibrariesConfig;
import dev.lightlogin.core.port.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Resolves the third-party jars the plugin needs but does not bundle.
 *
 * <p>This is what keeps the plugin jar small. The JDBC drivers and the GeoIP reader are several
 * megabytes, and most servers already ship at least one of them, so bundling them is wasteful in
 * exactly the common case. Resolution is attempted in a fixed order:</p>
 *
 * <ol>
 *   <li><b>The server's own classpath.</b> Detected by loading the library's probe class. If the
 *       server already provides it, nothing is downloaded and nothing is added.</li>
 *   <li><b>{@code plugins/LightLogin/libs/}.</b> An operator can drop a jar there to install a
 *       library offline; the SHA-256 is still checked, so a tampered file is re-fetched (or
 *       rejected when downloads are off).</li>
 *   <li><b>A download</b> from the configured repositories, written to a temporary file, verified
 *       against the pinned digest, and only then moved into place.</li>
 * </ol>
 *
 * <p>The digest check happens on every path, including a jar that is already on disk, so a file
 * that was truncated or altered between restarts does not get loaded.</p>
 *
 * <p>Resolved jars are loaded through a {@link URLClassLoader} whose parent is the plugin's own
 * loader. That keeps the libraries off the plugin's compile path and out of the shaded jar, while
 * still letting them see the server classes they expect.</p>
 */
public final class LibraryManager {

    /** Where a library came from, for diagnostics and tests. */
    public enum Origin {
        /** Already visible on the parent class loader. */
        SERVER,
        /** Found in {@code libs/} with a matching digest. */
        CACHED,
        /** Downloaded and verified during this startup. */
        DOWNLOADED
    }

    /** The outcome of resolving one library. */
    public record Resolution(Library library, Origin origin, Path file) {
    }

    /** {@link URI#getScheme()} returns the scheme without its trailing colon. */
    private static final String FILE_SCHEME = "file";

    private final Path libsDirectory;
    private final LibrariesConfig config;
    private final Logger logger;
    private final HttpClient http;

    public LibraryManager(Path libsDirectory, LibrariesConfig config, Logger logger) {
        this.libsDirectory = Objects.requireNonNull(libsDirectory, "libsDirectory");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Resolves every library and returns a loader that can see all of them.
     *
     * @param parent    the plugin's own class loader
     * @param libraries the libraries to make available
     * @return a loader over the resolved jars, or {@code parent} itself when the server already
     *         provides every one of them (so no needless loaders are created)
     */
    public ClassLoader loaderFor(ClassLoader parent, List<Library> libraries) {
        List<Resolution> resolutions = resolveAll(parent, libraries);
        List<URL> urls = new ArrayList<>();
        for (Resolution resolution : resolutions) {
            if (resolution.file() != null) {
                try {
                    urls.add(resolution.file().toUri().toURL());
                } catch (IOException e) {
                    throw new StorageException("Could not reference " + resolution.library().coordinates(), e);
                }
            }
        }
        if (urls.isEmpty()) {
            return parent;
        }
        return new URLClassLoader(urls.toArray(URL[]::new), parent);
    }

    /**
     * Resolves every library without building a loader.
     *
     * @return one entry per library, in the order given; the {@code file} is null for a library the
     *         server already provides
     */
    public List<Resolution> resolveAll(ClassLoader parent, List<Library> libraries) {
        Objects.requireNonNull(parent, "parent");
        List<Resolution> resolutions = new ArrayList<>(libraries.size());
        for (Library library : libraries) {
            if (isProvided(parent, library)) {
                logger.info("Using the server's own copy of " + library.coordinates() + '.');
                resolutions.add(new Resolution(library, Origin.SERVER, null));
                continue;
            }
            resolutions.add(resolve(library));
        }
        return resolutions;
    }

    /**
     * Makes one library available, returning where it came from.
     *
     * @throws StorageException when it cannot be obtained, naming the reason and the remedy
     */
    public Resolution resolve(Library library) {
        Objects.requireNonNull(library, "library");
        Path target = libsDirectory.resolve(library.fileName());

        Optional<Path> cached = usable(target, library);
        if (cached.isPresent()) {
            logger.info("Using " + libsDirectory.getFileName() + '/' + library.fileName()
                    + " for " + library.coordinates() + '.');
            return new Resolution(library, Origin.CACHED, cached.get());
        }

        if (Files.isRegularFile(target)) {
            logger.warning("Ignoring " + libsDirectory.getFileName() + '/' + library.fileName()
                    + ": its SHA-256 does not match the pinned digest for " + library.coordinates() + '.');
        }

        if (!config.autoDownload()) {
            throw new StorageException(missingMessage(library,
                    "automatic downloads are disabled (libraries.auto-download is false)"));
        }

        try {
            Files.createDirectories(libsDirectory);
        } catch (IOException e) {
            throw new StorageException("Could not create " + libsDirectory, e);
        }

        List<String> failures = new ArrayList<>();
        for (String repository : config.repositories()) {
            String url = repository.endsWith("/") ? repository + library.repositoryPath()
                    : repository + '/' + library.repositoryPath();
            try {
                Path downloaded = download(url, library, target);
                logger.info("Downloaded " + library.coordinates() + " for " + library.purpose() + '.');
                return new Resolution(library, Origin.DOWNLOADED, downloaded);
            } catch (StorageException e) {
                failures.add(repository + " -> " + e.getMessage());
            }
        }
        throw new StorageException(missingMessage(library,
                "no repository yielded a file with the expected digest [" + String.join("; ", failures) + ']'));
    }

    /** Whether the parent loader can already load the library's probe class. */
    private static boolean isProvided(ClassLoader parent, Library library) {
        try {
            Class.forName(library.probeClass(), false, parent);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            // Absent, or present but unusable (a missing transitive class). Either way it cannot be
            // relied on, so fall through to resolving it ourselves.
            return false;
        }
    }

    /** A cached file that exists and matches its digest. */
    private Optional<Path> usable(Path target, Library library) {
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return sha256(target).equals(library.sha256()) ? Optional.of(target) : Optional.empty();
        } catch (IOException e) {
            logger.warning("Could not read " + target + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches a jar to a temporary file beside the target, verifies it, and only then moves it into
     * place. Writing straight to the target would leave a half-written file that the next startup
     * would have to reject.
     */
    private Path download(String url, Library library, Path target) {
        String scheme = URI.create(url).getScheme();
        Path temporary;
        try {
            temporary = Files.createTempFile(libsDirectory, library.fileName(), ".part");
        } catch (IOException e) {
            throw new StorageException("Could not create a temporary file in " + libsDirectory, e);
        }
        try {
            if (FILE_SCHEME.equals(scheme)) {
                copyLocal(URI.create(url), temporary);
            } else if ("http".equals(scheme) || "https".equals(scheme)) {
                fetch(url, temporary);
            } else {
                throw new StorageException("unsupported repository scheme '" + scheme + '\'');
            }

            String actual = sha256(temporary);
            if (!actual.equals(library.sha256())) {
                throw new StorageException("digest mismatch: expected " + library.sha256()
                        + " but got " + actual);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Best effort; a leftover .part file is harmless and gets pruned by name on retry.
            }
        }
    }

    /** Copies from a {@code file:} repository, which is how an offline mirror is configured. */
    private static void copyLocal(URI uri, Path destination) throws IOException {
        Path source = Path.of(uri);
        if (!Files.isRegularFile(source)) {
            throw new StorageException("no such file: " + source);
        }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private void fetch(String url, Path destination) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(config.readTimeoutMillis()))
                .header("User-Agent", "LightLogin")
                .GET()
                .build();
        try {
            HttpResponse<Path> response = http.send(request,
                    HttpResponse.BodyHandlers.ofFile(destination));
            if (response.statusCode() / 100 != 2) {
                throw new StorageException("HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("download interrupted", e);
        }
    }

    /** Lowercase hex SHA-256 of a file, streamed so a large jar does not sit in memory. */
    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String missingMessage(Library library, String reason) {
        return "The " + library.purpose() + " (" + library.coordinates() + ") is required but is not "
                + "available: " + reason + ". Either let the server provide it, place "
                + library.fileName() + " in plugins/LightLogin/libs/, or enable "
                + "libraries.auto-download in config.yml.";
    }

    /** The directory jars are cached in. */
    public Path libsDirectory() {
        return libsDirectory;
    }
}