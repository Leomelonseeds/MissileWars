package com.leomelonseeds.missilewars.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail.Shape;
import org.bukkit.block.data.type.RedstoneRail;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.arenas.tracker.TrackedMissile;
import com.leomelonseeds.missilewars.arenas.tracker.TrackedUtility;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

/** A class to handle loading/placing of NBT and WorldEdit schematics */
public class SchematicManager {

    /**
     * Get the vector for a given structure/schematic path in a given config.
     *
     * @param config the config to get data from
     * @param path the path to the x, y, z vector data
     * @return the x, y, z as a vector
     */
    public static Vector getVector(FileConfiguration config, String path, String mapType, String mapName) {
        Vector vector = new Vector();
        if (config.contains(path)) {
            vector = new Vector(config.getDouble(path + ".x"), config.getDouble(path + ".y"),
                    config.getDouble(path + ".z"));
        } else {
            // If config does not contain path, then it's definitely a map
            vector = new Vector(ConfigUtils.getMapNumber(mapType, mapName, path + ".x"),
                                ConfigUtils.getMapNumber(mapType, mapName, path + ".y"),
                                ConfigUtils.getMapNumber(mapType, mapName, path + ".z"));
        }
        return vector;
    }
    
    public static boolean spawnNBTStructure(Player player, String structureName, Location loc, boolean redMissile, String mapName, Boolean isMissile, Boolean checkCollision) {
        return spawnNBTStructure(player, structureName, loc, redMissile, mapName, isMissile, checkCollision, 0);
    }

