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

import org.jetbrains.annotations.NotNull;

/**
 * Enumeration representing different causes of player authentication.
 * Each cause corresponds to a specific method or action triggering the authentication process.
 */
public enum AuthenticationCause {

    /**
     * Authentication triggered by a command.
     */
    COMMAND("Command"),

    /**
     * Authentication triggered automatically, such as upon login.
     */
    AUTOMATIC("Automatic"),

    /**
     * Authentication triggered by administrators.
     */
    ADMINS("Admins");

    private final String formalName;

    /**
     * Constructs an AuthenticationCause enum with the specified formal name.
     *
     * @param formalName The formal name of the authentication cause.
     */
    AuthenticationCause(String formalName) {
        this.formalName = formalName;
    }

    /**
     * Gets the formal name of the authentication cause.
     *
     * @return The formal name of the authentication cause.
     */
    @NotNull
    public String getFormalName() {
        return formalName;
    }
}
