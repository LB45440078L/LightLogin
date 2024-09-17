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

package top.cmarco.lightlogin.command.resetpassword;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.command.utils.CommandUtils;
import top.cmarco.lightlogin.database.PluginDatabase;
import top.cmarco.lightlogin.mail.MailManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ResetPasswordCommand extends LightLoginCommand {

    private final MailManager mailManager;
    private final Map<UUID, Long> lastRequest = new HashMap<>();

    public ResetPasswordCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, "lightlogin.resetpassword", "resetpassword", true);
        this.mailManager = new MailManager(plugin);
    }



    @Override
    protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {

        final PluginDatabase db = plugin.getDatabase();

        if (!configuration.isEmailEnabled()) {
            sendColorPrefixMessages(sender, configuration.getPasswordResetNotEnabled(), plugin);
            return;
        }

        if (sender instanceof ConsoleCommandSender)
        {

            if (args.length == 0x00) {
                sendColorPrefixMessages(sender, configuration.getPasswordResetHelpAdmins(), plugin);
                return;
            }

            if (args.length != 0x01) {
                sendColorPrefixMessages(sender, super.configuration.getIncorrectCommandUsage(), super.plugin);
                return;
            }

            final String playerName = args[0x00];
            final UUID uuid = CommandUtils.getUuid(super.plugin, playerName);

            if (uuid == null) {
                sendColorPrefixMessages(sender, configuration.getUnregisterNotFound(), plugin);
                return;
            }

            db.searchRowFromPK(uuid.toString())
                    .whenCompleteAsync((r,t) -> {

                        if (t != null) {
                            plugin.sendConsoleColoured("&7[ &c&lX&r &7] &cSomething went wrong during reset search of uuid.");
                            plugin.getLogger().warning(t.getLocalizedMessage());
                            return;
                        }

                        if (r == null) {
                            sendColorPrefixMessages(sender, configuration.getUnregisterNotFound(), plugin);
                            return;
                        }

                        if (r.getEmail() == null) {
                            sendColorPrefixMessages(sender, configuration.getPasswordResetMissingEmail(), plugin);
                            return;
                        }

                        mailManager.updatePasswordAndSendEmail(uuid, playerName, r.getEmail()).whenCompleteAsync((b, t2) -> {

                            if (t2 != null) {
                                plugin.getLogger().warning("Something went wrong when doing reset email request of " + playerName);
                                plugin.getLogger().warning(t2.getLocalizedMessage());
                                return;
                            }

                            if (b) {
                                List<String> remappedMsg = configuration.getPasswordResetSuccess().stream().map(s->s.replace("{EMAIL}", r.getEmail())).collect(Collectors.toList());
                                sendColorPrefixMessages(sender, remappedMsg, plugin);

                                Player find = plugin.getServer().getPlayer(uuid);
                                if (find != null && find.isOnline()) {
                                    sendColorPrefixMessages(find, remappedMsg, plugin);

                                }
                            } else {
                                List<String> remappedMsg = configuration.getPasswordResetSuccess().stream().map(s->s.replace("{PLAYER}", playerName)).collect(Collectors.toList());
                                sendColorPrefixMessages(sender, remappedMsg, plugin);
                            }

                        });

                    });

        }
        else {
            final long nowTime = System.currentTimeMillis();
            final Player player = (Player) sender;

            if (lastRequest.containsKey(player.getUniqueId())) {
                long lastTime = lastRequest.get(player.getUniqueId());
                boolean allow = (double) (nowTime - lastTime) / 1E3 >= configuration.getRecoveryMinDelay()*60;

                if (!allow) {
                    sendColorPrefixMessages(sender, configuration.getPasswordResetTooEarly(), plugin);
                    return;
                }

            }

            lastRequest.put(player.getUniqueId(), nowTime);

            if (args.length != 0) {
                sendColorPrefixMessages(sender, super.configuration.getPasswordResetHelp(), super.plugin);
                return;
            }

            db.searchRowFromPK(player.getUniqueId().toString())
                    .whenCompleteAsync((r,t) -> {

                        if (t != null) {
                            plugin.sendConsoleColoured("&7[ &c&lX&r &7] &cSomething went wrong during reset search of uuid.");
                            plugin.getLogger().warning(t.getLocalizedMessage());
                            return;
                        }

                        if (r == null) {
                            sendColorPrefixMessages(sender, configuration.getUnregisterNotFound(), plugin);
                            return;
                        }

                        if (r.getEmail() == null) {
                            sendColorPrefixMessages(sender, configuration.getPasswordResetMissingEmail(), plugin);
                            return;
                        }

                        mailManager.updatePasswordAndSendEmail(player.getUniqueId(), player.getName(), r.getEmail()).whenCompleteAsync((b, t2) -> {

                            if (t2 != null) {
                                plugin.getLogger().warning("Something went wrong when doing reset email request of " + player.getName());
                                plugin.getLogger().warning(t2.getLocalizedMessage());
                                return;
                            }

                            if (b) {
                                List<String> remappedMsg = configuration.getPasswordResetSuccess().stream().map(s->s.replace("{EMAIL}", r.getEmail())).collect(Collectors.toList());
                                sendColorPrefixMessages(sender, remappedMsg, plugin);

                                if (player.isOnline()) {
                                    sendColorPrefixMessages(player, remappedMsg, plugin);
                                }

                            } else {
                                List<String> remappedMsg = configuration.getPasswordResetSuccess().stream().map(s->s.replace("{PLAYER}", player.getName())).collect(Collectors.toList());
                                sendColorPrefixMessages(sender, remappedMsg, plugin);
                            }

                        });

                    });

        }

    }
}
