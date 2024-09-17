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

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents an event that is triggered when a player is unregistered.
 * This event is typically called when a player's registration data is removed.
 */
public class UnregisterEvent extends Event {

    private static final HandlerList HANDLERS_LIST = new HandlerList();
    private final UUID uuid;
    private final String name;

    /**
     * Constructs a new UnregisterEvent with the specified UUID and name.
     *
     * @param uuid The UUID of the player being unregistered.
     * @param name The name of the player being unregistered.
     */
    public UnregisterEvent(@NotNull UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
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
     * Gets the UUID of the player being unregistered.
     *
     * @return The UUID of the player being unregistered.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the name of the player being unregistered.
     *
     * @return The name of the player being unregistered.
     */
    public String getName() {
        return name;
    }
}