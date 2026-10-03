package dev.lightlogin.paper.messages;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and renders the message file.
 *
 * <p>Reload mutates the existing {@link YamlConfiguration} in place rather than replacing it, so a
 * component that captured the service in its constructor still sees corrected text after a reload
 * — the failure mode where "the reload worked everywhere except the alerts" is designed out.</p>
 *
 * <p>Messages render to plain strings with {@code &} colour codes translated, and are sent through
 * {@link CommandSender}. That keeps the plugin on the Bukkit API alone: no Adventure types, so the
 * same jar runs on Spigot, Paper and any fork that keeps the Bukkit contract.</p>
 */
public final class MessageService {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration configuration;
    private volatile String prefix;

    public MessageService(JavaPlugin plugin, File file) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = Objects.requireNonNull(file, "file");
        this.configuration = new YamlConfiguration();
        reload();
    }

    /** Re-reads the file into the existing configuration object. */
    public void reload() {
        try {
            if (file.exists()) {
                configuration.load(file);
            } else {
                plugin.saveResource("messages.yml", false);
                configuration.load(file);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read messages.yml: " + e.getMessage());
        }
        this.prefix = configuration.getString("prefix", "");
    }

    /** The raw, unparsed message value, or an empty string when the key is absent. */
    public String raw(String key) {
        String value = configuration.getString(key);
        return value == null ? "" : value;
    }

    /** Whether a key exists in the message file. */
    public boolean has(String key) {
        return configuration.contains(key);
    }

    /** Every key present in the file (used by the message-key audit test). */
    public java.util.Set<String> keys() {
        return configuration.getKeys(true);
    }

    /** Renders a message as colour-translated lines, prefixing only the first line. */
    public List<String> render(String key, Map<String, String> placeholders) {
        List<String> lines = configuration.isList(key)
                ? configuration.getStringList(key)
                : List.of(raw(key));
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> rendered = new ArrayList<>(lines.size());
        boolean first = true;
        for (String line : lines) {
            if (line.isEmpty() && first) {
                first = false;
                continue;
            }
            String text = applyPlaceholders(line, placeholders);
            if (first) {
                text = applyPlaceholders(prefix, placeholders) + text;
                first = false;
            }
            rendered.add(colour(text));
        }
        return rendered;
    }

    /** Sends a message to a sender. */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        for (String line : render(key, placeholders)) {
            sender.sendMessage(line);
        }
    }

    /** Sends a message with no placeholders. */
    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /**
     * Translates {@code &} colour codes to the section sign.
     *
     * <p>The single place colour codes are interpreted, so every caller can pass raw configured text
     * and a literal ampersand survives.</p>
     */
    public static String colour(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    private static String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        String output = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    /** Exposes the prefix for callers that build their own lines. */
    public String prefix() {
        return prefix;
    }
}