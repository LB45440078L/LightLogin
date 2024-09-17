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

package top.cmarco.lightlogin.world.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class EmptyChunkGenerator extends ChunkGenerator {

    private static Location cachedSpawnLoc = null;

    public static void generateBarrierContainer(@NotNull final World world) {
        for (int x = -3; x < 3; x++) {
            for (int z = -3; z < 3; z++) {
                for (int y = 237; y < 245; y++) {
                    Block block = world.getBlockAt(x,y,z);
                    if (block.getType() == Material.BARRIER) {
                        continue;
                    }
                    block.setType(Material.BARRIER);
                }
            }
        }
        world.getBlockAt(0, 240, 0).setType(Material.AIR);
        world.getBlockAt(0, 241, 0).setType(Material.AIR);
    }

    @Nullable
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        if (cachedSpawnLoc == null) {
            cachedSpawnLoc = new Location(world, 0.5f, 240.0001f, 0.5f, 0f, 0f);
        }

        return cachedSpawnLoc;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return x == 0 && z == x;
    }

    private static final List<BlockPopulator> EMPTY_BLOCKPOPULATOR = Collections.emptyList();

    @NotNull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return EMPTY_BLOCKPOPULATOR;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

}
