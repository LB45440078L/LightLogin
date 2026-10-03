package dev.lightlogin.paper.world;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down how the login world decides what is empty and what may be cleared.
 *
 * <p>Both predicates here exist because the check got them wrong in ways that only showed up on a
 * running server. Comparing against {@link Material#AIR} for equality rejected a perfectly empty End
 * world, because an empty world in a modern dimension is filled with {@code void_air}. And treating
 * the End's exit portal as terrain rejected a world that was fine.</p>
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
        // What the End's own generator would leave behind, which the scan must still find.
        assertFalse(VoidWorldService.isAirLike(Material.END_STONE));
        assertFalse(VoidWorldService.isAirLike(Material.OBSIDIAN));
        assertFalse(VoidWorldService.isAirLike(Material.BEDROCK));
        assertFalse(VoidWorldService.isAirLike(Material.STONE));
    }

    @Test
    @DisplayName("a stray artefact is cleared, generated terrain is not")
    void clearingIsBounded() {
        // What the End's exit portal looks like once widened by the sampling margin: a small cluster
        // sitting on the world floor. This is the case that must be cleared silently.
        long portal = 17L * 3L * 17L;
        assertTrue(VoidWorldService.isArtefactSized(portal), "the exit portal must be cleared");

        // What the End's island would look like: 49 by 49 columns over roughly 28 layers. Deleting
        // that on a guess would be worse than the problem it solves.
        long island = 49L * 28L * 49L;
        assertFalse(VoidWorldService.isArtefactSized(island),
                "generated terrain must never be deleted on a guess");

        // A single block, and an empty region, are both trivially clearable.
        assertTrue(VoidWorldService.isArtefactSized(1L));
        assertTrue(VoidWorldService.isArtefactSized(0L));
    }
}