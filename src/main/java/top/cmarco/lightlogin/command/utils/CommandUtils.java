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

package top.cmarco.lightlogin.command.utils;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.configuration.LightConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CommandUtils {

    private static String specialDisplayCache = null;

    private CommandUtils() {
        throw new RuntimeException();
    }

    @Nullable
    public static UUID getUuid(@NotNull final Plugin plugin, @NotNull final String username) {
        UUID playerUUID = null;
        if (!plugin.getServer().getOnlineMode()) {
            playerUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        } else {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(username);
            if (offlinePlayer.hasPlayedBefore()) {
                playerUUID = offlinePlayer.getUniqueId();
            }
        }

        return playerUUID;
    }

    public static boolean isCharAllowed(final char character, final @NotNull List<Character> specialChars) {
        return (character >= 0x30 && character <= 0x39) ||
                (character >= 0x41 && character <= 0x5A) ||
                (character >= 0x61 && character <= 0x7A) ||
                specialChars.contains(character);
    }

    public static boolean isPasswordSafe(@NotNull final String password, LightConfiguration configuration) {
        final int minLength = Math.max(configuration.getSafePasswordMinLength(), 0);
        final int minUppercase = Math.max(configuration.getSafePasswordMinUppercase(), 0);
        final int minNumbers = Math.max(configuration.getSafePasswordMinNumbers(), 0);
        final int minSpecial = Math.max(configuration.getSafePasswordMinSpecial(), 0);
        final List<Character> special = configuration.getSafePasswordAllowedSpecial();

        if (password.length() < minLength) {
            return false;
        }

        final char[] passwordChars = password.toCharArray();
        int uppercaseCount = 0, numberCount = 0, specialCount = 0;
        for (final char tempChar : passwordChars) {
            if (!isCharAllowed(tempChar, special)) {
                return false;
            }
            if (Character.isUpperCase(tempChar)) {
                uppercaseCount++;
            }
            if (0x30 <= tempChar && 0x39 >= tempChar) {
                numberCount++;
            }
            if (special.contains(tempChar)) {
                specialCount++;
            }
        }

        if (minUppercase > uppercaseCount) {
            return false;
        }

        if (minNumbers > numberCount) {
            return false;
        }

        if (minSpecial > specialCount) {
            return false;
        }

        return true;
    }

    public static void unsafePasswordMsg(@NotNull Player player, LightConfiguration configuration, LightLoginPlugin plugin) {
        final int minLength = configuration.getSafePasswordMinLength();
        final int minUppercase = configuration.getSafePasswordMinUppercase();
        final int minNumbers = configuration.getSafePasswordMinNumbers();
        final int minSpecial = configuration.getSafePasswordMinSpecial();
        final List<Character> special = configuration.getSafePasswordAllowedSpecial();

        if (specialDisplayCache == null) {
            final StringBuilder specialDisplay = new StringBuilder();
            specialDisplay.append("&7[&e");
            final Iterator<Character> characterIterator = special.iterator();
            while (characterIterator.hasNext()) {
                specialDisplay.append(characterIterator.next()).append("&f,&e");
            }
            specialDisplay.append("&7]");
            specialDisplayCache = specialDisplay.toString();
        }

        LightLoginCommand.sendColorPrefixMessages(player,
                configuration.getUnsafePassword().stream()
                        .map(str -> str.replace("{MIN_LENGTH}", String.valueOf(minLength))
                                .replace("{MIN_UPCASE}", String.valueOf(minUppercase))
                                .replace("{MIN_NUMBERS}", String.valueOf(minNumbers))
                                .replace("{MIN_SPECIAL}", String.valueOf(minSpecial))
                                .replace("{SPECIAL}", specialDisplayCache))
                        .collect(Collectors.toList()),
                plugin);
    }
}
