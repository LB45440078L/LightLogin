package dev.lightlogin.core.config;

import dev.lightlogin.core.captcha.CaptchaEngine;
import dev.lightlogin.core.crypto.Argon2Parameters;
import dev.lightlogin.core.support.MapConfigSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    @DisplayName("an empty source yields the documented defaults")
    void defaults() {
        LightLoginConfig config = ConfigLoader.load(MapConfigSource.empty());
        LightLoginConfig expected = LightLoginConfig.defaults();

        assertEquals(expected.database().type(), config.database().type());
        assertEquals(expected.security().maxFailedAttempts(), config.security().maxFailedAttempts());
        assertEquals(expected.login().timeoutMillis(), config.login().timeoutMillis());
        assertFalse(config.web().enabled());
        assertTrue(config.web().isLoopbackBind());
    }

    @Test
    @DisplayName("explicit values override the defaults")
    void overrides() {
        LightLoginConfig config = ConfigLoader.load(MapConfigSource.empty()
                .put("language", "fr")
                .put("database.type", "POSTGRESQL")
                .put("database.host", "db.internal")
                .put("database.port", 5432)
                .put("database.name", "auth")
                .put("security.session-ttl-millis", 3_600_000L)
                .put("login.timeout-millis", 30_000L)
                .put("captcha.enabled", false)
                .put("web-panel.enabled", true)
                .put("web-panel.bind-address", "0.0.0.0")
                .put("web-panel.port", 9000)
                .put("void-world.enabled", true)
                .put("void-world.mode", "THE_END"));

        assertEquals("fr", config.language());
        assertEquals(DatabaseConfig.DatabaseType.POSTGRESQL, config.database().type());
        assertEquals("db.internal", config.database().host());
        assertEquals(5432, config.database().port());
        assertEquals(3_600_000L, config.security().sessionTtlMillis());
        assertEquals(30_000L, config.login().timeoutMillis());
        assertFalse(config.captcha().enabled());
        assertTrue(config.web().enabled());
        assertEquals(9000, config.web().port());
        assertFalse(config.web().isLoopbackBind());
        assertTrue(config.voidWorld().enabled());
        assertTrue(config.voidWorld().endStyleVoid());
    }

    @Test
    @DisplayName("unsafe Argon2 parameters are clamped up to the OWASP floor")
    void argonClamped() {
        LightLoginConfig config = ConfigLoader.load(MapConfigSource.empty()
                .put("security.argon2.memory-kib", 4096)
                .put("security.argon2.iterations", 1));
        assertEquals(Argon2Parameters.FLOOR.memoryKib(), config.security().argon().memoryKib());
        assertEquals(Argon2Parameters.FLOOR.iterations(), config.security().argon().iterations());
    }

    @Test
    @DisplayName("an unknown database type falls back to SQLite")
    void unknownDatabase() {
        LightLoginConfig config = ConfigLoader.load(MapConfigSource.empty().put("database.type", "ORACLE"));
        assertEquals(DatabaseConfig.DatabaseType.SQLITE, config.database().type());
    }

    @Test
    @DisplayName("captcha mode parses case-insensitively and falls back on nonsense")
    void captchaMode() {
        assertEquals(CaptchaEngine.Mode.PROOF_OF_WORK,
                ConfigLoader.load(MapConfigSource.empty().put("captcha.mode", "proof_of_work")).captcha().mode());
        assertEquals(CaptchaEngine.Mode.ARITHMETIC,
                ConfigLoader.load(MapConfigSource.empty().put("captcha.mode", "nonsense")).captcha().mode());
    }

    @Test
    @DisplayName("country codes are normalised to upper case")
    void countryCodes() {
        LightLoginConfig config = ConfigLoader.load(MapConfigSource.empty()
                .put("safety.country-blocking.enabled", true)
                .put("safety.country-blocking.blocked", List.of("ru", "cn")));
        assertTrue(config.safety().countryBlockingEnabled());
        assertEquals(List.of("RU", "CN"), config.safety().blockedCountries());
    }

    @Test
    @DisplayName("password policy special characters are parsed from a list")
    void passwordPolicySpecials() {
        LightLoginConfig config = ConfigLoader.load(MapConfigSource.empty()
                .put("password-policy.allowed-special", List.of("!", "@"))
                .put("password-policy.min-special", 2));
        assertEquals(2, config.security().passwordPolicy().allowedSpecial().size());
        assertTrue(config.security().passwordPolicy().allowedSpecial().contains('@'));
    }

    @Test
    @DisplayName("a configuration version is carried through")
    void configVersion() {
        assertEquals(7, ConfigLoader.load(MapConfigSource.empty().put("config-version", 7)).configVersion());
    }
}