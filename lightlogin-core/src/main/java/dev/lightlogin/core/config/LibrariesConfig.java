package dev.lightlogin.core.config;

import java.util.List;

/**
 * Settings for the runtime library loader.
 *
 * <p>The heavy dependencies — the JDBC drivers and the GeoIP reader — are not bundled in the plugin
 * jar. They are resolved at startup in this order: from the server's own classpath if it already
 * provides them, else from {@code plugins/LightLogin/libs/}, else downloaded from one of these
 * repositories and verified against a pinned SHA-256.</p>
 *
 * @param repositories         Maven repository base URLs, tried in order
 * @param autoDownload         whether the plugin may download a missing library
 * @param connectTimeoutMillis HTTP connect timeout for a download
 * @param readTimeoutMillis    HTTP read timeout for a download
 */
public record LibrariesConfig(
        List<String> repositories,
        boolean autoDownload,
        long connectTimeoutMillis,
        long readTimeoutMillis) {

    public static LibrariesConfig defaults() {
        return new LibrariesConfig(List.of("https://repo1.maven.org/maven2"), true, 20_000, 120_000);
    }

    public LibrariesConfig {
        repositories = List.copyOf(repositories == null || repositories.isEmpty()
                ? List.of("https://repo1.maven.org/maven2")
                : repositories);
        if (connectTimeoutMillis <= 0) {
            connectTimeoutMillis = 20_000;
        }
        if (readTimeoutMillis <= 0) {
            readTimeoutMillis = 120_000;
        }
    }
}