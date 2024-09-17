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
import top.cmarco.lightlogin.LightLoginPlugin;

/**
 * An abstract implementation for PluginDatabase.
 */
public abstract class AbstractPluginDatabase implements PluginDatabase {

    protected final LightLoginPlugin plugin;
    protected final DatabaseType databaseType;

    protected AbstractPluginDatabase(final @NotNull LightLoginPlugin plugin,
                                     final @NotNull DatabaseType databaseType) {
        this.plugin = plugin;
        this.databaseType = databaseType;
    }

    @Override
    public void loadDriverClass() {
        try {
            final Class<?> driverClass = Class.forName(this.databaseType.getClassName());
        } catch (ClassNotFoundException exception) {
            this.plugin.setDisabled(true);
            this.plugin.getLogger().warning("WARNING! Could not load database driver class \"" + this.databaseType.getClassName() + "\"");
            if (this.plugin.getLightConfiguration().isCrashShutdown()) {
                this.plugin.getLogger().warning("Shutting down the server. . .");
                this.plugin.getServer().shutdown();
            }
        }
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public LightLoginPlugin getPlugin() {
        return plugin;
    }
}
