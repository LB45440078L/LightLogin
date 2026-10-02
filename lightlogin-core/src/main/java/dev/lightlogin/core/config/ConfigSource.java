package dev.lightlogin.core.config;

import java.util.List;
import java.util.Set;

/**
 * An abstraction over a hierarchical configuration file.
 *
 * <p>The core module must not depend on Bukkit's {@code FileConfiguration}, so configuration is
 * read through this narrow interface. The plugin module supplies an adapter over the server's YAML
 * implementation; tests supply a map-backed fake. Every accessor takes an explicit default so the
 * loader can be written once and behaves identically for a missing key, a wrong type and an empty
 * file.</p>
 */
public interface ConfigSource {

    String getString(String path, String fallback);

    int getInt(String path, int fallback);

    long getLong(String path, long fallback);

    double getDouble(String path, double fallback);

    boolean getBoolean(String path, boolean fallback);

    List<String> getStringList(String path);

    /** The immediate child keys of a section path; empty when the path is not a section. */
    Set<String> getKeys(String path);

    /** Whether a key is present at all (distinguishes "absent" from "set to default"). */
    boolean contains(String path);
}