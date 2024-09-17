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

package top.cmarco.lightlogin.data;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public final class StartupLoginsManager {

    private final Set<UUID> firstLoginSinceStartup = new CopyOnWriteArraySet<>();

    public void addPlayer(@NotNull Player player) {
        if (!contains(player)) {
            this.firstLoginSinceStartup.add(player.getUniqueId());
        }
    }

    public boolean contains(@NotNull Player player) {
        return this.contains(player.getUniqueId());
    }

    public boolean contains(@NotNull UUID uuid) {
        return this.firstLoginSinceStartup.contains(uuid);
    }

}
