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

public enum ConfigurationFiles {

    ENGLISH("config_english.yml"),
    SPANISH("config_spanish.yml"),
    FRENCH("config_french.yml"),
    HEBREW("config_hebrew.yml"),
    ITALIAN("config_italian.yml"),
    RUSSIAN("config_russian.yml"),
    FILIPINO("config_filipino.yml"),
    PORTUGUESE("config_portuguese.yml");

    private final String filename;

    ConfigurationFiles(final String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }
}
