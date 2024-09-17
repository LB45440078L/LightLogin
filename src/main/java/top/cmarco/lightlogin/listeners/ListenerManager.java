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

package top.cmarco.lightlogin.listeners;

import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;

import java.util.HashMap;
import java.util.Map;

public final class ListenerManager {

    private final LightLoginPlugin plugin;
    private final Map<String, NamedListener> registeredListeners = new HashMap<>();

    public ListenerManager(@NotNull LightLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public void addAllListeners() {
        PlayerUnloggedListener playerUnloggedListener = new PlayerUnloggedListener(plugin);
        LoginAuthenticatorListener loginAuthenticatorListener = new LoginAuthenticatorListener(plugin);
        AuthenticationListener authenticationListener = new AuthenticationListener(plugin);
        PasswordObfuscationListener passwordObfuscationListener = new PasswordObfuscationListener(plugin);

        this.registeredListeners.put(playerUnloggedListener.getName(), playerUnloggedListener);
        this.registeredListeners.put(loginAuthenticatorListener.getName(), loginAuthenticatorListener);
        this.registeredListeners.put(authenticationListener.getName(), authenticationListener);
        this.registeredListeners.put(passwordObfuscationListener.getName(), passwordObfuscationListener);
    }

    public void registerAllAddedListeners() {
        PluginManager pluginManager = this.plugin.getServer().getPluginManager();
        this.registeredListeners.values().forEach(listener -> pluginManager.registerEvents(listener, this.plugin));
    }
}
