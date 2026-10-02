package dev.lightlogin.core.policy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A small blocklist of the most common passwords, loaded once from the classpath.
 *
 * <p>Composition rules alone (length + character classes) are weak: {@code Password1!} satisfies
 * almost every policy yet appears in every cracking dictionary. A blocklist is the single most
 * effective complement, and loading it from a resource keeps the list updatable without a code
 * change. Lookups are case-insensitive and O(1).</p>
 */
public final class CommonPasswords {

    private static final String RESOURCE = "/dev/lightlogin/common-passwords.txt";
    private static final Set<String> WORDS = load();

    private CommonPasswords() {
    }

    private static Set<String> load() {
        Set<String> words = new HashSet<>(1024);
        try (InputStream in = CommonPasswords.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Collections.emptySet();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .forEach(words::add);
            }
        } catch (IOException e) {
            // A missing blocklist degrades gracefully; the policy still enforces composition rules.
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(words);
    }

    /** Whether the (case-insensitive) password appears in the blocklist. */
    public static boolean contains(char[] password) {
        if (WORDS.isEmpty()) {
            return false;
        }
        String candidate = new String(password).toLowerCase(Locale.ROOT);
        return WORDS.contains(candidate);
    }

    /** The number of loaded entries; used by diagnostics and tests. */
    public static int size() {
        return WORDS.size();
    }
}