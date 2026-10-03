package dev.lightlogin.paper.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/**
 * Reports one biome everywhere.
 *
 * <p>An empty world still has to answer the biome question, and the answer is visible: it decides the
 * fog, the water colour and the weather of the dimension's ambience. Reporting {@code the_end}
 * throughout is what makes the login world read as the End rather than as an unfinished void.</p>
 */
public final class SingleBiomeProvider extends BiomeProvider {

    private final Biome biome;

    public SingleBiomeProvider(Biome biome) {
        this.biome = biome;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return biome;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return List.of(biome);
    }
}