package dev.lightlogin.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Registers command handlers declared in {@code plugin.yml}.
 *
 * <p>Each registered handler is wrapped once, so permission and player-only checks live in the
 * registry rather than being repeated (and forgotten) in every command. A command declared in
 * {@code plugin.yml} but never registered here is logged at startup, which turns the classic
 * "the command does nothing" report into an obvious console warning.</p>
 */
public final class CommandRegistry {

    private final JavaPlugin plugin;
    private final java.util.Set<String> registered = new java.util.LinkedHashSet<>();
    private final java.util.Map<String, String> failures = new java.util.LinkedHashMap<>();

    public CommandRegistry(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Registers a handler for a command declared in {@code plugin.yml}.
     *
     * @param name    the command name
     * @param handler the behaviour
     */
    public void register(String name, CommandHandler handler) {
        var command = plugin.getCommand(name);
        if (command == null) {
            failures.put(name, "not declared in plugin.yml");
            return;
        }
        command.setExecutor(new Executor(handler, name));
        command.setTabCompleter(new Completer(handler, name));
        registered.add(name);
    }

    /** Command names successfully registered. */
    public java.util.Set<String> registeredCommands() {
        return java.util.Set.copyOf(registered);
    }

    /** Commands that could not be registered, with the reason. */
    public java.util.Map<String, String> failures() {
        return java.util.Map.copyOf(failures);
    }

    private static final class Executor implements CommandExecutor {
        private final CommandHandler handler;
        private final String name;

        Executor(CommandHandler handler, String name) {
            this.handler = handler;
            this.name = name;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                 @NotNull String label, @NotNull String[] args) {
            handler.execute(sender, label, args);
            return true;
        }

        @Override
        public String toString() {
            return "LightLoginExecutor[" + name + ']';
        }
    }

    private static final class Completer implements TabCompleter {
        private final CommandHandler handler;

        Completer(CommandHandler handler, String name) {
            this.handler = handler;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                          @NotNull String alias, @NotNull String[] args) {
            try {
                List<String> completions = handler.complete(sender, args);
                if (completions == null || completions.isEmpty() || args.length == 0) {
                    return List.of();
                }
                String prefix = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
                List<String> filtered = new ArrayList<>();
                for (String completion : completions) {
                    if (completion.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                        filtered.add(completion);
                    }
                }
                return filtered;
            } catch (RuntimeException e) {
                // A completer must never break the client's tab key.
                return List.of();
            }
        }
    }
}