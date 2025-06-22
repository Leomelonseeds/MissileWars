package com.leomelonseeds.missilewars.utilities.schem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;

public class SchematicLoadResult {
    
    private boolean isMissile;
    private Location spawnPos;
    private SchematicLoadStatus status;
    private StructureRotation rotation;
    private Clipboard clipboard;
    private List<Pair<Location, BlockData>> blockList;
    private File file;
    private String structureName;

    /** 
     * The result of a schematic load. If isAllowSpawn returns false,
     * all other fields except status may be null. Otherwise, all
     * other fields are guaranteed to exist.
     */
    public SchematicLoadResult(boolean isMissile, String structureName) {
        this.isMissile = isMissile;
        this.structureName = structureName;
        this.status = SchematicLoadStatus.NONE;
        this.rotation = StructureRotation.NONE;
        this.blockList = new ArrayList<>();
    }
    
    /**
     * Get non-modifiable structure block data. Checkcollisions must have been
     * called at least once, call it again to update the list
     * 
     * @return All non-air locations and blocks in the schematic, adjusted for rotation
     */
    public List<Pair<Location, BlockData>> getBlocks() {
        return Collections.unmodifiableList(blockList);
    }
    
    /**
     * Check if in the current arena this structure collides with unbreakable blocks.
     * This method also checks the likelihood that the missile will spawn and move successfully.
     * It will also move the missile back if it is spawned in an enemy portal.
     * This method is async callable but still expensive, use wisely.
     */
    public void checkCollisions() {
        Set<String> cancel = new HashSet<>(MissileWarsPlugin.getPlugin().getConfig().getStringList("cancel-schematic"));
        Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(spawnPos.getWorld());
        boolean redMissile = rotation == StructureRotation.CLOCKWISE_180;
        boolean missileInBase = isMissile && ArenaUtils.inShield(arena, spawnPos, redMissile ? TeamName.RED : TeamName.BLUE);
        boolean missileInOtherBase = isMissile && ArenaUtils.inShield(arena, spawnPos, redMissile ? TeamName.BLUE : TeamName.RED, 4);
        boolean allowSpawn = true;
        int teamGrief = spawnPos.getBlockZ() + (redMissile ? -1 : 1);
        blockList.clear();
        for (BlockVector3 locVec : clipboard) {
            // Get the data of the block that will be pasted. Use another block so command blocks don't show
            BlockData data = BukkitAdapter.adapt(clipboard.getBlock(locVec));
            if (data.getMaterial() == Material.COMMAND_BLOCK) {
                data = Material.CHISELED_DEEPSLATE.createBlockData();
            }
            
            // Get the location where this block will be pasted
            Location l = BukkitAdapter.adapt(spawnPos.getWorld(), locVec);
            if (rotation == StructureRotation.CLOCKWISE_180) {
                l.setX(-l.getBlockX());
                l.setZ(-l.getBlockZ());
                data.rotate(rotation);
            }
            l.add(spawnPos);
            
            // Cache this block data for if it needs to be used
            if (data.getMaterial() != Material.AIR) {
                blockList.add(Pair.of(l, data));
            }
            
            // If we can't spawn it no need to do further analysis here
            if (!allowSpawn) {
                continue;
            }
            
            // Get the current block this one will replace
            Material b = l.getBlock().getType();
            if (b == Material.AIR) {
                continue;
            } 
            
            // Check for teamgriefing
            if (missileInBase && l.getBlockZ() == teamGrief) {
                status = SchematicLoadStatus.IN_OWN_BASE;
                allowSpawn = false;
                continue;
            }
            
            // Don't apply unbreakable checks to non blocks in missile
            if (data.getMaterial() == Material.AIR) {
                continue;
            }
            
            // Check for unbreakable blocks
            if (cancel.contains(b.toString())) {
                // Move missile backwards if it would spawn in another base (it has probably collided with the portal)
                if (missileInOtherBase) {
                    spawnPos.add(0, 0, redMissile ? 1 : -1);
                    checkCollisions();
                    return;
                }
                
                // Otherwise, do not allow the missile spawn
                status = SchematicLoadStatus.UNBREAKABLE_BLOCKS;
                allowSpawn = false;
                continue;
            }
        }
        
        if (allowSpawn) {
            status = SchematicLoadStatus.SUCCESS;
        }
    }
    
    /**
     * Get the corners describing the bounding box of this structure. Spawnloc and clipboard must be set.
     * 
     * @return A copy of the corners (inclusive), index 0 is always guaranteed to have the minimum of each coordinate.
     */
    public Location[] getCorners() {
        // Basically an affine transformation but I can't be bothered to do the math
        Location[] res = new Location[2];
        BlockVector3 size = clipboard.getDimensions().subtract(1, 1, 1);
        if (rotation == StructureRotation.CLOCKWISE_180) {
            res[0] = spawnPos.clone().add(-size.getBlockX(), 0, -size.getBlockZ());
            res[1] = spawnPos.clone().add(0, size.getBlockY(), 0);
        } else {
            res[0] = spawnPos.clone();
            res[1] = spawnPos.clone().add(size.getBlockX(), size.getBlockY(), size.getBlockZ());
        }
        
        return res;
    }
    
    public boolean isAllowSpawn() {
        return status == SchematicLoadStatus.SUCCESS;
    }
    
    public Location getSpawnPos() {
        return spawnPos;
    }
    
    public SchematicLoadStatus getStatus() {
        return status;
    }

    public void setStatus(SchematicLoadStatus status) {
        this.status = status;
    }

    public void setSpawnPos(Location spawnPos) {
        this.spawnPos = spawnPos;
    }
    
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public StructureRotation getRotation() {
        return rotation;
    }

    public void setRotation(StructureRotation rotation) {
        this.rotation = rotation;
    }

    public void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    public String getStructureName() {
        return structureName;
    }
}
