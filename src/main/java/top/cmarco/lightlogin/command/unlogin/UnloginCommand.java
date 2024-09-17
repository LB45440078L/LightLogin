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

package top.cmarco.lightlogin.command.unlogin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.api.AuthenticationCause;
import top.cmarco.lightlogin.api.PlayerUnauthenticateEvent;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.data.AuthenticationManager;
import top.cmarco.lightlogin.database.LightLoginColumn;
import top.cmarco.lightlogin.database.PluginDatabase;

public class UnloginCommand extends LightLoginCommand {
    public UnloginCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, "lightlogin.admin.unlogin", "unlogin", true);
    }

    @Override
    protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {
        final AuthenticationManager authenticationManager = plugin.getAuthenticationManager();

        if (sender instanceof Player && !authenticationManager.isAuthenticated((Player) sender)) {
            return;
        }

        if (args.length != 1) {
            // TODO: add
            return;
        }

        final Player player = plugin.getServer().getPlayer(args[0x00]);

        if (player == null || !player.isOnline()) {
            sendColorPrefixMessages(sender, configuration.getPlayerNotOnline(), plugin);
            return;
        }

        final PluginDatabase database = plugin.getDatabase();

        database.updateRow(player.getUniqueId().toString(), LightLoginColumn.LAST_LOGIN, 1L);
        authenticationManager.addUnloginned(player);

        PlayerUnauthenticateEvent playerUnauthenticateEvent = new PlayerUnauthenticateEvent(player, AuthenticationCause.AUTOMATIC);
        this.plugin.getServer().getPluginManager().callEvent(playerUnauthenticateEvent);
    }
}
