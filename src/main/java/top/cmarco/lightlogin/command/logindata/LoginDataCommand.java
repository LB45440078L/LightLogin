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

 package top.cmarco.lightlogin.command.logindata;

 import java.nio.charset.StandardCharsets;
 import java.text.SimpleDateFormat;
 import java.util.Date;
import java.util.List;
 import java.util.UUID;
 import java.util.concurrent.CompletableFuture;
 
 import org.bukkit.OfflinePlayer;
 import org.bukkit.command.CommandSender;
 import org.jetbrains.annotations.NotNull;

 import top.cmarco.lightlogin.LightLoginPlugin;
 import top.cmarco.lightlogin.command.LightLoginCommand;
 import top.cmarco.lightlogin.data.LightLoginDbRow;
 import top.cmarco.lightlogin.database.PluginDatabase;
 import top.cmarco.lightlogin.network.NetworkUtilities;
 
 public final class LoginDataCommand extends LightLoginCommand {
 
     private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
 
     public LoginDataCommand(@NotNull LightLoginPlugin plugin) {
         super(plugin, "lightlogin.logindata", "login-data", true);
     }
 
     @Override
     protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {
 
         if (args.length != 1) {
             sendColorPrefixMessages(sender, super.configuration.getIncorrectCommandUsage(), super.plugin);
             return;
         }
 
         PluginDatabase database = plugin.getDatabase();
 
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
 
         final UUID playerUUIDFinal = playerUUID;
         CompletableFuture < LightLoginDbRow > row = database.searchRowFromPK(playerUUID.toString());
 
         final List < String > displayMsg = configuration.getLoginDataDisplay();
 
         row.whenCompleteAsync((data, throwable) -> {
 
             if (throwable != null) {
                 super.plugin.getLogger().warning(throwable.getLocalizedMessage());
                 return;
             }
 
             if (data == null) {
                 sendColorPrefixMessages(sender, configuration.getLoginDataNotFound(), plugin);
             } else {

                    final List<String> modifiedStrs = displayMsg.stream().map(s -> s
                    .replace("{UUID}", playerUUIDFinal.toString())
                    .replace("{EMAIL}", data.getEmail() != null ? data.getEmail() : "Unavailable")
                   .replace("{IP}", NetworkUtilities.convertLongToInetSocketAddress(data.getLastIpv4()))
                   .replace("{LOGIN}", DATE_FORMAT.format(new Date(data.getLastLogin())))
                   ).toList();
                   
                    sendColorPrefixMessages(sender, modifiedStrs, plugin);
            }
         });
 
     }
 
 }