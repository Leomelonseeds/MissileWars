package io.github.vhorvath2010.missilewars;

import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.commands.MissileWarsCommand;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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

        // Register serializable data
        ConfigurationSerialization.registerClass(Arena.class);

        // Save data files
        saveDefaultConfig();
        saveIfNotPresent("default-settings.yml");
        saveIfNotPresent("messages.yml");
        saveIfNotPresent("sounds.yml");
        saveIfNotPresent("items.yml");
        saveIfNotPresent("maps.yml");

        // Startup schematic management

        // Load commands and events
        getCommand("MissileWars").setExecutor(new MissileWarsCommand());

        // Load arenas
        arenaManager = new ArenaManager();
        arenaManager.loadArenas();
    }

    /**
     * Save a resource if it is not present
     *
     * @param resourcePath the path to the resource
     */
    private void saveIfNotPresent(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    @Override
    public void onDisable() {
        // Save arenas to data file
        arenaManager.saveArenas();
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
