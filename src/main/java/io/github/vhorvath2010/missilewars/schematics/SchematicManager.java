package io.github.vhorvath2010.missilewars.schematics;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.Vector3;
import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;

/** A class to handle loading/placing of NBT and WorldEdit schematics */
public class SchematicManager {

    /**
     * Get the vector for a given structure/schematic path in a given config.
     *
     * @param config the config to get data from
     * @param path the path to the x, y, z offset data
     * @return the x, y, z offset as a vector
     */
    public static Vector getVector(FileConfiguration config, String path) {
        Vector vector = new Vector();
        if (config.contains(path)) {
            vector = new Vector(config.getDouble(path + ".x"), config.getDouble(path + ".y"),
                    config.getDouble(path + ".z"));
        }
        return vector;
    }

    /**
     * Spawn a structure at a given location with a given rotation.
     *
     * @param structureName the name of the structure
     * @param loc the location to spawn the structure (pre-offset)
     * @param redMissile if the NBT structure is a red missile
     * @return true if the NBT structure was found and spawned, otherwise false
     */
    public static boolean spawnNBTStructure(String structureName, Location loc, boolean redMissile) {
        // Attempt to get structure file
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        FileConfiguration structureConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "items.yml");

        // Attempt to get structure file
        if (!structureConfig.contains(structureName + ".file")) {
            return false;
        }
        File structureFile = new File(plugin.getDataFolder() + File.separator + "structures",
                structureConfig.getString(structureName + ".file"));

        // Load structure data
        StructureManager manager = Bukkit.getStructureManager();
        Structure structure;
        try {
            structure = manager.loadStructure(structureFile);
        } catch (IOException e) {
            return false;
        }

        // Apply offset
        Location spawnLoc = loc.clone();
        spawnLoc = spawnLoc.add(getVector(structureConfig, structureName + ".offset"));

        // Replace convert blue blocks to red blocks and apply rotation if needed
        StructureRotation rotation = StructureRotation.NONE;
        if (redMissile) {
            rotation = StructureRotation.CLOCKWISE_180;
            Palette blockPalette = structure.getPalettes().get(0);
            for (BlockState data : blockPalette.getBlocks()) {
                String type = data.getType().toString();
                if (type.contains("BLUE_")) {
                    Material newMat = Material.getMaterial(type.replace("BLUE_", "RED_"));
                    if (newMat != null) {
                        data.setType(newMat);
                    }
                }
            }
        }

        // Place structure
        structure.place(spawnLoc, true, rotation, Mirror.NONE, 0, 1, new Random());
        return true;
    }

    /**
     * Spawn a WorldEdit schematic in a given world. The "maps.yml" file should have data on the spawn location and
     * schematic file.
     *
     * @param schematicName the name of the schematic in the maps.yml file
     * @param world the world to spawn the schematic in
     * @return true if the schematic was generated successfully, otherwise false
     */
    public static boolean spawnFAWESchematic(String schematicName, World world) {
        // Find schematic data from file
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        FileConfiguration schematicConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                        .toString(), "maps.yml");

        // Acquire WE clipboard
        if (!schematicConfig.contains(schematicName + ".file")) {
            System.out.println("no schem file found!");
            return false;
        }
        File schematicFile = new File(plugin.getDataFolder() + File.separator + "maps",
                schematicConfig.getString(schematicName + ".file"));
        Clipboard clipboard;
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        try {
            ClipboardReader reader = format.getReader(new FileInputStream(schematicFile));
            clipboard = reader.read();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        // Paste WE clipboard
        Vector spawnPos = getVector(schematicConfig, schematicName + ".pos");
        clipboard.paste(BukkitAdapter.adapt(world),
                Vector3.toBlockPoint(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));
        return true;
    }

}
