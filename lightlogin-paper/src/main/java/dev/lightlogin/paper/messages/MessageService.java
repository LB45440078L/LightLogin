package dev.lightlogin.paper.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.audience.Audience;
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
 * <p>Colour codes are translated with Adventure's ampersand serializer, so a literal ampersand is
 * preserved except where it is followed by a colour character.</p>
 */
public final class MessageService {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

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

    /** Renders a message as a list of components, prefixing only the first line. */
    public List<Component> render(String key, Map<String, String> placeholders) {
        List<String> lines = configuration.isList(key)
                ? configuration.getStringList(key)
                : List.of(raw(key));
        if (lines.isEmpty()) {
            return List.of();
        }
        List<Component> components = new ArrayList<>(lines.size());
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
            components.add(SERIALIZER.deserialize(text));
        }
        return components;
    }

    /** Sends a message to an audience. */
    public void send(Audience audience, String key, Map<String, String> placeholders) {
        for (Component component : render(key, placeholders)) {
            audience.sendMessage(component);
        }
    }

    /** Sends a message with no placeholders. */
    public void send(Audience audience, String key) {
        send(audience, key, Map.of());
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