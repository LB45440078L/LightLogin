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
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.configuration.LightConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutoKickManager {

    private final LightLoginPlugin plugin;
    private final Map<UUID, Long> joinedMap = new HashMap<>();
    private final LoginTimeoutBarManager timeoutBarManager;
    private BukkitTask bukkitTask = null;
    private BukkitTask titleTask = null;

    public AutoKickManager(@NotNull final LightLoginPlugin plugin) {
        this.plugin = plugin;
        this.timeoutBarManager = new LoginTimeoutBarManager(plugin.getLightConfiguration());
    }

    public void startAutoKickTask() {
        if (bukkitTask != null && titleTask != null) {
            stopAutoKickTask();
        }

        LightConfiguration config = plugin.getLightConfiguration();

        final BasicAuthenticationManager authenticationManager = (BasicAuthenticationManager) plugin.getAuthenticationManager();
        double ticks = config.getKickAfterSeconds() * 1E3;
        this.bukkitTask = plugin.getServer().getScheduler().runTaskTimer(plugin, ()-> {

            final Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();
            for (final Player onlinePlayer : onlinePlayers) {

                if (!joinedMap.containsKey(onlinePlayer.getUniqueId())) {
                    addEntered(onlinePlayer);
                    continue;
                }

                final long lastEnter = joinedMap.get(onlinePlayer.getUniqueId());
                final boolean compareTo = (System.currentTimeMillis() - lastEnter) >= ticks;

                if (authenticationManager.isAuthenticated(onlinePlayer)) {
                    return;
                }

                if (!compareTo && config.isLoginAnimationEnabled()) {
                    timeoutBarManager.sendBar(onlinePlayer, lastEnter);
                    timeoutBarManager.sendSound(onlinePlayer);
                    return;
                }

                onlinePlayer.kickPlayer(LightLoginCommand.colorAndReplace(plugin.getLightConfiguration().getLoginTookTooMuchTime(), plugin));

            }

        }, 1L, 20L);

        titleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, ()->{

            final Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();
            for (final Player onlinePlayer : onlinePlayers) {
                if (authenticationManager.isAuthenticated(onlinePlayer)) {
                    return;
                }

                final String loginTitle = LightLoginCommand.colorMessage(config.getTitleText().replace("{PLAYER}", onlinePlayer.getDisplayName()).replace("{PREFIX}", config.getMessagePrefix()));
                final String loginSubtitle = LightLoginCommand.colorMessage(config.getSubtitleText().replace("{PLAYER}", onlinePlayer.getDisplayName()).replace("{PREFIX}", config.getMessagePrefix()));
                final String regTitle = LightLoginCommand.colorMessage(config.getRegisterTitleText().replace("{PLAYER}", onlinePlayer.getDisplayName()).replace("{PREFIX}", config.getMessagePrefix()));
                final String regSubtitle = LightLoginCommand.colorMessage(config.getRegisterSubtitleText().replace("{PLAYER}", onlinePlayer.getDisplayName()).replace("{PREFIX}", config.getMessagePrefix()));

                if (authenticationManager.isUnloginned(onlinePlayer.getUniqueId()) && !authenticationManager.isUnregistered(onlinePlayer.getUniqueId())) {
                    onlinePlayer.sendTitle(loginTitle, loginSubtitle, config.getTitleFadeIn(), config.getTitleStay(), config.getTitleFadeOut());
                } else {
                    onlinePlayer.sendTitle(regTitle, regSubtitle, config.getTitleFadeIn(), config.getTitleStay(), config.getTitleFadeOut());
                }

            }

        }, 1L, config.getTitleRepeatDelay());

    }

    public void stopAutoKickTask() {
        if (bukkitTask == null || titleTask == null) {
            return;
        }

        bukkitTask.cancel();
        bukkitTask = null;

        titleTask.cancel();
        titleTask = null;
    }

    public void addEntered(@NotNull final Player player) {
        this.joinedMap.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void cleanPlayerData(@NotNull final Player player) {
        this.joinedMap.remove(player.getUniqueId());
    }
}
