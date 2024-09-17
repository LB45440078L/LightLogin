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

package top.cmarco.lightlogin.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.configuration.LightConfiguration;

import java.util.List;
import java.util.stream.Collectors;


public abstract class LightLoginCommand implements CommandExecutor {

    protected final LightLoginPlugin plugin;
    protected final LightConfiguration configuration;
    protected final String basePermission;
    protected final String commandName;
    protected final boolean allowConsole;

    protected LightLoginCommand(@NotNull LightLoginPlugin plugin,
                                @Nullable String basePermission,
                                @NotNull String commandName,
                                boolean allowConsole) {
        this.plugin = plugin;
        this.basePermission = basePermission;
        this.commandName = commandName;
        this.allowConsole = allowConsole;
        this.configuration = plugin.getLightConfiguration();
    }

    public static String colorMessage(@NotNull final String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String colorAndReplace(@NotNull final String text, @NotNull final LightLoginPlugin plugin) {
        return colorMessage(text.replaceAll("\\{PREFIX}", plugin.getLightConfiguration().getMessagePrefix()));
    }

    public static void sendColorMessage(@NotNull final CommandSender sender, @NotNull String string, @NotNull LightLoginPlugin plugin) {
        sender.sendMessage(colorAndReplace(string.replace("\\{PLAYER}", sender.getName()), plugin));
    }

    public static void sendColorPrefixMessages(@NotNull final CommandSender sender, @NotNull List<String> strings, @NotNull LightLoginPlugin plugin) {
        strings.forEach(string -> sendColorMessage(sender, string, plugin));
    }

    protected abstract void commandLogic(@NotNull final CommandSender sender, @NotNull final String[] args);

    public final void register() {
        this.plugin.getServer().getPluginCommand(this.commandName).setExecutor(this);
    }

    @Override
    public final boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!allowConsole && !(sender instanceof Player)) {
            sendColorPrefixMessages(sender, configuration.getPlayerOnlyCommandMessage(), plugin);
            return false;
        }

        if (this.basePermission != null && !sender.hasPermission(this.basePermission)) {
            sendColorPrefixMessages(sender, configuration.getMissingPermissionMessage().stream().map(s -> s.replaceAll("\\{PERMISSION}", basePermission)).collect(Collectors.toList()), plugin);
            return false;
        }

        this.commandLogic(sender, args);

        return false;
    }
}
