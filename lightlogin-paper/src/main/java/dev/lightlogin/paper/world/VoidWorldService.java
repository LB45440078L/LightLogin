package dev.lightlogin.paper.world;

import dev.lightlogin.core.config.VoidWorldConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * A blank world used to park players while they authenticate.
 *
 * <p>The world is generated with no terrain layers, so it is genuinely empty, and it is created in
 * whichever dimension the configuration names: the End for its dark sky and ambience, or the
 * overworld for a normal sky. Both variants are custom worlds of the configured name, so neither
 * touches the server's real end or overworld. Players are kept here only until they log in, so the
 * main world sees no unauthenticated entities and no chunk activity from them.</p>
 *
 * <p>Everything that could make the world cost anything is switched off: no terrain, no structures,
 * no mob spawning of any kind, no fire tick, no mob griefing, a random tick speed of zero, and the
 * spawn area is not force-loaded. The result is a world that exists as a place to stand and nothing
 * else.</p>
 *
 * <p>This is marked experimental in the configuration because a second world still has a real cost:
 * it keeps its own chunk cache and appears in the world list.</p>
 */
public final class VoidWorldService {

    private final JavaPlugin plugin;
    private final VoidWorldConfig config;
    private World world;

    public VoidWorldService(JavaPlugin plugin, VoidWorldConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Creates or loads the login world.
     *
     * @return true when the world is ready for use
     */
    public boolean initialise() {
        if (!config.enabled()) {
            return false;
        }
        try {
            World existing = Bukkit.getWorld(config.worldName());
            if (existing != null) {
                this.world = existing;
                return true;
            }

            boolean end = config.dimension() == VoidWorldConfig.Dimension.END;
            World.Environment environment = end ? World.Environment.THE_END : World.Environment.NORMAL;
            // Both variants are flat worlds with no layers. The biome is chosen per dimension purely
            // for ambience: an End-dimension world using the_void would look like a generation bug.
            String biome = end ? "the_end" : "the_void";
            WorldCreator creator = new WorldCreator(config.worldName())
                    .environment(environment)
                    .type(WorldType.FLAT)
                    .generatorSettings("{\"layers\":[],\"biome\":\"minecraft:" + biome + "\"}")
                    .generateStructures(false);
            World created = creator.createWorld();
            if (created == null) {
                plugin.getLogger().warning("The blank login world could not be created; login teleport is disabled.");
                return false;
            }
            created.setSpawnLocation(0, (int) config.spawnY(), 0);
            created.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
            // Spigot names these after the vanilla game rule ids. The old Bukkit aliases
            // (DO_MOB_SPAWNING, DO_DAYLIGHT_CYCLE, ANNOUNCE_ADVANCEMENTS and friends) do not exist
            // in this API, and doFireTick has been replaced by a fire spread radius.
            created.setGameRule(GameRule.SPAWN_MOBS, false);
            created.setGameRule(GameRule.SPAWN_PATROLS, false);
            created.setGameRule(GameRule.SPAWN_WANDERING_TRADERS, false);
            created.setGameRule(GameRule.ADVANCE_TIME, false);
            created.setGameRule(GameRule.ADVANCE_WEATHER, false);
            created.setGameRule(GameRule.SHOW_ADVANCEMENT_MESSAGES, false);
            created.setGameRule(GameRule.FALL_DAMAGE, false);
            created.setGameRule(GameRule.MOB_GRIEFING, false);
            created.setGameRule(GameRule.SPAWN_PHANTOMS, false);
            // Fire cannot spread, so nothing in an empty world can start or carry one.
            created.setGameRule(GameRule.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            // Nothing in this world should ever change: it exists only as a place to stand.
            created.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            // The spawn area is not force-loaded; the world is occupied only while someone logs in.
            created.setKeepSpawnInMemory(false);
            this.world = created;
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to prepare the blank login world: " + e.getMessage());
            return false;
        }
    }

    /** Whether the login world is available. */
    public boolean isAvailable() {
        return world != null;
    }

    /** The login world, if any. */
    public Optional<World> world() {
        return Optional.ofNullable(world);
    }

    /** The spawn location used for authentication, centred on the block. */
    public Location spawnLocation() {
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
        // Centre on the block so a player never spawns on a corner and drifts.
        spawn.setX(0.5);
        spawn.setZ(0.5);
        spawn.setY(config.spawnY());
        return spawn;
    }

    /** Teleports a player into the login world. */
    public boolean teleportIn(Player player) {
        Location target = spawnLocation();
        if (target == null) {
            return false;
        }
        return player.teleport(target);
    }

    /** The world name, for logging. */
    public String worldName() {
        return config.worldName();
    }

    /** Configuration accessor. */
    public VoidWorldConfig config() {
        return config;
    }
}