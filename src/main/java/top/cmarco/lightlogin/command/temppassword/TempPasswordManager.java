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

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;

import java.util.HashMap;
import java.util.Map;

public final class TempPasswordManager {
    public TempPasswordManager(@NotNull final LightLoginPlugin plugin) {
        this.plugin = plugin;
    }

    private final LightLoginPlugin plugin;
    private final Map<String, String> tempPasswords = new HashMap<>();

    public boolean checkPassword(@NotNull final Player player, @NotNull final String passwordAttempt) {
        if (this.tempPasswords.containsKey(player.getName())) {
            return this.tempPasswords.get(player.getName()).equals(passwordAttempt);
        } else {
            return false;
        }
    }

    public boolean hasUser(@NotNull final Player player) {
        return this.tempPasswords.containsKey(player.getName());
    }

    public void addUser(@NotNull final String username, @NotNull final String password) {
        int timeout = plugin.getLightConfiguration().getTempPasswordTimeout();
        this.tempPasswords.put(username, password);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeUser(username), 20L * timeout);
    }

    public void removeUser(@NotNull final String username) {
        this.tempPasswords.remove(username);
    }
}
