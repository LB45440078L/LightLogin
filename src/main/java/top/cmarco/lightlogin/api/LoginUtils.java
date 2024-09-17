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

package top.cmarco.lightlogin.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Utility class for handling player login-related operations.
 */
public final class LoginUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     * Throws a {@code RuntimeException} if instantiated.
     */
    private LoginUtils() {
        throw new RuntimeException("Cannot instantiate LoginUtils because it is a utility class!");
    }

    /**
     * Executes the specified action if the player is online.
     *
     * @param player         The player to check if online.
     * @param onlineConsumer The action to be executed if the player is online.
     * @throws NullPointerException if {@code player} is {@code null}.
     */
    public static void whenOnline(@NotNull final Player player, @NotNull final Consumer<? super Player> onlineConsumer) {
        if (Objects.requireNonNull(player, "Passed player is null!").isOnline()) {
            onlineConsumer.accept(player);
        }
    }

    /**
     * Executes one of the specified actions depending on whether the player is online or not.
     *
     * @param player          The player to check if online.
     * @param onlineConsumer  The action to be executed if the player is online.
     * @param offlineConsumer The action to be executed if the player is offline.
     * @throws NullPointerException if {@code player} is {@code null}.
     */
    public static void whenOnlineOrElse(@NotNull final Player player,
                                        @NotNull final Consumer<? super Player> onlineConsumer,
                                        @NotNull final Consumer<? super Player> offlineConsumer) {

        if (Objects.requireNonNull(player, "Passed player is null!").isOnline()) {
            onlineConsumer.accept(player);
        } else {
            offlineConsumer.accept(player);
        }

    }
}