    /**
     * Spawn a structure at a given location with a given rotation.
     *
     * @param player the player who spawned the structure
     * @param structureName the name of the structure
     * @param loc the location to spawn the structure (pre-offset)
     * @param redMissile if the NBT structure is a red missile
     * @param mapName The name of the Arena map the NBT structure is being spawned in
     * @param isMissile whether the structure is a missile or a utility
     * @param checkCollision whether to check if hitboxes intersect with important blocks
     * @return true if the NBT structure was found and spawned, otherwise false
     */
    private static boolean spawnNBTStructure(Player player, String structureName, Location loc, boolean redMissile, String mapName, Boolean isMissile, Boolean checkCollision, int attempt) {
        // Don't kill the lobby
        if (loc.getWorld().getName().equals("world")){
            sendError(player, "How the hell did you get that here?");
            return false;
        }
        
        // Don't be too high
        if (loc.getBlockY() > MissileWarsPlugin.getPlugin().getConfig().getInt("max-height")) {
            sendError(player, "Structures cannot be spawned this high!");
            return false;
        }

        // Attempt to get structure file
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        FileConfiguration structureConfig = ConfigUtils.getConfigFile("items.yml");
        String[] args = structureName.split("-");
        int level = Integer.parseInt(args[1]);

        // Attempt to get structure file
        if (ConfigUtils.getItemValue(args[0], level, "file") == null) {
            sendError(player, "The file for this structure is missing! Please contact an admin.");
            return false;
        }
        String fileName = (String) ConfigUtils.getItemValue(args[0], level, "file");
        if (fileName == null) {
            sendError(player, "The file for this structure is missing! Please contact an admin.");
            return false;
        }
        if (redMissile) {
            fileName = fileName.replaceAll(".nbt", "_red.nbt");
        }
        File structureFile = new File(plugin.getDataFolder() + File.separator + "structures",
                fileName);

        // Load structure data
        StructureManager manager = Bukkit.getStructureManager();
        Structure structure;
        try {
            structure = manager.loadStructure(structureFile);
        } catch (IOException e) {
            sendError(player, "The file for this structure is missing! Please contact an admin.");
            return false;
        }

        // Apply offset
        Location spawnLoc = loc.clone();
        Vector offset;
        if (structureConfig.contains(args[0] + ".offset")) {
            offset = getVector(structureConfig, args[0] + ".offset", null, null);
        } else {
            offset = getVector(structureConfig, args[0] + "." + level + ".offset", null, null);
        }
        
        // If player is using old offsets, add 2 if missile is >=3.3 and 1 otherwise. Just parity things.
        if (isMissile && player != null && player.hasPermission("umw.oldoffsets")) {
            offset.setZ(offset.getZ() + (Double.valueOf(ConfigUtils.getItemValue(args[0], level, "speed") + "") > 3 ? 2 : 1));
        }
        
        // Check for pokemissile
        int sizex = structure.getSize().getBlockX();
        int sizey = structure.getSize().getBlockY();
        int sizez = structure.getSize().getBlockZ();
        if (args.length == 3 && args[2].equals("p")) {
            isMissile = true;
            offset.setZ(-1 * sizez / 2);
            offset.setY(-1 * sizey / 2);
        }
        
        StructureRotation rotation = StructureRotation.NONE;
        if (redMissile) {
            offset.setZ(offset.getZ() * -1);
            offset.setX(offset.getX() * -1);
            rotation = StructureRotation.CLOCKWISE_180;
        }
        spawnLoc = spawnLoc.add(offset);
        
        // Checks if the missile intersects with an obsidian/barrier structure
        int spawnx = spawnLoc.getBlockX();
        int spawny = spawnLoc.getBlockY();
        int spawnz = spawnLoc.getBlockZ();
        if (checkCollision) {
            List<String> cancel = plugin.getConfig().getStringList("cancel-schematic");
            Arena arena = plugin.getArenaManager().getArena(player.getUniqueId());
            boolean missileInBase = isMissile && ArenaUtils.inShield(arena, spawnLoc, redMissile ? TeamName.RED : TeamName.BLUE);
            boolean missileInOtherBase = isMissile && ArenaUtils.inShield(arena, spawnLoc, redMissile ? TeamName.BLUE : TeamName.RED, 4);
            boolean isTutorial = loc.getWorld().getName().contains("tutorial");
            int x1, x2, z1, z2, tg; 
            if (redMissile) {
                z1 = spawnz - sizez + 1;
                z2 = spawnz;
                x1 = spawnx - sizex + 1;
                x2 = spawnx;
                tg = spawnz - 1;
            } else {
                z1 = spawnz;
                z2 = spawnz + sizez - 1;
                x1 = spawnx;
                x2 = spawnx + sizex - 1;
                tg = spawnz + 1;
            }
            
            for (int z = z1; z <= z2; z++) {
                for (int y = spawny; y < spawny + sizey; y++) {
                    for (int x = x1; x <= x2; x++) {
                        // Only check non-air blocks for missile collision
                        Location l = new Location(loc.getWorld(), x, y, z);
                        Material b = l.getBlock().getType();
                        if (b == Material.AIR) {
                            continue;
                        }
                        
                        // Check for tutorial arena
                        if (isMissile && z < 0 && isTutorial) {
                            sendError(player, "Missiles spawned on your side of this arena must be clear of obstacles!");
                            return false;
                        }
                        
                        // Check for teamgrief
                        if (missileInBase && z == tg) {
                            sendError(player, "You cannot spawn missiles inside your base!");
                            return false;
                        }
                        
                        // Check all other cancellable blocks
                        if (cancel.contains(b.toString())) {
                            // Move missile backwards if it would spawn in another base (it has definitely collided with the portal)
                            if (missileInOtherBase) {
                                Location testAgain = loc.clone().add(0, 0, redMissile ? 1 : -1);
                                return spawnNBTStructure(player, structureName, testAgain, redMissile, mapName, isMissile, checkCollision, ++attempt);
                            }
                            sendError(player, "You cannot spawn structures inside unbreakable blocks!");
                            return false;
                        }
                    }
                }
            }
        }

        // Place structure
        structure.place(spawnLoc, true, rotation, Mirror.NONE, 0, 1, new Random());
        if (player != null && attempt > 0) {
            ConfigUtils.sendConfigMessage("messages.missile-moved", player, null, null);
        }
        
        // Add structure to tracker list
        World world = loc.getWorld();
        Location pos1;
        Location pos2;
        BlockFace direction;
        if (rotation == StructureRotation.NONE) {
            pos1 = new Location(world, spawnx - 1, spawny - 1, spawnz - 1);
            pos2 = new Location(world, spawnx + sizex, spawny + sizey, spawnz + sizez);
            direction = BlockFace.SOUTH;
        } else if (rotation == StructureRotation.CLOCKWISE_180) {
            pos1 = new Location(world, spawnx + 1, spawny - 1, spawnz + 1);
            pos2 = new Location(world, spawnx - sizex, spawny + sizey, spawnz - sizez);
            direction = BlockFace.NORTH;
        } else if (rotation == StructureRotation.CLOCKWISE_90) {
            pos1 = new Location(world, spawnx + 1, spawny - 1, spawnz - 1);
            pos2 = new Location(world, spawnx - sizez, spawny + sizey, spawnz + sizex);
            direction = BlockFace.WEST;
        } else {
            pos1 = new Location(world, spawnx - 1, spawny - 1, spawnz + 1);
            pos2 = new Location(world, spawnx + sizez, spawny + sizey, spawnz - sizex);
            direction = BlockFace.EAST;
        }
        if (isMissile) {
            new TrackedMissile(args[0], level, player, pos1, pos2, direction, redMissile);
        } else {
            new TrackedUtility(args[0], level, player, pos1, pos2, direction, redMissile);
        }
        
        // Spawn TNT minecarts in torpedos
        if (args[0].equals("torpedo")) {
            final int minecarts = 4;
            List<Location> minecartLocs = new ArrayList<>();
            Location minecartLoc1 = loc.clone().toCenterLocation().add(0, -0.5, 0);
            minecartLocs.add(minecartLoc1);
            if (args[1].equals("2")) {
                minecartLocs.add(minecartLoc1.clone().add(0, -2, 0));
            }
            
            for (Location minecartLoc : minecartLocs) {
                for (int i = 0; i < minecarts; i++) {
                    world.spawnEntity(minecartLoc, EntityType.TNT_MINECART);
                }
            }
        }
        
        // Temp hotfix for structure rail rotation bug
        if (redMissile && structureName.equals("lifter-2")) {
            Location railLoc = spawnLoc.add(-1, 2, -8);
            Block block = railLoc.getBlock();
            block.setType(Material.DETECTOR_RAIL);
            RedstoneRail rail = (RedstoneRail) block.getBlockData(); 
            rail.setShape(Shape.EAST_WEST);
            block.setBlockData(rail);
            block.getState().update(true);
        }
        
        /* Temp hotfix for structure rail rotation bug (unused missile)
        if (redMissile && structureName.contains("cruiser-2")) {
            Location railLoc = spawnLoc.add(0, 1, -8);
            Block block = railLoc.getBlock();
            block.setType(Material.POWERED_RAIL);
            RedstoneRail rail = (RedstoneRail) block.getBlockData(); 
            rail.setShape(Shape.NORTH_SOUTH);
            block.setBlockData(rail);
            block.getState().update(true);
        }*/
        return true;
    }
    
