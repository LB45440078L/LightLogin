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

package top.cmarco.lightlogin.command.email;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.data.AuthenticationManager;
import top.cmarco.lightlogin.database.LightLoginColumn;
import top.cmarco.lightlogin.database.PluginDatabase;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EmailCommand extends LightLoginCommand {
    public EmailCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, null, "email", false);
    }

    private static final Pattern EMAIL = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    public static boolean isValidEmail(final String email) {
        return EMAIL.matcher(email).matches();
    }

    @Override
    protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = (Player) sender;

        AuthenticationManager authenticationManager = plugin.getAuthenticationManager();

        if (!authenticationManager.isAuthenticated(player)) {
            return;
        }

        if (args.length == 0) {
            sendColorPrefixMessages(player, super.configuration.getEmailUpdateHelp(), super.plugin);
            return;
        }

        if (args.length == 1) {

            String email = args[0];

            if (!isValidEmail(email)) {
                sendColorPrefixMessages(player, super.configuration.getEmailInvalidFormat(), super.plugin);
                return;
            }

            PluginDatabase database = super.plugin.getDatabase();
            database.updateRow(player.getUniqueId().toString(), LightLoginColumn.EMAIL, email)
                    .whenCompleteAsync((v, t) -> {

                        if (t != null) {
                            plugin.getLogger().warning(t.getLocalizedMessage());
                            if (player.isOnline()) {
                                sendColorPrefixMessages(player, configuration.getEmailSomethingWrong(), super.plugin);
                            }
                            return;
                        }

                        if (player.isOnline()) {
                            List<String> toParse = super.configuration.getEmailUpdated();
                            toParse = toParse.stream().map(s->s.replace("{EMAIL}", email)).collect(Collectors.toList());
                            sendColorPrefixMessages(player, toParse, super.plugin);
                        }

                    });



        } else {
            sendColorPrefixMessages(player, super.configuration.getIncorrectCommandUsage(), super.plugin);
        }

    }
}
