package io.github.vhorvath2010.missilewars.arenas;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.events.ArenaInventoryEvents;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.schematics.VoidChunkGenerator;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Class to manager all Missile Wars arenas. */
public class ArenaManager {

    /** A list of all loaded arenas. */
    private List<Arena> loadedArenas;

    /** Default constructor */
    public ArenaManager() {
        loadedArenas = new ArrayList<>();
    }

    /** Load arenas from data file */
    public void loadArenas() {
        File arenaFile = new File(MissileWarsPlugin.getPlugin().getDataFolder(), "arenas.yml");

        // Acquire arenas from data file
        if (arenaFile.exists()) {
            FileConfiguration arenaConfig = new YamlConfiguration();
            try {
                arenaConfig.load(arenaFile);
                if (arenaConfig.contains("arenas")) {
                    loadedArenas = (List<Arena>) arenaConfig.get("arenas");
                }
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }
        }

        // Load worlds for arenas
        assert loadedArenas != null;
        for (Arena arena : loadedArenas) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "Loading arena: " + arena.getName() + "...");
            new WorldCreator("mwarena_" + arena.getName()).createWorld();
        }
    }

    /** Save arenas from data file */
    public void saveArenas() {
        File arenaFile = new File(MissileWarsPlugin.getPlugin().getDataFolder(), "arenas.yml");
        FileConfiguration arenaConfig = new YamlConfiguration();
        arenaConfig.set("arenas", loadedArenas);
        try {
            arenaConfig.save(arenaFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get an Arena by name.
     *
     * @param name the name of the Arena
     * @return the Arena, or null if it doesn't exist
     */
    public Arena getArena(String name) {
        for (Arena arena : loadedArenas) {
            if (arena.getName().equalsIgnoreCase(name)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Get an Arena by index.
     *
     * @param index the index of the Arena
     * @return the Arena, or null if it doesn't exist
     */
    public Arena getArena(int index) {
        if (index < 0 || index >= loadedArenas.size()) {
            return null;
        }
        return loadedArenas.get(index);
    }

    /**
     * Get the Arena that a player with a given UUID is in.
     *
     * @param id the UUID of the player
     * @return the Arena that the player is in, or null
     */
    public Arena getArena(UUID id) {
        for (Arena arena : loadedArenas) {
            if (arena.isInArena(id)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Create a new Arena given a name with default player capacity.
     *
     * @param name the name of the Arena
     * @param creator the creator of the world
     * @return true if the Arena was created, otherwise false
     */
    public boolean createArena(String name, CommandSender creator) {
        // Ensure arena world doesn't exist
        if (Bukkit.getWorld("mwarena_" + name) != null) {
            creator.sendMessage(ChatColor.RED + "A world already exists for that arena!");
            return false;
        }

        // Create Arena world
        creator.sendMessage(ChatColor.GREEN + "Generating arena world...");
        WorldCreator arenaCreator = new WorldCreator("mwarena_" + name);
        arenaCreator.generator(new VoidChunkGenerator());
        World arenaWorld = arenaCreator.createWorld();
        assert arenaWorld != null;
        creator.sendMessage(ChatColor.GREEN + "Arena world generated!");

        // Create Arena lobby
        creator.sendMessage(ChatColor.GREEN + "Generating lobby...");
        if (!SchematicManager.spawnFAWESchematic("lobby", arenaWorld)) {
            creator.sendMessage(ChatColor.RED + "Error generating lobby! Are schematic files present?");
            return false;
        } else {
            creator.sendMessage(ChatColor.GREEN + "Lobby generated!");
        }

        // Setup world spawn to lobby center
        FileConfiguration schematicConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "maps.yml");
        Vector spawnVector = SchematicManager.getVector(schematicConfig, "lobby.pos");
        arenaWorld.setSpawnLocation(spawnVector.getBlockX(), spawnVector.getBlockY(), spawnVector.getBlockZ());

        // Spawn barrier wall
        FileConfiguration settings = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "default-settings.yml");
        int length = settings.getInt("barrier.length");
        int x = settings.getInt("barrier.center.x");
        int zCenter = settings.getInt("barrier.center.z");
        for (int y = 0; y <= 256; ++y) {
            for (int z = zCenter - length / 2; z < zCenter + length / 2; z++) {
                arenaWorld.getBlockAt(x, y, z).setType(Material.BARRIER);
            }
        }

        // Spawn arena with default map
        Arena arena = new Arena(name, settings.getInt("arena-cap"));
        creator.sendMessage(ChatColor.GREEN + "Generating default map...");
        if (arena.generateMap("default-map")) {
            creator.sendMessage(ChatColor.GREEN + "Default map generated!");
        } else {
            creator.sendMessage(ChatColor.RED + "Error generating default map! Are schematic files present?");
            return false;
        }

        // Register Arena
        loadedArenas.add(arena);
        return true;
    }

    /**
     * Open the arena selector for a given player.
     *
     * @param player the player
     */
    public void openArenaSelector(Player player) {
        // Stop if player is already in an arena
        if (getArena(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "You are already in a game!");
            return;
        }
        Inventory selector = Bukkit.createInventory(null, 27,
                ConfigUtils.getConfigText("inventories.game-selector.title", player, null));

        // Add Arena items
        for (Arena arena : loadedArenas) {
            ItemStack arenaItem = new ItemStack(Material.TNT, Math.max(1, arena.getNumPlayers()));
            ItemMeta arenaItemMeta = arenaItem.getItemMeta();
            assert arenaItemMeta != null;
            arenaItemMeta.setDisplayName(ConfigUtils.getConfigText("inventories.game-selector.game-item.name",
                    player, arena));
            arenaItemMeta.setLore(ConfigUtils.getConfigTextList("inventories.game-selector.game-item.lore",
                    player, arena));
            arenaItem.setItemMeta(arenaItemMeta);
            selector.addItem(arenaItem);
        }
        ArenaInventoryEvents.selectingArena.add(player);
        player.openInventory(selector);
    }

}
