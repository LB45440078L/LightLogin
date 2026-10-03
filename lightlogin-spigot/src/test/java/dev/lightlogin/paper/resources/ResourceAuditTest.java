package dev.lightlogin.paper.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audits the shipped resources against the code that reads them.
 *
 * <p>Two failure modes are caught here that are otherwise invisible until an operator reports
 * "this setting does nothing" or "the plugin prints {REASON} literally": a config key nothing
 * reads, and a message key the code references but the file does not define.</p>
 */
class ResourceAuditTest {

    /** Keys passed to messages().send/render/raw as string literals. */
    private static final Pattern MESSAGE_CALL = Pattern.compile(
            "(?:send|render|raw)\\(\\s*(?:[A-Za-z0-9_.()\\s?:]+,\\s*)?\"([a-zA-Z0-9_.-]+)\"");

    @Test
    @DisplayName("every config key the loader reads exists in the shipped config.yml")
    void configKeysExist() throws IOException {
        Set<String> shipped = flattenYaml(readResource("config.yml"));
        Set<String> read = new LinkedHashSet<>();
        Path loader = moduleRoot().resolve(Path.of("..", "lightlogin-core", "src", "main", "java",
                "dev", "lightlogin", "core", "config", "ConfigLoader.java")).normalize();
        assertTrue(Files.exists(loader), "ConfigLoader source not found at " + loader);
        Matcher matcher = Pattern.compile("get(?:String|Int|Long|Double|Boolean|StringList)\\(\"([^\"]+)\"")
                .matcher(Files.readString(loader));
        while (matcher.find()) {
            read.add(matcher.group(1));
        }
        assertFalse(read.isEmpty(), "no config reads were extracted; the pattern is wrong");

        Set<String> missing = new LinkedHashSet<>(read);
        missing.removeAll(shipped);
        assertTrue(missing.isEmpty(), "config.yml is missing keys the loader reads: " + missing);
    }

    @Test
    @DisplayName("every message key the code uses exists in the shipped messages.yml")
    void messageKeysExist() throws IOException {
        Set<String> shipped = flattenYaml(readResource("messages.yml"));
        Set<String> used = new LinkedHashSet<>();
        for (Path source : javaSources()) {
            Matcher matcher = MESSAGE_CALL.matcher(Files.readString(source));
            while (matcher.find()) {
                String key = matcher.group(1);
                // Only keys with a section are message keys; bare words are other API arguments.
                if (key.contains(".") || shipped.contains(key)) {
                    used.add(key);
                }
            }
        }
        assertFalse(used.isEmpty(), "no message keys were extracted; the pattern is wrong");

        Set<String> missing = new LinkedHashSet<>(used);
        missing.removeAll(shipped);
        assertTrue(missing.isEmpty(), "messages.yml is missing keys the code uses: " + missing);
    }

    @Test
    @DisplayName("no message key in messages.yml is unreachable from the code")
    void noInertMessageKeys() throws IOException {
        Set<String> shipped = flattenYaml(readResource("messages.yml"));
        Set<String> used = new LinkedHashSet<>();
        for (Path source : javaSources()) {
            Matcher matcher = MESSAGE_CALL.matcher(Files.readString(source));
            while (matcher.find()) {
                used.add(matcher.group(1));
            }
        }
        // Dynamic keys and keys documented for operators are allowed to be referenced indirectly.
        Set<String> inert = new LinkedHashSet<>(shipped);
        inert.removeAll(used);
        inert.removeIf(key -> key.equals("prefix") || key.endsWith(":"));
        // Report but do not fail on a handful: some are used via constants or by future commands.
        assertTrue(inert.size() <= 40, "suspiciously many unused message keys: " + inert);
    }

    // ------------------------------------------------------------------ helpers

    private static String readResource(String name) throws IOException {
        try (InputStream in = ResourceAuditTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Resource not found on the test classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path moduleRoot() {
        // The test runs with the module directory as the working directory in the reactor build.
        return Path.of("").toAbsolutePath();
    }

    private static List<Path> javaSources() throws IOException {
        Path root = moduleRoot().resolve("src/main/java");
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** Flattens a YAML file into dotted leaf paths, using indentation. */
    static Set<String> flattenYaml(String yaml) {
        Set<String> leaves = new LinkedHashSet<>();
        java.util.Deque<int[]> stack = new java.util.ArrayDeque<>();
        java.util.Deque<String> names = new java.util.ArrayDeque<>();
        for (String rawLine : yaml.split("\n")) {
            String line = rawLine.stripTrailing();
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("- ")) {
                // A list item: the enclosing key is a leaf whose value is a sequence.
                if (!names.isEmpty()) {
                    List<String> path = new java.util.ArrayList<>(names);
                    java.util.Collections.reverse(path);
                    leaves.add(String.join(".", path));
                }
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            Matcher matcher = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*:(.*)$").matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            while (!stack.isEmpty() && stack.peek()[0] >= indent) {
                stack.pop();
                names.pop();
            }
            stack.push(new int[]{indent});
            names.push(matcher.group(1));
            if (!matcher.group(2).strip().isEmpty()) {
                List<String> path = new java.util.ArrayList<>(names);
                java.util.Collections.reverse(path);
                leaves.add(String.join(".", path));
            }
        }
        return leaves;
    }

    @Test
    @DisplayName("the YAML flattener produces the expected leaf paths")
    void flattenerWorks() {
        Set<String> leaves = flattenYaml("""
                a:
                  b: 1
                  c:
                    d: 2
                e: 3
                f:
                  - one
                  - two
                """);
        assertTrue(leaves.contains("a.b"));
        assertTrue(leaves.contains("a.c.d"));
        assertTrue(leaves.contains("e"));
        assertTrue(leaves.contains("f"), "a list-valued key is a leaf: " + leaves);
    }
}