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
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an event that is triggered when a player is authenticated.
 * This event is typically called when a player's authentication process is completed.
 */
public class PlayerAuthenticateEvent extends PlayerEvent {

    private final AuthenticationCause authenticationCause;
    private static final HandlerList HANDLERS_LIST = new HandlerList();

    /**
     * Constructs a new PlayerAuthenticateEvent with the specified player and authentication cause.
     *
     * @param who                 The player who is being authenticated.
     * @param authenticationCause The cause of the authentication.
     */
    public PlayerAuthenticateEvent(@NotNull Player who, @NotNull AuthenticationCause authenticationCause) {
        super(who);
        this.authenticationCause = authenticationCause;
    }

    /**
     * Gets the list of event handlers for this event.
     *
     * @return The list of event handlers for this event.
     */
    public static HandlerList getHandlerList() {
        return HANDLERS_LIST;
    }

    /**
     * Gets the list of event handlers for this event.
     *
     * @return The list of event handlers for this event.
     */
    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS_LIST;
    }

    /**
     * Gets the cause of the authentication.
     *
     * @return The cause of the authentication.
     */
    @NotNull
    public AuthenticationCause getAuthenticationCause() {
        return authenticationCause;
    }
}
