package com.leomelonseeds.missilewars.arenas.teams;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

public class ClassicPortal {
    
    private boolean alive;
    private Location loc1; // Corner 1, defined in config
    private Location loc2; // Corner 2, always -x and -y from corner 1 (west)
    private BlockDisplay glow;
    private Color teamColor;
    
    public ClassicPortal(Location loc1) {
        this.alive = true;
        this.loc1 = loc1;
        this.loc2 = loc1.clone();
        this.teamColor = loc1.getZ() < 0 ? Color.BLUE : Color.RED;
        
        // Find loc2
        Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            while (loc2.clone().add(-1, 0, 0).getBlock().getType() != Material.OBSIDIAN) {
                loc2.add(-1, 0, 0);
            }
            
            while (loc2.clone().add(0, -1, 0).getBlock().getType() != Material.OBSIDIAN) {
                loc2.add(0, -1, 0);
            }
        });
    }
    
    public boolean isAlive() {
        return alive;
    }
    
    /**
     * If alive is set to false, automatically tries to remove glow
     * 
     * @param alive
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
        if (!alive) {
            unglow();
        }
    }
    
    /**
     * Make portal glow, showing its location to all players
     * Does nothing if portal is dead, or already glowing
     * 
     * @param distance the distance that players can see the glow from
     */
    public void glow(float distance) {
        if (!alive || glow != null) {
            return;
        }
        
        glow = (BlockDisplay) loc1.getWorld().spawnEntity(loc1.clone().add(1, 1, 0), EntityType.BLOCK_DISPLAY);
        GlassPane pane = (GlassPane) Material.PURPLE_STAINED_GLASS_PANE.createBlockData();
        pane.setFace(BlockFace.EAST, true);
        pane.setFace(BlockFace.WEST, true);
        glow.setBlock(pane);
        glow.setViewRange(distance);
        
        // Figure out transformation vector
        Vector3f scale = new Vector3f(loc2.getBlockX() - loc1.getBlockX() - 1, loc2.getBlockY() - loc1.getBlockY() - 1, 1);
        Transformation trans = new Transformation(new Vector3f(), new AxisAngle4f(), scale, new AxisAngle4f());
        glow.setTransformation(trans);
        glow.setGlowing(true);
        glow.setGlowColorOverride(teamColor);
    }
    
    /**
     * Remove the glow from this portal
     */
    public void unglow() {
        if (glow == null) {
            return;
        }
        
        glow.remove();
        glow = null;
    }
}
