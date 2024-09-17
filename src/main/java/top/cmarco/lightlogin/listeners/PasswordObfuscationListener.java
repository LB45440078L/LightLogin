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

package top.cmarco.lightlogin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.data.PlaintextPasswordManager;

import java.util.stream.Collectors;

public final class PasswordObfuscationListener extends NamedListener {

    private final LightLoginPlugin plugin;

    public PasswordObfuscationListener(@NotNull final LightLoginPlugin plugin) {
        super("password_obfuscation_listener");
        this.plugin = plugin;
    }

    private void sendWarning(@NotNull final Player player) {
        LightLoginCommand.sendColorPrefixMessages(player,
                plugin.getLightConfiguration().getPasswordInChat(), this.plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        String message = event.getMessage();

        final PlaintextPasswordManager plaintextManager = this.plugin.getPlaintextPasswordManager();
        final String plaintextPassword = plaintextManager.getPassword(player);

        if (plaintextPassword == null) {
            return;
        }

        final String[] splitMessage = message.split("\\s+");
        for (final String arg : splitMessage) {
            if (plaintextPassword.equals(arg)) {
                event.setCancelled(true);
                message = message.replace(arg, "*".repeat(arg.length()));
                sendWarning(player);
                player.sendMessage(message);
                break;
            }
        }

    }
}
