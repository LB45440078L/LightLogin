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
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.configuration.LightConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public abstract class CredentialPluginDatabase extends AbstractPluginDatabase {

    /* ------------------------------ */

    protected final String username;
    protected final String password;
    protected final String address;
    protected final String databaseName;
    protected final int port;

    /* ------------------------------ */

    protected Connection connection = null;

    protected CredentialPluginDatabase(final @NotNull LightLoginPlugin plugin,
                                       final @NotNull DatabaseType type) {
        super(plugin, type);
        LightConfiguration conf = plugin.getLightConfiguration();
        this.username = conf.getUsername();
        this.password = conf.getPassword();
        this.address = conf.getAddress();
        this.databaseName = conf.getDatabaseName();
        this.port = conf.getPort();
    }


    @NotNull
    protected String createConnectionUrl() {
        return String.format("jdbc:%s://%s:%d/%s",
                this.databaseType.getName(),
                this.address,
                this.port,
                this.databaseName);
    }

    @Override
    public final @Nullable Connection getConnection() {
        return this.connection;
    }

    @Override
    public void connect() {
        if (this.connection != null) {
            return;
        }

        try {
            this.connection = DriverManager.getConnection(this.createConnectionUrl());
        } catch (SQLException exception) {
            super.plugin.getLogger().warning("WARNING! Could not connect to the database.");
            super.plugin.setDisabled(true);
            if (super.plugin.getLightConfiguration().isCrashShutdown()) {
                super.plugin.getLogger().warning("Shutting the server down!");
                super.plugin.getServer().shutdown();
            }
        }
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getAddress() {
        return address;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public int getPort() {
        return port;
    }
}
