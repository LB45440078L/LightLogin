package dev.lightlogin.paper.library;

/**
 * A third-party jar the plugin needs at runtime but does not bundle.
 *
 * <p>Carrying the SHA-256 here rather than trusting whatever the repository serves is the point:
 * the digest is the security boundary. A download that does not match it is discarded, and a jar
 * already sitting in {@code libs/} that does not match it is re-fetched, so a tampered or truncated
 * file cannot be loaded just because it has the expected name.</p>
 *
 * @param group      Maven group id
 * @param artifact   Maven artifact id
 * @param version    pinned version
 * @param sha256     lowercase hex SHA-256 of the jar
 * @param probeClass class used to detect whether the library is already on the server's classpath
 * @param purpose    short human-readable reason, used in log lines and error messages
 */
public record Library(String group, String artifact, String version, String sha256, String probeClass,
                      String purpose) {

    /** The jar's file name inside {@code libs/} and in the repository. */
    public String fileName() {
        return artifact + '-' + version + ".jar";
    }

    /** The path of the jar within a Maven repository. */
    public String repositoryPath() {
        return group.replace('.', '/') + '/' + artifact + '/' + version + '/' + fileName();
    }

    /** {@code group:artifact:version}, for log lines. */
    public String coordinates() {
        return group + ':' + artifact + ':' + version;
    }

    @Override
    public String toString() {
        return coordinates() + " (" + purpose + ')';
    }
}