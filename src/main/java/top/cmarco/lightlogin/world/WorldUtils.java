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

package top.cmarco.lightlogin.world;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.configuration.LightConfiguration;
import top.cmarco.lightlogin.log.StartupLogo;
import top.cmarco.lightlogin.world.generator.EmptyChunkGenerator;

public final class WorldUtils {

    private WorldUtils() {
        throw new RuntimeException();
    }

    public static World findWorld(@NotNull LightConfiguration lightConfiguration) {
        WorldCreator worldCreator = WorldCreator.name(StartupLogo.getLoadingString(7, lightConfiguration.getChosenLanguage()));
        worldCreator.generator(new EmptyChunkGenerator());

        World.Environment environment = null;
        String configEnv = lightConfiguration.getVoidWorldMode();
        for (World.Environment env : World.Environment.values()) {
            if (env.name().equalsIgnoreCase(configEnv)) {
                environment = env;
                break;
            }
        }

        worldCreator.environment(environment == null ? World.Environment.NORMAL : environment);
        return worldCreator.createWorld();
    }
}
