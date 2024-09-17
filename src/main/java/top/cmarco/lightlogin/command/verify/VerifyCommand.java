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

package top.cmarco.lightlogin.command.verify;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;

import java.util.HashMap;
import java.util.UUID;

public final class VerifyCommand extends LightLoginCommand {

    private final HashMap<UUID, Integer> failedAttempts = new HashMap<>();

    private void addOrIncrement(UUID uuid) {
        if (failedAttempts.containsKey(uuid)) {
            failedAttempts.compute(uuid, (k, v) -> v == null ? 1 : v + 1);
        } else {
            failedAttempts.put(uuid, 1);
        }
    }

    private static boolean isInteger(@NotNull String str) {
        return str.matches("-?\\d+");
    }

    public VerifyCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, null, "verify", false);
    }

    private void checkMaxAttempts(Player player) {
        int max = configuration.getCaptchaMaxAttempts();
        if (!failedAttempts.containsKey(player.getUniqueId())) {
            return;
        } else if (failedAttempts.get(player.getUniqueId()) > max) {
            configuration.getCaptchaPunishments().forEach(p ->
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), p.replace("{PLAYER}", player.getName())));
        }
    }

    @Override
    protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {
        final VerificationManager verificationManager = plugin.getVerificationManager();

        if (!verificationManager.isCaptchaActive()) {
            return;
        }

        if (args.length != 1) {
            sendColorPrefixMessages(sender, super.configuration.getIncorrectCommandUsage(), super.plugin);
            return;
        }

        final String answer = args[0];
        final Player player = (Player) sender;
        if (!isInteger(answer)) {
            addOrIncrement(player.getUniqueId());
            sendColorPrefixMessages(sender, super.configuration.getCaptchaWrongAnswer(), super.plugin);
            checkMaxAttempts(player);
            return;
        }


        final int i = Integer.parseInt(answer);
        final VerificationData verificationData = verificationManager.getData(player);

        if (verificationData == null) {
            return;
        } else if (verificationData.getAnswer() != i) {
            addOrIncrement(player.getUniqueId());
            sendColorPrefixMessages(sender, super.configuration.getCaptchaWrongAnswer(), super.plugin);
            checkMaxAttempts(player);
            return;
        }

        sendColorPrefixMessages(sender, super.configuration.getCaptchaCorrectAnswer(), super.plugin);
        verificationManager.verifyPlayer(player);

    }
}
