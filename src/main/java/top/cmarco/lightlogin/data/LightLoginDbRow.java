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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightLoginDbRow {
    @NotNull
    private final String uuid;

    @NotNull
    private final String passwordHash;

    @NotNull
    private final String passwordSalt;

    @Nullable
    private final String email;

    private final long lastLogin;

    private final long last_ipv4;

    public LightLoginDbRow(@NotNull String uuid,
                           @NotNull String passwordHash,
                           @NotNull String passwordSalt,
                           @Nullable String email,
                           long lastLogin,
                           long last_ipv4) {
        this.uuid = uuid;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.email = email;
        this.lastLogin = lastLogin;
        this.last_ipv4 = last_ipv4;
    }

    @NotNull
    public String getUuid() {
        return uuid;
    }

    @NotNull
    public String getPasswordHash() {
        return passwordHash;
    }

    @NotNull
    public String getPasswordSalt() {
        return passwordSalt;
    }

    @Nullable
    public String getEmail() {
        return email;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public long getLastIpv4() {
        return last_ipv4;
    }

    @Override
    public String toString() {
        return "{" + uuid + ", " + passwordHash + ", " + passwordSalt + ", " +
                (email != null ? email + ", " : "") + lastLogin + ", " + last_ipv4 + "}";
    }
}
