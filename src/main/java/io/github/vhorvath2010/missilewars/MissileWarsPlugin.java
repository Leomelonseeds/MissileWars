package io.github.vhorvath2010.missilewars;

import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.commands.MissileWarsCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Base class for the Missile Wars plugin */
public final class MissileWarsPlugin extends JavaPlugin {

    /** Singleton instance of this class. */
    private static MissileWarsPlugin plugin;
    /** The loaded ArenaManager for the plugin. */
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        // Load instance
        plugin = this;

        // Save data files
        saveDefaultConfig();
        saveResource("structures.yml", false);
        saveResource("maps.yml", false);

        // Startup schematic management

        // Load commands and events
        getCommand("MissileWars").setExecutor(new MissileWarsCommand());

        // Load arenas
        arenaManager = new ArenaManager();
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

    /**
     * Gets the plugin's current ArenaManager.
     *
     * @return the plugin's current ArenaManager
     */
    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}
