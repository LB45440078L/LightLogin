package dev.lightlogin.paper.world;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down what the login world's emptiness check considers empty.
 *
 * <p>This exists because the check got it wrong in a way that only showed up on the second startup.
 * It compared each block against {@link Material#AIR} for equality, but an empty world in a modern
 * dimension is filled with {@code void_air}. A perfectly empty End world was therefore reported as
 * full terrain, and the first startup passed only because the spawn area had not been generated yet,
 * so there were no blocks to misjudge. The world worked; the check was lying.</p>
 */
class VoidWorldServiceTest {

    @Test
    @DisplayName("every air variant counts as empty")
    void airVariantsAreEmpty() {
        assertTrue(VoidWorldService.isAirLike(Material.AIR));
        // The one that mattered: an empty End contains void_air, not air.
        assertTrue(VoidWorldService.isAirLike(Material.VOID_AIR),
                "an empty End world is void_air; treating only AIR as empty rejects a blank world");
        assertTrue(VoidWorldService.isAirLike(Material.CAVE_AIR));
        assertTrue(VoidWorldService.isAirLike(Material.STRUCTURE_VOID));
    }

    @Test
    @DisplayName("real blocks are not empty")
    void realBlocksAreNotEmpty() {
        // What the End's own generator would leave behind, which the check must still catch.
        assertFalse(VoidWorldService.isAirLike(Material.END_STONE));
        assertFalse(VoidWorldService.isAirLike(Material.OBSIDIAN));
        assertFalse(VoidWorldService.isAirLike(Material.BEDROCK));
        assertFalse(VoidWorldService.isAirLike(Material.STONE));
    }
}