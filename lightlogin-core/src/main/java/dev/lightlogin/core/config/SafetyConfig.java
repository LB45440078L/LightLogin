package dev.lightlogin.core.config;

import java.util.List;

/**
 * Network safety settings: per-IP limits, IP bans and country (nation) blocking.
 *
 * @param maxPlayersPerIp        simultaneous authenticated players per address (0 = unlimited)
 * @param maxRegistrationsPerIp  accounts registrable per address (0 = unlimited)
 * @param ipBansEnabled          whether IP bans are enforced
 * @param autoBanOnBruteForce    whether repeated failures auto-ban the address
 * @param bruteForceBanMillis    auto-ban duration
 * @param countryBlockingEnabled whether nation blocking is active
 * @param blockedCountries       ISO-3166 alpha-2 codes to refuse
 * @param allowedCountries       when non-empty, only these codes are allowed
 * @param geoIpDatabase          path to a MaxMind GeoLite2 country database
 * @param whitelistedIps         addresses exempt from all IP checks
 */
public record SafetyConfig(
        int maxPlayersPerIp,
        int maxRegistrationsPerIp,
        boolean ipBansEnabled,
        boolean autoBanOnBruteForce,
        long bruteForceBanMillis,
        boolean countryBlockingEnabled,
        List<String> blockedCountries,
        List<String> allowedCountries,
        String geoIpDatabase,
        List<String> whitelistedIps) {

    public SafetyConfig {
        if (maxPlayersPerIp < 0) {
            maxPlayersPerIp = 0;
        }
        if (maxRegistrationsPerIp < 0) {
            maxRegistrationsPerIp = 0;
        }
        if (bruteForceBanMillis < 0) {
            bruteForceBanMillis = 3_600_000L;
        }
        blockedCountries = List.copyOf(blockedCountries == null ? List.of() : blockedCountries);
        allowedCountries = List.copyOf(allowedCountries == null ? List.of() : allowedCountries);
        whitelistedIps = List.copyOf(whitelistedIps == null ? List.of() : whitelistedIps);
        geoIpDatabase = geoIpDatabase == null ? "" : geoIpDatabase;
    }

    public static SafetyConfig defaults() {
        return new SafetyConfig(3, 2, true, true, 3_600_000L, false, List.of(), List.of(), "", List.of());
    }
}