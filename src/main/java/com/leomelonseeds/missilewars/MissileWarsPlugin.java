package com.leomelonseeds.missilewars;

import java.io.File;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.tracker.TrackedMissile;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.invs.InventoryManager;
import com.leomelonseeds.missilewars.listener.ArenaGameruleListener;
import com.leomelonseeds.missilewars.listener.ArenaInventoryListener;
import com.leomelonseeds.missilewars.listener.CustomItemListener;
import com.leomelonseeds.missilewars.listener.DefuseHelper;
import com.leomelonseeds.missilewars.listener.MiscListener;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.JSONManager;
import com.leomelonseeds.missilewars.utilities.MissileWarsPlaceholder;
import com.leomelonseeds.missilewars.utilities.SQLManager;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;

/** Base class for the Missile Wars plugin */
public final class MissileWarsPlugin extends JavaPlugin {

    /** Singleton instance of this class. */
    private static MissileWarsPlugin plugin;
    /** The loaded ArenaManager for the plugin. */
    private ArenaManager arenaManager;
    /** The loaded DeckManager for this plugin. */
    private DeckManager deckManager;
    /** The loaded economy for this plugin */
    private static Economy econ;
    /** The loaded chat API for this plugin */
    private static Chat chat;
    /** The loaded sql manager for this plugin */
    private SQLManager sqlManager;
    /** The loaded json manager for this plugin */
    private JSONManager jsonManager;
    /** The loaded json manager for this plugin */
    private InventoryManager invManager;

    @Override
    public void onEnable() {
        // Load instance
        plugin = this;

        // Register serializable data
        ConfigurationSerialization.registerClass(Arena.class);

        // Save data files
        log("Loading and saving config files...");
        saveDefaultConfig();
        saveIfNotPresent("messages.yml");
        saveIfNotPresent("sounds.yml");
        saveIfNotPresent("items.yml");
        saveIfNotPresent("maps.yml");
        saveIfNotPresent("ranks.yml");
        saveIfNotPresent("default.json");
        saveIfNotPresent("cosmetics/death-messages.yml");
        log("Loaded all config files.");

        // Load commands and events
        log("Loading commands and events...");
        DefuseHelper dfh = new DefuseHelper(this);
        getCommand("MissileWars").setExecutor(new MissileWarsCommand());
        Bukkit.getPluginManager().registerEvents(new ArenaGameruleListener(), this);
        Bukkit.getPluginManager().registerEvents(new ArenaInventoryListener(), this);
        Bukkit.getPluginManager().registerEvents(new MiscListener(), this);
        Bukkit.getPluginManager().registerEvents(new CustomItemListener(), this);
        Bukkit.getPluginManager().registerEvents(dfh, this);
        ProtocolLibrary.getProtocolManager().addPacketListener(dfh);
        log("Commands and events loaded.");

        // Load decks
        log("Loading deck manager...");
        deckManager = new DeckManager(this);
        log("Deck manager loaded.");

        // Load player deck cache
        log("Starting player deck cache...");
        jsonManager = new JSONManager(this);
        log("Player deck cache loaded!");
        
        // Load player inventory cache
        log("Starting player inventory cache...");
        invManager = new InventoryManager();
        log("Player inventory cache loaded!");

        // Load arenas
        log("Loading up arenas...");
        arenaManager = new ArenaManager(this);
        arenaManager.loadArenas();
        setupSpeeds();
        log("All arenas ready to go.");

        // Load placeholders
        log("Hooking into PlaceholderAPI...");
        new MissileWarsPlaceholder().register();
        log("All placeholders registered.");

        // Load economy
        log("Hooking into Vault...");
        setupVault();
        log("Economy setup complete.");

        // Load MySQL
        log("Setting up the MySQL database...");
        setupDatabase();
        log("MySQL setup complete.");

        log("Missile Wars is ready to play :)");
    }
    
    /**
     * Setup missile speeds for user in TrackedMissile
     */
    private void setupSpeeds() {
        TrackedMissile.speeds.put(1.7, 12);
        TrackedMissile.speeds.put(2.0, 10);
        TrackedMissile.speeds.put(2.2, 9);
        TrackedMissile.speeds.put(2.5, 8);
        TrackedMissile.speeds.put(3.3, 6);
        TrackedMissile.speeds.put(4.4, 9);
    }

    /**
     * Setup the economy manager
     */
    private void setupVault() {
        RegisteredServiceProvider<Economy> rspE = getServer().getServicesManager().getRegistration(Economy.class);
        econ = rspE.getProvider();
        RegisteredServiceProvider<Chat> rspC = getServer().getServicesManager().getRegistration(Chat.class);
        chat = rspC.getProvider();
    }

    /**
     * Setup the server database
     */
    private void setupDatabase() {
        sqlManager = new SQLManager(this);
        log("Testing MySQL connection...");
        if (!sqlManager.testConnection()) {
            log("The plugin cannot function without the database. Shutting down now...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        log("MySQL connection test complete.");

        log("Setting up default tables...");
        sqlManager.setupTables();
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
        log("Saving arenas to file...");
        arenaManager.saveArenas();
        log("Arenas saved!");

        // Save all player decks
        log("Saving player deck configurations...");
        jsonManager.saveAll(false);
        log("Player decks saved!");
        
        // Save all player inventories
        log("Saving player inventories...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (getArenaManager().getArena(player.getUniqueId()) == null) {
                InventoryUtils.saveInventory(player, false);
            }
        }
        log("Player inventories saved!");

        // Close database connection
        log("Closing MySQL connection...");
        sqlManager.onDisable();
        log("Connection closed. Have a nice day!");
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

    /**
     * Gets the plugin's current Economy
     *
     * @return the plugin's current Economy
     */
    public Economy getEconomy() {
        return econ;
    }

    /**
     * Gets the vault chat API
     *
     * @return the vault chat API
     */
    public Chat getChat() {
        return chat;
    }

    /**
     * Gets the plugin's current database manager
     *
     * @return the plugin's database manager
     */
    public SQLManager getSQL() {
        return sqlManager;
    }

    /**
     * Gets the plugin's current json manager
     *
     * @return the plugin's json manager
     */
    public JSONManager getJSON() {
        return jsonManager;
    }
    
    /**
     * Gets the plugin's current inv manager
     *
     * @return the plugin's inv manager
     */
    public InventoryManager getInvs() {
        return invManager;
    }

    /**
     * Send a logging message to the console
     *
     * @param message
     */
    public void log(String message) {
        Bukkit.getLogger().log(Level.INFO, "[MissileWars] " + message);
    }
}
