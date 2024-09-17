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
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;

public abstract class NamedListener implements Listener {

    private final String name;

    protected static void giveBlindness(@NotNull final Player player, @NotNull final LightLoginPlugin plugin) {

        if (!plugin.getLightConfiguration().isLoginBlindness()) {
            return;
        }

        player.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(20*60*60, 1));
    }

    protected void runSync(@NotNull Plugin plugin, @NotNull Runnable runnable) {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTask(plugin, runnable);
    }

    protected NamedListener(@NotNull String name) {
        this.name = name;
    }

    @NotNull
    public String getName() {
        return name;
    }
}
