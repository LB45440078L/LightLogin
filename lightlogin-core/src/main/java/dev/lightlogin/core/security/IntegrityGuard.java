package dev.lightlogin.core.security;

import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Best-effort tamper and instrumentation detection.
 *
 * <p>Java cannot sandbox a plugin that shares the JVM, and this class does not pretend otherwise.
 * What it does is make tampering <em>detectable</em>: it records whether the JVM was started with a
 * {@code -javaagent} (the usual vector for bytecode-rewriting attacks against a login plugin),
 * exposes the loaded agents for an operator to inspect, and can fingerprint a class's bytecode so a
 * later comparison catches on-disk modification.</p>
 *
 * <p>The honest position, stated in the documentation as well: the only complete isolation is a
 * separate JVM or an operating-system sandbox. This guard raises the cost and the visibility of an
 * attack, it does not make one impossible.</p>
 */
public final class IntegrityGuard {

    private final List<String> agents;
    private final boolean instrumented;

    private IntegrityGuard(List<String> agents, boolean instrumented) {
        this.agents = List.copyOf(agents);
        this.instrumented = instrumented;
    }

    /** Inspects the current JVM. */
    public static IntegrityGuard inspect() {
        List<String> agents = new ArrayList<>();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-javaagent") || arg.startsWith("-agentlib") || arg.startsWith("-agentpath")) {
                agents.add(arg);
            }
        }
        return new IntegrityGuard(agents, !agents.isEmpty());
    }

    /** Whether the JVM was launched with an instrumentation agent. */
    public boolean isInstrumented() {
        return instrumented;
    }

    /** The agent arguments observed at boot. */
    public List<String> agents() {
        return agents;
    }

    /**
     * A warning suitable for the console, or empty when the JVM is clean.
     */
    public List<String> warnings() {
        if (!instrumented) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        out.add("This JVM was started with an instrumentation agent:");
        agents.forEach(agent -> out.add("  " + agent));
        out.add("Agents can rewrite classes at load time. If you did not install these yourself,");
        out.add("treat the authentication data on this server as potentially compromised.");
        return List.copyOf(out);
    }

    /** SHA-256 of a class's bytecode, for on-disk tamper checks. */
    public static String fingerprint(Class<?> type) {
        try {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (var in = type.getResourceAsStream(resource)) {
                if (in == null) {
                    return "";
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
                return HexFormat.of().formatHex(digest.digest());
            }
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            return "";
        }
    }
}