package com.leomelonseeds.missilewars.listener.handler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.schem.SchematicLoadResult;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockCategories;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

public class EngineerSession {
    
    private static final int PARTICLE_DIV = 5;
    private static final float PARTICLE_SIZE = 0.3F;
    
    private Player player;
    private double range;
    private ItemStack item;
    private String structure;
    private boolean isRed;
    private BukkitTask updateTask;
    private BlockVector3 size;
    private Location pos1; // null if currently selected position not available
    private Location pos2;
    private String fileName;
    private boolean isSaving;
    
    public EngineerSession(Player player, ItemStack item, String structure, TeamName team) {
        this.player = player;
        this.range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getBaseValue();
        this.item = item;
        this.isRed = team == TeamName.RED;
        this.structure = structure;
        SchematicLoadResult res = SchematicManager.loadNBTStructure(player, structure, player.getLocation(), false, false, false);
        this.size = res.getSize();
        this.fileName = res.getFile().getName();
        if (isRed) {
            fileName = fileName.replace(".nbt", "_red.nbt");
        }
        this.updateTask = createUpdateTask();
    }

    // Updatetask spawns particles and updates the position of the currently selected structure
    public BukkitTask createUpdateTask() {
        ConfigUtils.sendConfigMessage("engineer.start", player);
        ConfigUtils.sendConfigSound("engineer-start", player);
        return Bukkit.getScheduler().runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            updatePosition();
            if (pos1 == null) {
                return;
            }
            
            // Verify that the session is still valid
            PlayerInventory pinv = player.getInventory();
            if (pinv.getItemInMainHand().getType() != Material.IRON_PICKAXE ||
                    pinv.getItemInOffHand().getType() != item.getType()) {
                end(true, false);
                return;
            }
            
            // Spawn particles
            Location p1 = pos1.toCenterLocation().subtract(0.5, 0.5, 0.5);
            Location p2 = pos2.toCenterLocation().add(0.5, 0.5, 0.5);
            DustOptions dustOptions = new DustOptions(ArenaUtils.getTeamParticleColor(isRed), PARTICLE_SIZE);
            int lenx = (int) Math.round(p2.getX() - p1.getX()) * PARTICLE_DIV;
            int leny = (int) Math.round(p2.getY() - p1.getY()) * PARTICLE_DIV;
            int lenz = (int) Math.round(p2.getZ() - p1.getZ()) * PARTICLE_DIV;
            for (int x = 0; x <= lenx; x++) {
                for (int y = 0; y <= leny; y++) {
                    for (int z = 0; z <= lenz; z++) {
                        boolean isX = x == 0 || x == lenx;
                        boolean isY = y == 0 || y == leny;
                        boolean isZ = z == 0 || z == lenz;
                        
                        // This condition checks if we are on the outline
                        if (!(isX ? (isY || isZ) : (isY && isZ))) {
                            continue;
                        }
                        
                        // If block is not air, dust should be red
                        Location cur = new Location(
                            p1.getWorld(),
                            p1.getX() + (double) x / PARTICLE_DIV,
                            p1.getY() + (double) y / PARTICLE_DIV, 
                            p1.getZ() + (double) z / PARTICLE_DIV
                        );

                        p1.getWorld().spawnParticle(Particle.DUST, cur, 1, dustOptions);
                    }
                }
            }
        }, 1, 1);
    }
    
    /**
     * Cancel this engineer session, ending the updatetask
     */
    public void end(boolean removeFromManager, boolean saved) {
        this.updateTask.cancel();
        if (removeFromManager) {
            EngineerManager.getInstance().removeSession(player.getUniqueId());
        }
        
        if (saved) {
            ConfigUtils.sendConfigMessage("engineer.saved", player);
            ConfigUtils.sendConfigSound("engineer-saved", player.getLocation());
        } else {
            ConfigUtils.sendConfigMessage("engineer.cancel", player);
            ConfigUtils.sendConfigSound("engineer-cancel", player);
        }
    }
    
    /**
     * Uses FAWE MC Structure clipboard format to save the selected region into an NBT file.
     * Only blocks containing "GLASS" and pre-defined missile-related blocks are saved. The
     * resulting file is saved in /structures/engineer with format "[uuid]_[default].nbt".
     * The deck instance item receives a custom name and has its internal item-structure
     * name changed to "[default]-e-[uuid]". Calling async is possible.
     * 
     * @return whether the save was successful
     */
    public boolean save() {
        if (isSaving) {
            return false;
        }
        
        if (pos1 == null) {
            ConfigUtils.sendConfigMessage("engineer.no-selection", player);
            return false;
        }
        
        isSaving = true;
        ConfigUtils.sendConfigMessage("engineer.saving", player);

        // Check for cancellable blocks
        List<String> whitelist = MissileWarsPlugin.getPlugin().getConfig().getStringList("deconstructor-blocks");
        whitelist.add("GLASS");
        BlockVector3 bpos1 = BlockVector3.at(pos1.getX(), pos1.getY(), pos1.getZ());
        BlockVector3 bpos2 = BlockVector3.at(pos2.getX(), pos2.getY(), pos2.getZ());
        World world = pos1.getWorld();
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        CuboidRegion region = new CuboidRegion(weWorld, bpos1, bpos2);
        List<Pair<Location, Material>> leaves = new ArrayList<>();
        boolean containsBlocks = false;
        for (BlockVector3 point : region) {
            Location loc = new Location(world, point.getX(), point.getY(), point.getZ());
            Material type = loc.getBlock().getType();
            String blockType = type.toString();
            if (blockType.equals("AIR")) {
                continue;
            }
            
            if (blockType.contains("LEAVES")) {
                leaves.add(Pair.of(loc, type));
                continue;
            }
            
            if (!whitelist.parallelStream().anyMatch(s -> blockType.contains(s))) {
                ConfigUtils.sendConfigMessage("engineer.selection-whitelist", player);
                return false;
            }
            
            containsBlocks = true;
        }
        
        if (!containsBlocks) {
            ConfigUtils.sendConfigMessage("engineer.no-selection", player);
            return false;
        }

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        String dashlessUUID = player.getUniqueId().toString().replace("-", "");
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            // Add structure voids, remove leaves, and flip rotateable blocks if on red
            Set<BlockType> voidTypes = BlockCategories.LEAVES.getAll();
            voidTypes.add(BlockTypes.AIR);
            editSession.replaceBlocks(region, new BlockTypeMask(clipboard, voidTypes), BlockTypes.STRUCTURE_VOID);
            
            // Create a copy to save all current blocks to the clipboard
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, bpos1, clipboard, bpos1);
            copy.setCopyingEntities(false);
            if (isRed) {
                // This transformation mirrors the copy from the center
                copy.setTransform(new AffineTransform(
                    -1, 0,  0, size.getX() - 1,
                     0, 1,  0, 0,
                     0, 0, -1, size.getZ() - 1
                ));
            }
            Operations.complete(copy);
            
            if (isRed) {
                rotateBlocks(clipboard);
            }
            
            // Save schematic to file
            String newFileName = dashlessUUID + "_" + fileName;
            File schematicFile = new File(MissileWarsPlugin.getPlugin().getDataFolder() + 
                    File.separator + "structures" +
                    File.separator + "engineer", 
                    newFileName);
            try (ClipboardWriter writer = BuiltInClipboardFormat.MINECRAFT_STRUCTURE.getWriter(new FileOutputStream(schematicFile))) {
                writer.write(clipboard);
            } catch (IOException e) {
                ConfigUtils.sendConfigMessage("engineer.error", player);
                return false;
            }
            
            // There's a chance that in a previous game, player may have cached a structure with this name.
            // If so, this clears that file so the correct preview may be shown.
            SchematicManager.structureCache.remove(newFileName);
            editSession.replaceBlocks(region, Set.of(new BaseBlock(BlockTypes.STRUCTURE_VOID.getDefaultState())), BlockTypes.AIR);
        }

        String customName = structure + "-e-" + dashlessUUID;
        EngineerManager.getInstance().saveOldItem(customName, item);
        
        // Update the instance item's meta and name
        ItemMeta meta = item.getItemMeta();
        String name = ConfigUtils.toPlain(meta.customName());
        String[] args = name.split(" ");
        String playerName = player.getName() + (player.getName().toLowerCase().endsWith("s") ? "'" : "'s");
        String missileName = (args[1].startsWith("§") ? args[1].substring(0, 2) : "") + playerName + " " + args[1];
        String finalName = String.join(" ", args[0], missileName, args[2]);
        meta.customName(ConfigUtils.toComponent(finalName));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"), 
            PersistentDataType.STRING,
            customName
        );
        item.setItemMeta(meta);
        
        // Give the item to the player
        ItemStack giveItem = item.clone();
        Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> {
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack curItem = contents[i];
                if (curItem == null || curItem.getType() != giveItem.getType()) {
                    continue;
                }

                giveItem.setAmount(curItem.getAmount());
                giveItem.addUnsafeEnchantments(curItem.getEnchantments());
                player.getInventory().setItem(i, giveItem);
                break;
            }
            
            // Replace leaf blocks back with leaves
            leaves.forEach(l -> l.getLeft().getBlock().setType(l.getRight()));
            end(false, true);
        });
        
        return true;
    }
    
    /**
     * Rotate all rotateable blocks 180 degrees to save a red side structure correctly
     * 
     * @param clipboard
     * @param session
     */
    private void rotateBlocks(BlockArrayClipboard clipboard) {
        for (BlockVector3 block : clipboard) {
            String curState = clipboard.getBlock(block).toString();
            String flippedState = curState
                    .replace("east", "temp_e")
                    .replace("west", "east")
                    .replace("temp_e", "west")
                    .replace("north", "temp_n")
                    .replace("south", "north")
                    .replace("temp_n", "south");
            
            if (curState.equals(flippedState)) {
                continue;
            }
            
            BlockState newState = BlockState.get(flippedState);
            clipboard.setBlock(block, newState);
        }
    }
    
    /**
     * Uses the player's current target block to update pos1 and pos2 depending on the team.
     * The target block is calculated as in vanilla using the player's block interaction
     * range. The selected block is the bottom central block of the selected region, where
     * the left side will extend ceil((sizeX - 1) / 2) and the right side will extend floor
     * of the same. Top will extend sizeY - 1 and depth will extend sizeZ - 1 in whichever
     * direction the enemy team is in.
     */
    private void updatePosition() {
        Location eyeLoc = player.getEyeLocation();
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(eyeLoc, eyeLoc.getDirection(), range);
        if (rayTrace == null || rayTrace.getHitBlock() == null) {
            this.pos1 = null;
            return;
        }
        
        Location center = rayTrace.getHitBlock().getLocation();
        double xDiff = ((double) size.getX() - 1) / 2;
        if (isRed) {
            this.pos1 = center.clone().add(-Math.ceil(xDiff), 0, -size.getZ() + 1);
            this.pos2 = center.clone().add(Math.floor(xDiff), size.getY() - 1, 0);
        } else {
            this.pos1 = center.clone().add(-Math.floor(xDiff), 0, 0);
            this.pos2 = center.clone().add(Math.ceil(xDiff), size.getY() - 1, size.getZ() - 1);
        }
        
        
    }
    
    public boolean isSaving() {
        return isSaving;
    }
}
