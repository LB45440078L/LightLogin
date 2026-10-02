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
 * <p>The world is a flat world with no layers and the void biome, which produces an empty world
 * without a custom chunk generator (whose signatures change between server versions). Players are
 * kept here only until they log in, so the main world sees no unauthenticated entities and no
 * chunk activity from them.</p>
 *
 * <p>This is marked experimental in the configuration because a second world has a real cost: it
 * keeps its own chunk cache and spawn area loaded.</p>
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
            String biome = config.endStyleVoid() ? "the_end" : "the_void";
            WorldCreator creator = new WorldCreator(config.worldName())
                    .type(WorldType.FLAT)
                    .generatorSettings("{\"layers\":[],\"biome\":\"minecraft:" + biome + "\"}")
                    .generateStructures(false);
            World created = creator.createWorld();
            if (created == null) {
                plugin.getLogger().warning("The void login world could not be created; login teleport is disabled.");
                return false;
            }
            created.setSpawnLocation(0, (int) config.spawnY(), 0);
            created.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
            created.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            created.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            created.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            created.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            created.setGameRule(GameRule.FALL_DAMAGE, false);
            created.setGameRule(GameRule.DO_FIRE_TICK, false);
            this.world = created;
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to prepare the void login world: " + e.getMessage());
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

    /** The spawn location used for authentication. */
    public Location spawnLocation() {
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
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