package dev.lightlogin.core.geo;

/**
 * Resolves the ISO-3166 alpha-2 country of an IP address.
 *
 * <p>An interface so that country blocking degrades gracefully: without a GeoIP database the
 * plugin installs {@link #DISABLED}, which reports an unknown country and never blocks, rather than
 * failing startup.</p>
 */
public interface CountryResolver {

    /** A resolver that knows nothing and blocks nothing. */
    CountryResolver DISABLED = ip -> null;

    /**
     * @return the upper-case country code, or {@code null} when unknown
     */
    String countryOf(String ip);

    default boolean isAvailable() {
        return this != DISABLED;
    }
}