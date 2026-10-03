package dev.lightlogin.paper.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the promise that the login world is empty.
 *
 * <p>This shipped broken once: the world was created with {@code environment(THE_END)} and no
 * generator, so Spigot generated the End's own terrain and the "blank" world had an island in it. The
 * fix is an explicit generator whose every stage is off. These assertions are what stop a future edit
 * from switching a stage back on, which would silently reintroduce terrain.</p>
 *
 * <p>Only the flags and the fixed spawn are asserted, because they are the parts that need no server.
 * The generator writing nothing cannot be observed without a live world, which is why the service also
 * verifies the result at runtime.</p>
 */
class EmptyLoginWorldGeneratorTest {

    private static final double SPAWN_Y = 100.0;

    private static EmptyLoginWorldGenerator generator() {
        // The biome provider is never consulted by anything asserted here, so it can be null.
        return new EmptyLoginWorldGenerator(null, SPAWN_Y);
    }

    @Test
    @DisplayName("no generation stage is enabled, in any dimension")
    void everyStageIsOff() {
        EmptyLoginWorldGenerator generator = generator();
        assertFalse(generator.shouldGenerateNoise(), "noise would place stone");
        assertFalse(generator.shouldGenerateSurface(), "surface would place dirt and grass");
        assertFalse(generator.shouldGenerateBedrock(), "bedrock would place a floor");
        assertFalse(generator.shouldGenerateCaves(), "caves carve terrain out of nothing");
        assertFalse(generator.shouldGenerateDecorations(), "decorations place trees and plants");
        assertFalse(generator.shouldGenerateMobs(), "mobs must not be generated");
        assertFalse(generator.shouldGenerateStructures(), "structures include End gateways");
    }

    @Test
    @DisplayName("nothing is allowed to spawn naturally")
    void nothingSpawns() {
        // The world is never touched: the answer must not depend on it.
        assertFalse(generator().canSpawn(null, 0, 0));
        assertFalse(generator().canSpawn(null, 1_000, -1_000));
    }

    @Test
    @DisplayName("the spawn is fixed and centred on the block")
    void fixedSpawn() {
        Location spawn = generator().getFixedSpawnLocation(null, new Random(1L));
        assertEquals(0.5, spawn.getX());
        assertEquals(SPAWN_Y, spawn.getY());
        assertEquals(0.5, spawn.getZ());
    }
}