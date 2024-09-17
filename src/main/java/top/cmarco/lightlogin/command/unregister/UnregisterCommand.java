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

package top.cmarco.lightlogin.command.unregister;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.api.UnregisterEvent;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.data.AuthenticationManager;
import top.cmarco.lightlogin.database.PluginDatabase;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class UnregisterCommand extends LightLoginCommand {

    public UnregisterCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, "lightlogin.admin.unregister", "unregister", true);
    }

    @Override
    protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {
        final AuthenticationManager authManager = super.plugin.getAuthenticationManager();

        if (sender instanceof Player && !authManager.isAuthenticated((Player) sender)) {
            return;
        }

        if (args.length != 1) {
            sendColorPrefixMessages(sender, super.configuration.getUnregisterIncorrectUsage(), super.plugin);
            return;
        }

        UUID playerUUID = null;
        if (!super.plugin.getServer().getOnlineMode()) {
            playerUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + args[0]).getBytes(StandardCharsets.UTF_8));
        } else {
            OfflinePlayer offlinePlayer = super.plugin.getServer().getOfflinePlayer(args[0]);
            if (offlinePlayer.hasPlayedBefore()) {
                playerUUID = offlinePlayer.getUniqueId();
            }
        }

        if (playerUUID == null) {
            sendColorPrefixMessages(sender, super.configuration.getUnregisterNotFound(), super.plugin);
            return;
        }

        PluginDatabase database = super.plugin.getDatabase();
        authManager.unauthenticate(playerUUID);

        UnregisterEvent unregisterEvent = new UnregisterEvent(playerUUID, args[0]);
        super.plugin.getServer().getPluginManager().callEvent(unregisterEvent);

        final UUID finalPlayerUUID = playerUUID;

        database.deleteRow(playerUUID.toString())
                .whenCompleteAsync((bool, throwable) -> {

                    if (throwable != null) {
                        super.plugin.getLogger().warning(throwable.getLocalizedMessage());
                        return;
                    }

                    if (bool) {

                        if (!(sender instanceof Player) || ((Player) sender).isOnline()) {
                            sendColorPrefixMessages(sender, super.configuration.getUnregisteredSuccess(), plugin);
                            authManager.addUnregistered(finalPlayerUUID);
                        }

                    } else {

                        if (!(sender instanceof Player) || ((Player) sender).isOnline()) {
                            sendColorPrefixMessages(sender, super.configuration.getUnregisterNotFound(), plugin);
                        }

                    }

                });

    }
}
