package dev.lightlogin.paper.config;

import dev.lightlogin.core.config.ConfigSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Adapts Bukkit's {@link FileConfiguration} to the core's {@link ConfigSource}.
 *
 * <p>This is the only place the core's configuration model touches the server API, which is what
 * keeps the whole of the core testable without a server.</p>
 */
public final class BukkitConfigSource implements ConfigSource {

    private final FileConfiguration configuration;

    public BukkitConfigSource(FileConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public String getString(String path, String fallback) {
        String value = configuration.getString(path);
        return value == null ? fallback : value;
    }

    @Override
    public int getInt(String path, int fallback) {
        return configuration.isInt(path) ? configuration.getInt(path) : fallback;
    }

    @Override
    public long getLong(String path, long fallback) {
        return configuration.isLong(path) || configuration.isInt(path)
                ? configuration.getLong(path) : fallback;
    }

    @Override
    public double getDouble(String path, double fallback) {
        return configuration.isDouble(path) || configuration.isInt(path)
                ? configuration.getDouble(path) : fallback;
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return configuration.isBoolean(path) ? configuration.getBoolean(path) : fallback;
    }

    @Override
    public List<String> getStringList(String path) {
        return configuration.getStringList(path);
    }

    @Override
    public Set<String> getKeys(String path) {
        ConfigurationSection section = configuration.getConfigurationSection(path);
        return section == null ? Set.of() : section.getKeys(false);
    }

    @Override
    public boolean contains(String path) {
        return configuration.contains(path);
    }
}