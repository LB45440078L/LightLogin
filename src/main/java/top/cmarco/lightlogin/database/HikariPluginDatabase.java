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


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;

import java.sql.SQLException;

public abstract class HikariPluginDatabase extends CredentialPluginDatabase {

    protected final HikariConfig hikariConfig;
    protected final HikariDataSource hikariDataSource;

    protected HikariPluginDatabase(@NotNull LightLoginPlugin plugin, @NotNull DatabaseType databaseType) {
        super(plugin, databaseType);
        this.hikariConfig = new HikariConfig();
        this.hikariConfig.setJdbcUrl(super.createConnectionUrl());
        this.hikariConfig.setUsername(super.username);
        this.hikariConfig.setPassword(super.password);
        this.hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        this.hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        this.hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.hikariDataSource = new HikariDataSource(this.hikariConfig);
    }

    @Override
    public void connect() {

        if (this.connection != null) {
            return;
        }

        try {
            this.connection = this.hikariDataSource.getConnection();
        } catch (SQLException exception) {
            super.plugin.getLogger().warning("WARNING! Could not connect to the database using HikariCP.");
            super.plugin.setDisabled(true);
            if (super.plugin.getLightConfiguration().isCrashShutdown()) {
                super.plugin.getLogger().warning("Shutting the server down!");
                super.plugin.getServer().shutdown();
            }
        }
    }
}