    private static void sendError(Player player, String message) {
        if (player == null) {
            return;
        }
        
        player.sendMessage(ConfigUtils.toComponent("&c" + message));
    }
    
    /**
     * Returns the 2 positions in which this missile will be spawned. Contains tons of
     * duplicated code which I am very unproud of
     * 
     * @param structureName
     * @param loc
     * @param redMissile
     * @return
     */
    public static Location[] getCorners(String structureName, Location loc, boolean redMissile, boolean oldOffsets) {
        // Attempt to get structure file
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        FileConfiguration structureConfig = ConfigUtils.getConfigFile("items.yml");
        String [] args = structureName.split("-");
        int level = Integer.parseInt(args[1]);

        // Attempt to get structure file
        if (ConfigUtils.getItemValue(args[0], level, "file") == null) {
            return null;
        }
        String fileName = (String) ConfigUtils.getItemValue(args[0], level, "file");
        if (fileName == null) {
            return null;
        }
        if (redMissile) {
            fileName = fileName.replaceAll(".nbt", "_red.nbt");
        }
        File structureFile = new File(plugin.getDataFolder() + File.separator + "structures",
                fileName);

        // Load structure data
        StructureManager manager = Bukkit.getStructureManager();
        Structure structure;
        try {
            structure = manager.loadStructure(structureFile);
        } catch (IOException e) {
            return null;
        }

        // Apply offset
        Location spawnLoc = loc.clone();

        Vector offset;
        if (structureConfig.contains(args[0] + ".offset")) {
            offset = getVector(structureConfig, args[0] + ".offset", null, null);
        } else {
            offset = getVector(structureConfig, args[0] + "." + level + ".offset", null, null);
        }
        
        // If player is using old offsets, add 2 if missile is >=3.3 and 1 otherwise. Just parity things.
        if (oldOffsets) {
            offset.setZ(offset.getZ() + (Double.valueOf(ConfigUtils.getItemValue(args[0], level, "speed") + "") > 3 ? 2 : 1));
        }
        
        // Flip z if on red team
        StructureRotation rotation = StructureRotation.NONE;
        
        // Normal red missile offset adjustment
        if (redMissile) {
            offset.setZ(offset.getZ() * -1);
            offset.setX(offset.getX() * -1);
            rotation = StructureRotation.CLOCKWISE_180;
        }
        spawnLoc = spawnLoc.add(offset);

        int spawnx = spawnLoc.getBlockX();
        int spawny = spawnLoc.getBlockY();
        int spawnz = spawnLoc.getBlockZ();

        int sizex = structure.getSize().getBlockX();
        int sizey = structure.getSize().getBlockY();
        int sizez = structure.getSize().getBlockZ();
        
        // Add structure to tracker list
        World world = loc.getWorld();
        Location pos1;
        Location pos2;
        if (rotation == StructureRotation.NONE) {
            pos1 = new Location(world, spawnx - 1, spawny - 1, spawnz - 1);
            pos2 = new Location(world, spawnx + sizex, spawny + sizey, spawnz + sizez);
        } else if (rotation == StructureRotation.CLOCKWISE_180) {
            pos1 = new Location(world, spawnx + 1, spawny - 1, spawnz + 1);
            pos2 = new Location(world, spawnx - sizex, spawny + sizey, spawnz - sizez);
        } else if (rotation == StructureRotation.CLOCKWISE_90) {
            pos1 = new Location(world, spawnx + 1, spawny - 1, spawnz - 1);
            pos2 = new Location(world, spawnx - sizez, spawny + sizey, spawnz + sizex);
        } else {
            pos1 = new Location(world, spawnx - 1, spawny - 1, spawnz + 1);
            pos2 = new Location(world, spawnx + sizez, spawny + sizey, spawnz - sizex);
        }
        
        return new Location[] {pos1.toCenterLocation(), pos2.toCenterLocation()};
    }
    
