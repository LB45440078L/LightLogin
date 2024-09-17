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

package top.cmarco.lightlogin.configuration;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.log.StartupLogo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public final class ConfigUtils {

    private ConfigUtils() {
        throw new RuntimeException();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static YamlConfiguration createCustomConfig(@NotNull ConfigurationFiles configurationFile, @NotNull LightLoginPlugin plugin) {
        File customConfigFile = new File(plugin.getDataFolder(), configurationFile.getFilename());

        if (!customConfigFile.exists()) {
            customConfigFile.getParentFile().mkdirs();
            plugin.saveResource(configurationFile.getFilename(), false);
        }

        final YamlConfiguration customConfig = new YamlConfiguration();

        try {

            YamlConfiguration original = new YamlConfiguration();
            InputStream stream = plugin.getResource(configurationFile.getFilename());
            assert stream != null;
            InputStreamReader streamReader = new InputStreamReader(stream);
            original.load(streamReader);
            customConfig.load(customConfigFile);

            Map<String, Object> currentValues = customConfig.getValues(true);
            Map<String, Object> originalValues = original.getValues(true);

            originalValues.forEach((a,b) -> {
                if (!currentValues.containsKey(a)) {
                    customConfig.set(a, b);
                }
            });

            customConfig.save(customConfigFile);

            streamReader.close();
            stream.close();

            return customConfig;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning(StartupLogo.getLoadingString(8, plugin.getLightConfiguration().getChosenLanguage()) + configurationFile.getFilename());
            plugin.getLogger().warning(exception.getLocalizedMessage());
        }
        return null;
    }

    public static ConfigurationFiles chosenLanguage(@NotNull LightLoginPlugin plugin) {
        final String language = plugin.getConfig().getString(StartupLogo.getLoadingString(18, ConfigurationFiles.ENGLISH));
        ConfigurationFiles chosenLanguage = null;

        for (ConfigurationFiles value : ConfigurationFiles.values()) {
            if (value.name().equalsIgnoreCase(language)) {
                chosenLanguage = value;
                break;
            }
        }

        if (chosenLanguage == null) {
            plugin.getLogger().warning(StartupLogo.getLoadingString(19, plugin.getLightConfiguration().getChosenLanguage())+ language);
            plugin.getLogger().warning(StartupLogo.getLoadingString(20, plugin.getLightConfiguration().getChosenLanguage()));
            chosenLanguage = ConfigurationFiles.ENGLISH;
        }

        return chosenLanguage;
    }
}
