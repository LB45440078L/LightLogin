package dev.lightlogin.paper.log;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * Console output helpers.
 *
 * <p>Coloured lines are sent through the server's {@link CommandSender} console, which is what
 * understands {@code §} codes; the logger is only a fallback because it would print the raw glyphs.
 * The fallback exists because {@code getConsoleSender()} throws before the server is fully up, and
 * losing a startup line entirely is worse than losing its colour.</p>
 *
 * <p>The banner is deliberately plain ASCII: block-drawing characters are exactly what older
 * console fonts cannot render, and a banner that shows as mojibake is worse than no banner.</p>
 */
public final class ConsoleBanner {

    private static final char SECTION = '\u00A7';

    /**
     * The wordmark.
     *
     * <p>Every line is exactly the same length and none is over 80 columns wide, so the block cannot
     * shear. An earlier revision had doubled backslashes, which pushed the descender of the "g" two
     * columns to the right and broke the art; the equal-width assertion in the test suite now makes
     * that class of mistake a build failure.</p>
     *
     * <p>Generated with {@code pyfiglet -f standard LightLogin} rather than drawn by hand.</p>
     */
    private static final String[] BANNER = {
            "",
            "   _     _       _     _   _                _       ",
            "  | |   (_) __ _| |__ | |_| |    ___   __ _(_)_ __  ",
            "  | |   | |/ _` | '_ \\| __| |   / _ \\ / _` | | '_ \\ ",
            "  | |___| | (_| | | | | |_| |__| (_) | (_| | | | | |",
            "  |_____|_|\\__, |_| |_|\\__|_____\\___/ \\__, |_|_| |_|",
            "           |___/                      |___/         ",
            "",
    };

    private final JavaPlugin plugin;

    public ConsoleBanner(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Prints the banner and a version line. */
    public void print(String version, int schemaVersion) {
        for (String line : BANNER) {
            send("&6" + line);
        }
        send("&7  Secure authentication for Paper  &8|  &fv" + version + "  &8|  &fschema " + schemaVersion);
        send("");
    }

    /** Sends a coloured line to the console. */
    public void send(String ampersandText) {
        String coloured = translate(ampersandText);
        try {
            Bukkit.getConsoleSender().sendMessage(coloured);
        } catch (Throwable ignored) {
            // The server is not up yet (or this is a unit test): fall back to the logger with the
            // colour codes stripped, so the information is still visible.
            Logger logger = plugin.getLogger();
            logger.info(stripCodes(coloured));
        }
    }

    /** Sends a startup step line. */
    public void step(String text) {
        send("&8[&6LightLogin&8] &7» &f" + text);
    }

    /** Sends a success line. */
    public void ok(String text) {
        send("&8[&6LightLogin&8] &7» &a" + text);
    }

    /** Sends a warning line. */
    public void warn(String text) {
        send("&8[&6LightLogin&8] &7» &e" + text);
    }

    /** Sends an error line. */
    public void error(String text) {
        send("&8[&6LightLogin&8] &7» &c" + text);
    }

    /** Prints several lines. */
    public void lines(List<String> lines) {
        lines.forEach(this::send);
    }

    private static String translate(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length() && isCode(input.charAt(i + 1))) {
                out.append(SECTION).append(Character.toLowerCase(input.charAt(i + 1)));
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isCode(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')
                || lower == 'k' || lower == 'l' || lower == 'm' || lower == 'n'
                || lower == 'o' || lower == 'r';
    }

    private static String stripCodes(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == SECTION && i + 1 < input.length()) {
                i++;
            } else {
                out.append(input.charAt(i));
            }
        }
        return out.toString();
    }

    /** The banner art, exposed so a test can assert it stays ASCII. */
    public static String[] bannerArt() {
        return BANNER.clone();
    }
}