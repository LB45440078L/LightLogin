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

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package top.cmarco.lightlogin.database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.cmarco.lightlogin.data.LightLoginDbRow;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Represents a specific implementation of database used by this
 * plugin to store data.
 * Only one implementation may be used at once by this software,
 * however many different ones may be coded so that the user can
 * choose his preferred one.
 *
 * Currently implemented: SQLite, MySQL, PostgresSQL
 */
public interface PluginDatabase {

    /**
     * Load the necessary driver classes used by the database.
     * When not necessary, leave implementation empty.
     */
    default void loadDriverClass() {}

    /**
     * Perform a connection to the database server.
     */
    void connect();

    /**
     * Creates the tables necessarily used by this software.
     * Do not rewrite the table if already exists.
     */
    void createTables();

    /**
     * Get the connection to the database.
     * @return Connection to the database, null if you could not achieve connection.
     */
    @Nullable Connection getConnection();

    CompletableFuture<LightLoginDbRow> searchRowFromPK(@NotNull String uuid);

    CompletableFuture<LightLoginDbRow> addRow(@NotNull LightLoginDbRow row);

    CompletableFuture<Void> updateRow(@NotNull String uuid, @NotNull LightLoginColumn column, @NotNull Object columnValue);

    CompletableFuture<Boolean> deleteRow(@NotNull String uuid);

    CompletableFuture<List<LightLoginDbRow>> searchRowsPredicate(@NotNull Predicate<? super LightLoginDbRow> predicate);

    default void close() {
        try {
            Connection connection = getConnection();
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }
}
