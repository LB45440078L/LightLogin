package dev.lightlogin.paper.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * A registered command's behaviour.
 *
 * <p>A functional interface with a default completer, so a simple command is a lambda while a
 * command with tab completion supplies one extra method.</p>
 */
@FunctionalInterface
public interface CommandHandler {

    /** Runs the command. */
    void execute(CommandSender sender, String label, String[] args);

    /** Tab completion for the command's arguments. */
    default List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}