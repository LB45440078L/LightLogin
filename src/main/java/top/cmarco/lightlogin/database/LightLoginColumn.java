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

package top.cmarco.lightlogin.database;

public enum LightLoginColumn {

    UUID("uuid", String.class),
    PASSWORD("password", String.class),
    SALT("salt", String.class),
    EMAIL("email", String.class),
    LAST_LOGIN("last_login", Long.class),
    LAST_IPV4("last_ipv4", Long.class);

    private final String name;
    private final Class<?> columnType;

    LightLoginColumn(String name, Class<?> columnType) {
        this.name = name;
        this.columnType = columnType;
    }

    public String getName() {
        return name;
    }

    public Class<?> getColumnType() {
        return columnType;
    }
}
