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

package top.cmarco.lightlogin.command.changepassword;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.command.utils.CommandUtils;
import top.cmarco.lightlogin.database.LightLoginColumn;
import top.cmarco.lightlogin.database.PluginDatabase;
import top.cmarco.lightlogin.encrypt.Argon2Utilities;

import java.util.Base64;

public class ChangePasswordCommand extends LightLoginCommand {
    public ChangePasswordCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, null, "changepassword", false);
    }

    private static final String wrongSyntax = "&cWrong syntax for changepassword command!";

    @Override
    protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = (Player) sender;

        if (args.length != 0x03) {
            sendColorMessage(player, wrongSyntax, plugin);
            return;
        }

        final String oldPassword = args[0];
        final String newPassword = args[1];
        final String newPasswordConfirm = args[2];

        if (!newPassword.equals(newPasswordConfirm)) {
            sendColorPrefixMessages(player, configuration.getRegisterUnequalPasswordsMessage(), plugin);
            return;
        }

        if (!CommandUtils.isPasswordSafe(newPassword, super.configuration)) {
            CommandUtils.unsafePasswordMsg(player, super.configuration, super.plugin);
            return;
        }

        final PluginDatabase database = super.plugin.getDatabase();

        database.searchRowFromPK(player.getUniqueId().toString())
                .whenCompleteAsync((row, t) -> {


                    if (t != null) {
                        plugin.getLogger().warning(t.getLocalizedMessage());
                        if (player.isOnline()) {
                            sendColorPrefixMessages(player, configuration.getRegisterError(), plugin);
                        }
                        return;
                    }


                    if (row == null) {
                        if (player.isOnline()) {
                            sendColorPrefixMessages(player, configuration.getChangepasswordUnregistered(), plugin);
                        }
                        return;
                    }

                    final String previousSaltBase64 = row.getPasswordSalt();
                    final String previousPasswordHashBase64 = row.getPasswordHash();
                    final byte[] rowSalt = Base64.getDecoder().decode(previousSaltBase64);

                    final String oldPasswordHashed = Argon2Utilities.encryptArgon2(oldPassword, rowSalt);

                    final boolean matchedPassword = oldPasswordHashed.equals(previousPasswordHashBase64);

                    if (!matchedPassword) {
                        if (player.isOnline()) {
                            sendColorPrefixMessages(player, configuration.getChangepasswordWrongOldPassword(), plugin);
                        }
                        return;
                    }

                    final String newPasswordHashOldSalt = Argon2Utilities.encryptArgon2(newPassword, rowSalt);

                    database.updateRow(player.getUniqueId().toString(), LightLoginColumn.PASSWORD, newPasswordHashOldSalt)
                            .whenCompleteAsync((v, th) -> {

                                if (th != null) {
                                    plugin.getLogger().warning(th.getLocalizedMessage());
                                    if (player.isOnline()) {
                                        sendColorPrefixMessages(player, configuration.getRegisterError(), plugin);
                                    }
                                    return;
                                }

                                super.plugin.getPlaintextPasswordManager().setPassword(player, args[2]);

                                if (player.isOnline()) {
                                    sendColorPrefixMessages(player, configuration.getChangePasswordUpdated(), plugin);
                                }

                            });

                });
    }
}
