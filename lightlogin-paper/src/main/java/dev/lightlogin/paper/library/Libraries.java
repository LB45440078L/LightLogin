package dev.lightlogin.paper.library;

import dev.lightlogin.core.config.DatabaseConfig;

import java.util.List;

/**
 * The pinned catalogue of runtime libraries.
 *
 * <p>Every entry carries the SHA-256 of the exact artifact, so an operator who allows downloads is
 * still protected if a mirror is compromised or a version is silently republished. Versions are
 * deliberately pinned rather than ranged: a login plugin's dependency set should not change because
 * a build ran on a different day.</p>
 *
 * <p>Bumping a version means replacing its digest too. The digests here were taken from the
 * artifacts in the local Maven repository.</p>
 */
public final class Libraries {

    private Libraries() {
    }

    /** SQLite, the default embedded backend. */
    public static final Library SQLITE = new Library(
            "org.xerial", "sqlite-jdbc", "3.53.4.0",
            "bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb",
            "org.sqlite.JDBC", "SQLite database driver");

    /** MariaDB / MySQL. */
    public static final Library MARIADB = new Library(
            "org.mariadb.jdbc", "mariadb-java-client", "3.5.10",
            "919b8c1c771d9ee3465811462f242c9543ab401e140c64988ddbf1d8abcb18b2",
            "org.mariadb.jdbc.Driver", "MariaDB/MySQL database driver");

    /** PostgreSQL. */
    public static final Library POSTGRESQL = new Library(
            "org.postgresql", "postgresql", "42.7.13",
            "6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93",
            "org.postgresql.Driver", "PostgreSQL database driver");

    /** slf4j API, which the JDBC drivers log through when the server does not provide it. */
    public static final Library SLF4J_API = new Library(
            "org.slf4j", "slf4j-api", "2.0.17",
            "7b751d952061954d5abfed7181c1f645d336091b679891591d63329c622eb832",
            "org.slf4j.Logger", "logging facade used by the JDBC drivers");

    /** GeoIP reader, used only when nation blocking is configured. */
    public static final Library GEOIP2 = new Library(
            "com.maxmind.geoip2", "geoip2", "5.2.0",
            "1d8524be0a8f8de5a740f55e64c591e2c73a9a07034a7879384dd2a1b9648867",
            "com.maxmind.geoip2.DatabaseReader", "MaxMind GeoIP2 reader");

    /** The MaxMind database format reader that geoip2 builds on. */
    public static final Library MAXMIND_DB = new Library(
            "com.maxmind.db", "maxmind-db", "4.2.0",
            "85ac1d4e7aff61dd274b723e403aae3f49ca68f23f931a5003953b3e908e2ccf",
            "com.maxmind.db.Reader", "MaxMind database format reader");

    /** JSON binding, required by geoip2. */
    public static final Library JACKSON_DATABIND = new Library(
            "com.fasterxml.jackson.core", "jackson-databind", "2.22.1",
            "7dcd7e53bec1f56c7ad278bd1ca0840bebcc595d61ce44d6a8439abb75b965b2",
            "com.fasterxml.jackson.databind.ObjectMapper", "JSON binding used by the GeoIP reader");

    /** Jackson's core, required by geoip2. */
    public static final Library JACKSON_CORE = new Library(
            "com.fasterxml.jackson.core", "jackson-core", "2.22.1",
            "941ff029bcdb93e83d209ce516c1a7fb8bbac07d0a2fa122f5bf194b2cd7b4f4",
            "com.fasterxml.jackson.core.JsonParser", "JSON streaming used by the GeoIP reader");

    /** Jackson annotations, required by geoip2. */
    public static final Library JACKSON_ANNOTATIONS = new Library(
            "com.fasterxml.jackson.core", "jackson-annotations", "2.22",
            "21ddb598807d3a51a876704eb979d9296e1c6a6f47ab1826ff88c6d6a127a2d0",
            "com.fasterxml.jackson.annotation.JsonProperty", "Jackson annotations");

    /** Jackson's JSR-310 module, required by geoip2. */
    public static final Library JACKSON_JSR310 = new Library(
            "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.22.1",
            "85da45b7e5e565418963ff8f0dcb184365b0557c15468933f4318dde5c3ce845",
            "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", "Jackson JSR-310 module");

    /** Everything the GeoIP reader needs. */
    public static final List<Library> GEOIP = List.of(
            GEOIP2, MAXMIND_DB, JACKSON_DATABIND, JACKSON_CORE, JACKSON_ANNOTATIONS, JACKSON_JSR310);

    /**
     * The driver for a configured backend, plus slf4j.
     *
     * @return the libraries needed to talk to that database
     */
    public static List<Library> forDatabase(DatabaseConfig.DatabaseType type) {
        return switch (type) {
            case SQLITE -> List.of(SQLITE);
            case MARIADB, MYSQL -> List.of(MARIADB, SLF4J_API);
            case POSTGRESQL -> List.of(POSTGRESQL, SLF4J_API);
        };
    }
}