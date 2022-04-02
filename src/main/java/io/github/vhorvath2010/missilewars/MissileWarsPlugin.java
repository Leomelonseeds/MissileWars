package io.github.vhorvath2010.missilewars;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.commands.MissileWarsCommand;
import io.github.vhorvath2010.missilewars.commands.SpectateCommand;
import io.github.vhorvath2010.missilewars.decks.DeckManager;
import io.github.vhorvath2010.missilewars.events.ArenaGameruleEvents;
import io.github.vhorvath2010.missilewars.events.ArenaInventoryEvents;
import io.github.vhorvath2010.missilewars.events.ArenaLeaveEvents;
import io.github.vhorvath2010.missilewars.events.StructureItemEvents;
import io.github.vhorvath2010.missilewars.utilities.MissileWarsPlaceholder;

/** Base class for the Missile Wars plugin */
public final class MissileWarsPlugin extends JavaPlugin {

    /** Singleton instance of this class. */
    private static MissileWarsPlugin plugin;
    /** The loaded ArenaManager for the plugin. */
    private ArenaManager arenaManager;
    /** The loaded DeckManager for this plugin. */
    private DeckManager deckManager;

    @Override
    public void onEnable() {
        // Load instance
        plugin = this;

        // Register serializable data
        ConfigurationSerialization.registerClass(Arena.class);

        // Save data files
        saveDefaultConfig();
        saveIfNotPresent("messages.yml");
        saveIfNotPresent("sounds.yml");
        saveIfNotPresent("items.yml");
        saveIfNotPresent("maps.yml");

        // Load commands and events
        getCommand("MissileWars").setExecutor(new MissileWarsCommand());
        getCommand("Spectate").setExecutor(new SpectateCommand());
        Bukkit.getPluginManager().registerEvents(new ArenaGameruleEvents(), this);
        Bukkit.getPluginManager().registerEvents(new ArenaInventoryEvents(), this);
        Bukkit.getPluginManager().registerEvents(new ArenaLeaveEvents(), this);
        Bukkit.getPluginManager().registerEvents(new StructureItemEvents(), this);

        // Load decks
        deckManager = new DeckManager();

        // Load arenas
        arenaManager = new ArenaManager();
        arenaManager.loadArenas();

        // Load placeholders
        new MissileWarsPlaceholder().register();
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
     * Gets the plugin's current DeckManager.
     *
     * @return the plugin's current DeckManager
     */
    public DeckManager getDeckManager() {
        return deckManager;
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
