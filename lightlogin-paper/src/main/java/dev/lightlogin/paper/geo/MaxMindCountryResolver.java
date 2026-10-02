package dev.lightlogin.paper.geo;

import dev.lightlogin.core.geo.CountryResolver;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Locale;

/**
 * MaxMind GeoLite2 backed country resolution for nation blocking.
 *
 * <p>Opens the database read-only and keeps it mapped for the plugin's lifetime, which is the
 * supported usage pattern. If the file is absent or unreadable the resolver degrades to
 * {@link CountryResolver#DISABLED}, so a misconfigured path disables nation blocking rather than
 * breaking startup.</p>
 *
 * <p>Unlike the rest of the plugin this class reaches the GeoIP reader <em>reflectively</em>. The
 * reader and its Jackson dependencies are about three megabytes and are only used when nation
 * blocking is configured, so {@code lightlogin-paper} does not compile against them: they are
 * resolved at startup like the JDBC drivers (see
 * {@link dev.lightlogin.paper.library.LibraryManager}) and handed to {@link #open} as a class
 * loader. The reflective handles are resolved once and reused, so the per-connection cost is the
 * same as a direct call.</p>
 */
public final class MaxMindCountryResolver implements CountryResolver {

    private final Object reader;
    private final Method countryMethod;
    private final Method closeMethod;
    private final Method responseCountryMethod;
    private final Method countryIsoCodeMethod;
    private final JavaPlugin plugin;

    private MaxMindCountryResolver(Object reader, Method countryMethod, Method closeMethod,
                                   Method responseCountryMethod, Method countryIsoCodeMethod,
                                   JavaPlugin plugin) {
        this.reader = reader;
        this.countryMethod = countryMethod;
        this.closeMethod = closeMethod;
        this.responseCountryMethod = responseCountryMethod;
        this.countryIsoCodeMethod = countryIsoCodeMethod;
        this.plugin = plugin;
    }

    /**
     * Opens the database if it exists and the reader can be reached.
     *
     * @param plugin       the owning plugin, for logging and resolving a relative path
     * @param databasePath the {@code .mmdb} file, absolute or relative to the data folder
     * @param loader       a loader that can see {@code com.maxmind.geoip2.DatabaseReader}
     * @return a working resolver, or {@link CountryResolver#DISABLED}
     */
    public static CountryResolver open(JavaPlugin plugin, String databasePath, ClassLoader loader) {
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
            Class<?> builderType = Class.forName("com.maxmind.geoip2.DatabaseReader$Builder", true, loader);
            Class<?> readerType = Class.forName("com.maxmind.geoip2.DatabaseReader", true, loader);
            Class<?> responseType = Class.forName("com.maxmind.geoip2.model.CountryResponse", true, loader);
            Class<?> countryType = Class.forName("com.maxmind.geoip2.model.Country", true, loader);

            Object builder = builderType.getConstructor(File.class).newInstance(file);
            Object reader = builderType.getMethod("build").invoke(builder);

            return new MaxMindCountryResolver(reader,
                    readerType.getMethod("country", InetAddress.class),
                    readerType.getMethod("close"),
                    accessor(responseType, "country", "getCountry"),
                    accessor(countryType, "isoCode", "getIsoCode"),
                    plugin);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("The GeoIP reader is not available (" + e.getMessage()
                    + "); country blocking is disabled.");
            return CountryResolver.DISABLED;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Could not open the GeoIP database: " + describe(e));
            return CountryResolver.DISABLED;
        }
    }

    /**
     * Finds an accessor, preferring the record-style name and falling back to the bean-style one.
     *
     * <p>The GeoIP models moved to records, so which name exists depends on the reader version;
     * accepting either keeps this adapter working across an upgrade.</p>
     */
    private static Method accessor(Class<?> type, String recordStyle, String beanStyle)
            throws NoSuchMethodException {
        try {
            return type.getMethod(recordStyle);
        } catch (NoSuchMethodException ignored) {
            return type.getMethod(beanStyle);
        }
    }

    @Override
    public String countryOf(String ip) {
        try {
            InetAddress address = InetAddress.ofLiteral(ip);
            Object response = countryMethod.invoke(reader, address);
            Object country = responseCountryMethod.invoke(response);
            Object code = countryIsoCodeMethod.invoke(country);
            return code == null ? null : code.toString().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            // Unknown address, private range, or not in the database: treat as unknown rather than
            // failing the connection.
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
            closeMethod.invoke(reader);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not close the GeoIP database: " + describe(e));
        }
    }

    private static String describe(Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException ite && ite.getCause() != null
                ? ite.getCause()
                : throwable;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}