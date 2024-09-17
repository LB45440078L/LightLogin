/*
 * LightLogin - Optimised and Safe SpigotMC Software for Authentication
 *     Copyright © 2024  CMarco
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.cmarco.lightlogin.encrypt;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class used to encrypt important data
 * using the Argon2 hashing algorithm.
 */
public final class Argon2Utilities {

    public static boolean debug = false;

    private Argon2Utilities() {
        throw new RuntimeException("You may not instantiate this utility class.");
    }

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a secure salt bytes with defined length.
     *
     * @param bytes The amount of bytes.
     * @return The array.
     */
    public static byte[] generateSaltByte(final int bytes) {
        final byte[] salt = new byte[bytes];
        secureRandom.nextBytes(salt);
        return salt;
    }

    private static final int ITERATIONS = 4; // 16
    private static final int MEMORY_LIMIT = 65336;
    private static final int HASH_LENGTH = 32; // 64
    private static final int PARALLELISM = 4;

    /**
     * A function to encrypt a string in Argon2 and encode it using Base64.
     * This functions adds a safety feature and needs to be used carefully.
     * 16 bytes used, hash length of 32.
     *
     * @param password The plaintext password input.
     * @param salt a salt to use.
     * @return Hashed and encoded password.
     */
    @NotNull
    public static String encryptArgon2(@NotNull final String password, final byte[] salt) {
        final long startTime = System.currentTimeMillis();
        final Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_LIMIT)
                .withParallelism(PARALLELISM)
                .withSalt(salt);
        final Argon2BytesGenerator generator = new Argon2BytesGenerator();

        generator.init(builder.build());
        final byte[] result = new byte[HASH_LENGTH];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), result, 0x00, result.length);
        final long endTime = System.currentTimeMillis();
        if (debug) {
            System.out.printf("HASH IMPACT (ms) = %.3f%n", ((double) (endTime - startTime)) / 1E3);
        }
        return Base64.getEncoder().encodeToString(result);
    }

    /**
     * A function to encrypt a string in Argon2 and encode it using Base64.
     * This functions adds a safety feature and needs to be used carefully.
     * 16 bytes used, hash length of 32.
     * Uses random salt.
     *
     * @param password The plaintext password input.
     * @return Hashed and encoded password.
     */
    public static String encryptArgon2(@NotNull final String password) {
        return encryptArgon2(password, generateSaltByte(0x10));
    }

}
