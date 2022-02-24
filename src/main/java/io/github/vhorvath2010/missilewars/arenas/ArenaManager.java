package io.github.vhorvath2010.missilewars.arenas;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.schematics.VoidChunkGenerator;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Class to manager all Missile Wars arenas. */
public class ArenaManager {

    /** A list of all loaded arenas. */
    private List<Arena> loadedArenas;

    /** Default constructor */
    public ArenaManager() {
        loadedArenas = new ArrayList<>();
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
     * Create a new Arena given a name with default player capacity.
     *
     * @param name the name of the Arena
     * @param creator the creator of the world
     * @return true if the Arena was created, otherwise false
     */
    public boolean createArena(String name, Player creator) {
        // Ensure arena world doesn't exist
        if (Bukkit.getWorld("arena_" + name) != null) {
            creator.sendMessage(ChatColor.RED + "A world already exists for that arena!");
            return false;
        }

        // Create Arena world
        creator.sendMessage(ChatColor.GREEN + "Generating arena world...");
        WorldCreator arenaCreator = new WorldCreator("arena_" + name);
        arenaCreator.generator(new VoidChunkGenerator());
        World arenaWorld = arenaCreator.createWorld();
        creator.sendMessage(ChatColor.GREEN + "Arena world generated!");

        // Create Arena lobby
        if (!SchematicManager.spawnFAWESchematic("lobby", arenaWorld)) {
            creator.sendMessage(ChatColor.RED + "Error generating lobby! Are schematic files present?");
        }

        // Register Arena
        loadedArenas.add(new Arena(name,
                ConfigUtils.getConfigFile("default-settings.yml").getInt("arena-cap")));
        return true;
    }

}
