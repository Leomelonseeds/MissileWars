package io.github.vhorvath2010.missilewars;

import org.bukkit.plugin.java.JavaPlugin;

public final class MissileWarsPlugin extends JavaPlugin {

    /** Singleton instance of this class. */
    private static MissileWarsPlugin plugin;

    @Override
    public void onEnable() {
        // Load instance
        plugin = this;

        // Startup schematic management
        saveResource("schematics.yml", false);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    /**
     * Get the instance of the plugin running.
     *
     * @return the instance of the plugin running
     */
    public static MissileWarsPlugin getPlugin() {
        return plugin;
    }
}
