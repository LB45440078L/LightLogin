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

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.configuration.LightConfiguration;

public final class LoginTimeoutBarManager {

    private final LightConfiguration config;

    public LoginTimeoutBarManager(@NotNull final LightConfiguration config) {
        this.config = config;
    }

    public void sendBar(@NotNull final Player player, @Range(from = 0L, to = Long.MAX_VALUE) final long joinedTime) {

        final Player.Spigot spigotPlayer = player.spigot();
        final StringBuilder actionBar = new StringBuilder();
        final long currentTime = System.currentTimeMillis();
        final int stepsFilled = getStepsFilled(joinedTime, currentTime);
        final int maxTime = config.getKickAfterSeconds();
        final long kickTime = joinedTime + (maxTime * 1000L);

        // Fill the progress bar accordingly
        for (int i = 0; i < config.getLoginAnimationBarLength(); i++) {
            if (i < stepsFilled) {
                actionBar.append(config.getLoginAnimationBarTimePassedColour()).append(config.getLoginAnimationBarCharacterPassed()); // Green color for filled steps
            } else {
                actionBar.append(config.getLoginAnimationBarTimeNotPassedColour()).append(config.getLoginAnimationBarCharacterNotPassed()); // Green color for filled steps
            }
        }

        final long remainingSeconds = (kickTime - currentTime) / 1000L;

        final String finalOutput = config.getLoginAnimationFormat()
                        .replace("{BAR}", actionBar.toString())
                        .replace("{TIME}", Long.toString(remainingSeconds));

        spigotPlayer.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(LightLoginCommand.colorMessage(finalOutput)));

    }

    private int getStepsFilled(long joinedTime, long currentTime) {
        final int maxTime = config.getKickAfterSeconds();
        final double totalTime = (double) maxTime * 1000L;
        final double elapsedTime = currentTime - joinedTime;

        // Calculate percentage completion
        final double percentage = (elapsedTime / totalTime) * 100d;

        // Calculate how many steps should be filled based on the percentage
        return (int) (percentage / (100 / config.getLoginAnimationBarLength()));
    }

    public void sendSound(@NotNull final Player player) {
        if (!config.isSoundsEnabled()) {
            return;
        }

        player.playSound(player.getEyeLocation(), Sound.valueOf(config.getActionBarTickSound()), .2f ,1.0f);
    }
}
