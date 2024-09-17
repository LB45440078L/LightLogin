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

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoidLoginManager {

    private final World loginWorld;

    private final ConcurrentHashMap<UUID, Location> joinLocation = new ConcurrentHashMap<>();

    public VoidLoginManager(@NotNull World loginWorld) {
        this.loginWorld = Objects.requireNonNull(loginWorld);
    }

    public void sendLoginToVoid(@NotNull Player player) {
        this.setJoinLocation(player, player.getLocation());
        player.teleport(this.loginWorld.getSpawnLocation());
    }

    public void sendToLastLocation(@NotNull Player player) {
        Location lastLocation = this.joinLocation.get(player.getUniqueId());
        if (lastLocation != null) {
            player.teleport(lastLocation);
        }
    }

    @Nullable
    public Location getLocation(@NotNull Player player) {
        return this.joinLocation.get(player.getUniqueId());
    }

    public void setJoinLocation(@NotNull Player player, @NotNull Location location) {
        this.joinLocation.put(player.getUniqueId(), location);
    }

    public void clearLastLocation(@NotNull UUID uuid) {
        this.joinLocation.remove(uuid);
    }
}
