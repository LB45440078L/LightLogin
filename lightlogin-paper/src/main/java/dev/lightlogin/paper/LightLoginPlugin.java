package dev.lightlogin.paper;

import dev.lightlogin.paper.bootstrap.LightLoginBootstrap;
import dev.lightlogin.paper.bootstrap.PluginContext;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Paper plugin entry point.
 *
 * <p>Deliberately thin: it owns the bootstrap's lifecycle and nothing else, so all the wiring lives
 * in one testable place and the plugin class has no logic to get out of step with it. A failure
 * during enable is logged with its cause and the plugin disables itself rather than running in a
 * half-initialised state.</p>
 */
public final class LightLoginPlugin extends JavaPlugin {

    private LightLoginBootstrap bootstrap;

    @Override
    public void onEnable() {
        bootstrap = new LightLoginBootstrap(this);
        try {
            bootstrap.enable();
        } catch (Throwable e) {
            getLogger().severe("LightLogin failed to start: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.disable();
        }
    }

    /** The wired services, for the plugin's own components and API consumers. */
    public PluginContext context() {
        return bootstrap == null ? null : bootstrap.context();
    }

    /** Reloads runtime-changeable settings. */
    public void reloadPlugin() {
        if (bootstrap != null) {
            bootstrap.reload();
        }
    }
}