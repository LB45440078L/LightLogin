package dev.lightlogin.core.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters.Builder;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Argon2id password hasher backed by BouncyCastle.
 *
 * <p>Argon2id is the Password Hashing Competition winner and the OWASP first choice: it is
 * memory-hard (resisting GPU/ASIC cracking), and its hybrid data-dependent/data-independent
 * addressing resists both side-channel and time-memory-tradeoff attacks. Each hash carries its
 * own random salt and its work factors in the encoded output.</p>
 *
 * <p>This class is thread-safe. Argon2 with a 64 MiB memory cost allocates roughly 64 MiB per
 * concurrent operation, so the caller is expected to bound concurrency (see the async executor in
 * the paper module) rather than call this from unbounded threads.</p>
 */
public final class Argon2idPasswordHasher implements PasswordHasher {

    private final Argon2Parameters parameters;
    private final Pepper pepper;
    private final SecureRandom random;

    public Argon2idPasswordHasher(Argon2Parameters parameters, Pepper pepper) {
        this(parameters, pepper, new SecureRandom());
    }

    Argon2idPasswordHasher(Argon2Parameters parameters, Pepper pepper, SecureRandom random) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.pepper = Objects.requireNonNull(pepper, "pepper");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Creates a hasher with the balanced profile and no pepper. */
    public static Argon2idPasswordHasher createDefault() {
        return new Argon2idPasswordHasher(Argon2Parameters.BALANCED, Pepper.NONE);
    }

    @Override
    public String hash(char[] password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = new byte[parameters.saltBytes()];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt, parameters);
        try {
            return PhcFormat.encode(PhcFormat.of(parameters, salt, derived));
        } finally {
            ConstantTime.wipe(derived);
        }
    }

    @Override
    public boolean verify(char[] password, String encoded) {
        Objects.requireNonNull(password, "password");
        if (encoded == null || !PhcFormat.looksLikeArgon2id(encoded)) {
            return false;
        }
        PhcFormat.Argon2Hash parsed;
        try {
            parsed = PhcFormat.decode(encoded);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] actual = derive(password, parsed.salt(), parsed.parameters());
        try {
            return ConstantTime.equals(parsed.hash(), actual);
        } finally {
            ConstantTime.wipe(actual);
        }
    }

    @Override
    public boolean needsRehash(String encoded) {
        if (encoded == null || !PhcFormat.looksLikeArgon2id(encoded)) {
            return true;
        }
        try {
            PhcFormat.Argon2Hash parsed = PhcFormat.decode(encoded);
            return parameters.isStrongerThan(parsed.parameters())
                    || parsed.version() != PhcFormat.VERSION_13;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Override
    public String algorithm() {
        return "argon2id";
    }

    public Argon2Parameters parameters() {
        return parameters;
    }

    private byte[] derive(char[] password, byte[] salt, Argon2Parameters params) {
        byte[] raw = Pepper.utf8(password);
        byte[] material = null;
        try {
            material = pepper.apply(raw);
            Builder builder = new Builder(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id)
                    .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(params.iterations())
                    .withMemoryAsKB(params.memoryKib())
                    .withParallelism(params.parallelism())
                    .withSalt(salt);
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(builder.build());
            byte[] out = new byte[params.hashBytes()];
            generator.generateBytes(material, out);
            return out;
        } finally {
            ConstantTime.wipe(raw);
            if (material != raw) {
                ConstantTime.wipe(material);
            }
        }
    }
}