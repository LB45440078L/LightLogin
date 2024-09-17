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

package top.cmarco.lightlogin.command.temppassword;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;

import java.util.List;

public final class TempPasswordCommand extends LightLoginCommand {

    public TempPasswordCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, "lightlogin.admin.temppassword", "temppassword", true);
    }

    @Override
    protected void commandLogic(@NotNull final CommandSender sender, @NotNull final String[] args) {

        if (!(sender instanceof final ConsoleCommandSender console)) {
            return;
        }

        //temppassword <player> <psw> <verification>
        if (args.length != 3) {
            sendColorPrefixMessages(sender, configuration.getIncorrectCommandUsage(), plugin);
            return;
        }

        final String playerName = args[0], newPassword = args[1], verification = args[2];

        if (!verification.equals(configuration.getTempPasswordSecretKey())) {
            sendColorPrefixMessages(sender, configuration.getTempLoginWrongPasswordMessages(), plugin);
            return;
        }

        TempPasswordManager tempPasswordManager = plugin.getTempPasswordManager();

        tempPasswordManager.addUser(playerName, newPassword);

        final List<String> strings = configuration.getTempoLoginAddedPasswordMessages().stream().map(s -> s.replace("{PLAYER}", playerName)).toList();
        sendColorPrefixMessages(sender, strings, plugin);
    }
}
