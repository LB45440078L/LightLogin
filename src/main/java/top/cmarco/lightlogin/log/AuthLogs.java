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

package top.cmarco.lightlogin.log;

import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

public final class AuthLogs {
    private final LightLoginPlugin plugin;
    private final TreeMap<Long, String> logs = new TreeMap<>();
    public static final DateTimeFormatter FORMATTER_TXT = DateTimeFormatter.ofPattern("MM/dd/yyyy - hh:mm:ssa");
    public static final DateTimeFormatter FORMATTER_FILENAME = DateTimeFormatter.ofPattern("MM_dd_yyyy_hh_mm_ss");

    public AuthLogs(LightLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveLogs() {
        String logFilename = "LOG_" + LocalDateTime.now().format(FORMATTER_FILENAME) + ".txt";

        File pluginLogsFolder = new File(plugin.getDataFolder(), "logs");

        if (!pluginLogsFolder.exists()) {
            pluginLogsFolder.mkdir();
        }

        File newLogFile = new File(pluginLogsFolder, logFilename);

        // Create parent directories if they do not exist
        newLogFile.getParentFile().mkdirs();

        if (newLogFile.exists()) {
            return;
        }

        try {
            plugin.sendConsoleColoured("&7[ &a&l. . .&r &7] &eStarting to save auth log file &7(&a" + newLogFile.getName() + "&7)");
            boolean created = newLogFile.createNewFile();

            if (!created) {
                plugin.getLogger().warning("Something went wrong when saving log file " + logFilename);
                return;
            }

            final BufferedWriter writer = new BufferedWriter(new FileWriter(newLogFile));

            for (Map.Entry<Long, String> entry : this.logs.entrySet()) {
                String s = "[" + Instant.ofEpochMilli(entry.getKey()).atZone(ZoneId.systemDefault()).format(FORMATTER_TXT) + "]: ";
                writer.append(s).append(entry.getValue());
                writer.newLine();
            }

            writer.close();
            plugin.sendConsoleColoured("&7[ &a&l✔&r &7] &aCorrectly saved log file!");

        } catch (IOException exception) {
            plugin.getLogger().warning(exception.getLocalizedMessage());
        }
    }

    public void add(@NotNull String message) {
        this.logs.put(System.currentTimeMillis(), message);
    }

}
