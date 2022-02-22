package io.github.vhorvath2010.missilewars.schematics;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/** A class to handle loading/placing of NBT and WorldEdit schematics */
public class SchematicManager {

    /**
     * Spawn a schematic at a given location with a given rotation.
     *
     * @param schematicName the name of the schematic
     * @param loc the location to spawn the schematic (pre-offset)
     * @param rotation the rotation to be applied to the schematic after the offset
     * @return true if the NBT schematic was found and spawned, otherwise false
     */
    public boolean spawnNBTSchematic(String schematicName, Location loc, StructureRotation rotation) {
        // Attempt to get schematic file
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        File offsetDataFile = new File(plugin.getDataFolder(), "schematics.yml");
        FileConfiguration schematicConfig = new YamlConfiguration();
        if (offsetDataFile.exists()) {
            try {
                schematicConfig.load(offsetDataFile);
            } catch (IOException | InvalidConfigurationException e) {
                return false;
            }
        } else {
            return false;
        }

        // Attempt to get structure file
        File schematicFile = new File(plugin.getDataFolder() + File.pathSeparator + "schematics",
                schematicConfig.getString(schematicName + ".file"));
        if (!schematicFile.exists()) {
            return false;
        }

        // Load schematic data
        StructureManager manager = Bukkit.getStructureManager();
        Structure structure;
        try {
            structure = manager.loadStructure(schematicFile);
        } catch (IOException e) {
            return false;
        }

        // Apply offset
        Location spawnLoc = loc;
        loc.add(getOffset(schematicConfig, schematicName + ".offset"));

        // Place schematic
        structure.place(spawnLoc, true, rotation, Mirror.NONE, 0, 1, new Random());
        return true;
    }

    /**
     * Get the offset vector for a given schematic path in a given config.
     *
     * @param config the config to get data from
     * @param path the path to the x, y, z offset data
     * @return the x, y, z offset as a vector
     */
    private Vector getOffset(FileConfiguration config, String path) {
        Vector offset = new Vector();
        if (config.contains(path)) {
            offset = new Vector(config.getDouble(path + ".x"), config.getDouble(path + ".y"),
                    config.getDouble(path + ".z"));
        }
        return offset;
    }

}
