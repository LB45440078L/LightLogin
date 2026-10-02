package dev.lightlogin.core.crypto;

import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encoder and parser for the PHC string format of Argon2id hashes:
 *
 * <pre>$argon2id$v=19$m=65536,t=3,p=1$&lt;salt&gt;$&lt;hash&gt;</pre>
 *
 * <p>Storing the parameters alongside the digest is what makes a stored hash self-describing: the
 * salt can never be lost and parameters can be raised over time without invalidating old hashes.
 * This directly fixes the original implementation, whose convenience overload generated a random
 * salt and then discarded it.</p>
 */
public final class PhcFormat {

    private static final Pattern PATTERN = Pattern.compile(
            "^\\$argon2id\\$v=(\\d+)\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)$");

    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    /** Argon2 version 19 (0x13), the current specification version. */
    public static final int VERSION_13 = 0x13;

    private PhcFormat() {
    }

    /**
     * A decoded Argon2id hash: the work factors, salt and derived key.
     *
     * @param version     Argon2 version (19 for v1.3)
     * @param parameters  the work factors recovered from the encoded string
     * @param salt        the random salt
     * @param hash        the derived key
     */
    public record Argon2Hash(int version, Argon2Parameters parameters, byte[] salt, byte[] hash) {

        public Argon2Hash {
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(salt, "salt");
            Objects.requireNonNull(hash, "hash");
        }
    }

    /** Encodes a hash into its PHC string form. */
    public static String encode(Argon2Hash hash) {
        Objects.requireNonNull(hash, "hash");
        Argon2Parameters p = hash.parameters();
        return "$argon2id$v=" + hash.version()
                + "$m=" + p.memoryKib() + ",t=" + p.iterations() + ",p=" + p.parallelism()
                + "$" + ENCODER.encodeToString(hash.salt())
                + "$" + ENCODER.encodeToString(hash.hash());
    }

    /** Builds a v1.3 hash from parameters, salt and derived key. */
    public static Argon2Hash of(Argon2Parameters parameters, byte[] salt, byte[] hash) {
        return new Argon2Hash(VERSION_13, parameters, salt, hash);
    }

    /**
     * Parses a PHC string.
     *
     * @throws IllegalArgumentException when the string is not a well-formed Argon2id hash
     */
    public static Argon2Hash decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        Matcher matcher = PATTERN.matcher(encoded);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a valid argon2id PHC string");
        }
        try {
            int version = Integer.parseInt(matcher.group(1));
            int memory = Integer.parseInt(matcher.group(2));
            int iterations = Integer.parseInt(matcher.group(3));
            int parallelism = Integer.parseInt(matcher.group(4));
            byte[] salt = DECODER.decode(matcher.group(5));
            byte[] hash = DECODER.decode(matcher.group(6));
            Argon2Parameters parameters =
                    new Argon2Parameters(memory, iterations, parallelism, salt.length, hash.length);
            return new Argon2Hash(version, parameters, salt, hash);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Corrupt argon2id PHC string", e);
        }
    }

    /** Reports whether a string looks like an Argon2id PHC hash, without fully parsing it. */
    public static boolean looksLikeArgon2id(String encoded) {
        return encoded != null && encoded.startsWith("$argon2id$");
    }
}