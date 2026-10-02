package dev.lightlogin.paper.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import dev.lightlogin.core.geo.CountryResolver;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.InetAddress;
import java.util.Locale;

/**
 * MaxMind GeoLite2 backed country resolution for nation blocking.
 *
 * <p>Opens the database read-only and keeps it mapped for the plugin's lifetime, which is the
 * supported usage pattern. If the file is absent or unreadable the resolver degrades to
 * {@link CountryResolver#DISABLED}, so a misconfigured path disables nation blocking rather than
 * breaking startup.</p>
 */
public final class MaxMindCountryResolver implements CountryResolver {

    private final DatabaseReader reader;
    private final JavaPlugin plugin;

    private MaxMindCountryResolver(DatabaseReader reader, JavaPlugin plugin) {
        this.reader = reader;
        this.plugin = plugin;
    }

    /**
     * Opens the database if it exists.
     *
     * @return a working resolver, or {@link CountryResolver#DISABLED}
     */
    public static CountryResolver open(JavaPlugin plugin, String databasePath) {
        if (databasePath == null || databasePath.isBlank()) {
            return CountryResolver.DISABLED;
        }
        File file = new File(databasePath);
        if (!file.isAbsolute()) {
            file = new File(plugin.getDataFolder(), databasePath);
        }
        if (!file.isFile()) {
            plugin.getLogger().warning("GeoIP database not found at " + file
                    + "; country blocking is disabled.");
            return CountryResolver.DISABLED;
        }
        try {
            return new MaxMindCountryResolver(new DatabaseReader.Builder(file).build(), plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not open the GeoIP database: " + e.getMessage());
            return CountryResolver.DISABLED;
        }
    }

    @Override
    public String countryOf(String ip) {
        try {
            InetAddress address = InetAddress.ofLiteral(ip);
            CountryResponse response = reader.country(address);
            return response.country().isoCode().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            // Unknown address, private range, or not in the database: treat as unknown.
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /** Closes the database. */
    public void close() {
        try {
            reader.close();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not close the GeoIP database: " + e.getMessage());
        }
    }
}