    /**
     * Spawn a base-level maps.yml fawe schematic
     * 
     * @param schematicName
     * @param world
     * @return
     */
    public static boolean spawnFAWESchematic(String schematicName, World world) {
        return spawnFAWESchematic(schematicName, world, null, null);
    }

    /**
     * Spawn a WorldEdit schematic in a given world. The "maps.yml" file should have data on the spawn location and
     * schematic file.
     *
     * @param schematicName the name of the schematic in the maps.yml file
     * @param world the world to spawn the schematic in
     * @param mapType the gamemode of the map, used for searching for the maps in the file
     * @param callback sync stuff to run after the map is generated
     * @return true if the schematic was generated successfully, otherwise false
     */
    public static boolean spawnFAWESchematic(String schematicName, World world, String mapType, DBCallback callback) {
        // Find schematic data from file
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        FileConfiguration schematicConfig = ConfigUtils.getConfigFile("maps.yml");

        // Acquire WE clipboard
        String possibleMapType = mapType == null ? "" : mapType + ".";
        if (!schematicConfig.contains(possibleMapType + schematicName + ".file")) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not find " + possibleMapType + schematicName + ".file");
            return false;
        }
        File schematicFile = new File(plugin.getDataFolder() + File.separator + "maps",
                schematicConfig.getString(possibleMapType + schematicName + ".file"));
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
        Vector spawnPos;
        if (mapType == null) {
            spawnPos = getVector(schematicConfig, schematicName + ".pos", null, null);
        } else {
            spawnPos = getVector(schematicConfig, "pos", mapType, schematicName);
        }
        
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                @SuppressWarnings("resource")
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()))
                        .copyEntities(true)
                        .ignoreAirBlocks(true)
                        .build();
                Operations.complete(operation);
                if (callback == null) {
                    return;
                }
                
                Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> {
                    // LightSmog epic random TNT
                    if (schematicName.equals("light-smog")) {
                        Region region = new CuboidRegion(weWorld, BlockVector3.at(34, -16, -21), BlockVector3.at(-34, 32, 21));
                        BlockState tnt = BukkitAdapter.adapt(Material.TNT.createBlockData());
                        BlockState blackStainedGlass = BukkitAdapter.adapt(Material.BLACK_STAINED_GLASS.createBlockData());
                        RandomPattern pattern = new RandomPattern();
                        pattern.add(tnt, 0.5);
                        pattern.add(blackStainedGlass, 99.5);
                        editSession.setBlocks(region, pattern);
                    }
                    
                    // Trinitrotoluene random tnt
                    else if (schematicName.equals("trinitrotoluene")) {
                        Region region1 = new CuboidRegion(weWorld, BlockVector3.at(-32, -15, -65), BlockVector3.at(32, 16, -34));
                        Region region2 = new CuboidRegion(weWorld, BlockVector3.at(-32, -15, 34), BlockVector3.at(32, 16, 65));
                        BlockState tnt = BukkitAdapter.adapt(Material.TNT.createBlockData());
                        BlockState cyanStainedGlass = BukkitAdapter.adapt(Material.CYAN_STAINED_GLASS.createBlockData());
                        BlockState redStainedGlass = BukkitAdapter.adapt(Material.RED_STAINED_GLASS.createBlockData());
                        Set<BaseBlock> cyanSet = new HashSet<>();
                        cyanSet.add(new BaseBlock(cyanStainedGlass));
                        Set<BaseBlock> redSet = new HashSet<>();
                        redSet.add(new BaseBlock(redStainedGlass));
                        RandomPattern pattern1 = new RandomPattern();
                        RandomPattern pattern2 = new RandomPattern();
                        pattern1.add(tnt, 1);
                        pattern1.add(cyanStainedGlass, 99);
                        pattern2.add(tnt, 1);
                        pattern2.add(redStainedGlass, 99);
                        editSession.replaceBlocks(region1, cyanSet, pattern1);
                        editSession.replaceBlocks(region2, redSet, pattern2);
                    }
                    
                    // Start doing everything else
                    callback.onQueryDone(null);
                });
            }
        });
        return true;
    }
    
    
    /**
     * Clears a 5x5 area centered around X and y = 12 from z = 50 to z = -49
     * Function is currently unused, since problems exist where malicious
     * players can still prevent new players from completing the tutorial
     * 
     * @param x
     * @param world
     */
    public static void clearTutorialLane(final int x, World world, DBCallback callback) {
        final int y = 12;
        final int zneg = -50;
        final int zpos = 50;
        Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                Region region = new CuboidRegion(weWorld, BlockVector3.at(x - 2, y - 1, zneg), BlockVector3.at(x + 2, y + 1, zpos));
                editSession.setBlocks(region, new BaseBlock(BukkitAdapter.adapt(Material.AIR.createBlockData())));
                Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> callback.onQueryDone(null));
            } 
        });
    }
    
    /**
     * Use FAWE to fill a region asynchronously with air,
     * removing all non-player entities also in the region
     * 
     * @param x1
     * @param y1
     * @param z1
     * @param x2
     * @param y2
     * @param z2
     * @param world
     * @param callback code to run when finished. This is called using a BukkitTask. Can be null
     */
    public static void setAirAsync(int x1, int y1, int z1, int x2, int y2, int z2, World world, DBCallback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            setAir(x1, y1, z1, x2, y2, z2, world);
            if (callback != null) {
                Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> callback.onQueryDone(null));
            }
        });
    }
    
    /**
     * Use FAWE to fill a region synchronously with air.
     * Non-player entities within this region are also removed 1 tick later.
     * This method can be manually called asynchronously if necessary.
     * 
     * @param x1
     * @param y1
     * @param z1
     * @param x2
     * @param y2
     * @param z2
     * @param world
     */
    public static void setAir(int x1, int y1, int z1, int x2, int y2, int z2, World world) {
        setAir(x1, y1, z1, x2, y2, z2, world, true);
    }
    
    /**
     * Use FAWE to fill a region synchronously with air.
     * This method can be manually called asynchronously if necessary.
     * 
     * @param x1
     * @param y1
     * @param z1
     * @param x2
     * @param y2
     * @param z2
     * @param world
     * @param removeEntities whether non-player entities within the region should be removed
     */
    public static void setAir(int x1, int y1, int z1, int x2, int y2, int z2, World world, boolean removeEntities) {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        BlockVector3 pos1 = BlockVector3.at(x1, y1, z1);
        BlockVector3 pos2 = BlockVector3.at(x2, y2, z2);
        Region region = new CuboidRegion(weWorld, pos1, pos2);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            editSession.setBlocks(region, BlockTypes.AIR);
        }
        
        if (!removeEntities) {
            return;
        }
        
        int xmin = Math.min(x1, x2), xmax = Math.max(x1, x2);
        int ymin = Math.min(y1, y2), ymax = Math.max(y1, y2);
        int zmin = Math.min(z1, z2), zmax = Math.max(z1, z2);
        Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> {
            for (Entity e : world.getEntities()) {
                if (e.getType() == EntityType.PLAYER) {
                    continue;
                }
                
                Location l = e.getLocation();
                if (xmin < l.getX() && l.getX() < xmax &&
                    ymin < l.getY() && l.getY() < ymax &&
                    zmin < l.getZ() && l.getZ() < zmax) {
                    e.remove();
                }
            }
        });
    }

}