package com.leomelonseeds.missilewars.arenas.teams;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class ClassicPortal {

    private static boolean GLOW_SAFE = false;  
    private final static int GLOW_WAIT = 200; 
    private final static float GLOW_DISTANCE = 10F;
    private static MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
    
    private boolean alive;
    private Location loc1; // Corner 1, defined in config
    private Location loc2; // Corner 2, always -x and -y from corner 1 (west)
    private BlockDisplay glow;
    
    public ClassicPortal(Location loc1) {
        this.alive = true;
        this.loc1 = loc1;
        this.loc2 = loc1.clone();
        
        // Find loc2, then setup glow 
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            while (loc2.clone().add(-1, 0, 0).getBlock().getType() != Material.OBSIDIAN) {
                loc2.add(-1, 0, 0);
            }
            
            while (loc2.clone().add(0, -1, 0).getBlock().getType() != Material.OBSIDIAN) {
                loc2.add(0, -1, 0);
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> setupGlow());
        });
    }
    
    private void setupGlow() {
        // If server just started, wait a bit for everything to calm down
        if (!GLOW_SAFE) {
            ConfigUtils.schedule(GLOW_WAIT, () -> setupGlow());
            return;
        }
        
        glow = (BlockDisplay) loc1.getWorld().spawnEntity(loc1.clone().add(1, 1, 0), EntityType.BLOCK_DISPLAY);
        GlassPane pane = (GlassPane) Material.PURPLE_STAINED_GLASS_PANE.createBlockData();
        pane.setFace(BlockFace.EAST, true);
        pane.setFace(BlockFace.WEST, true);
        glow.setBlock(pane);
        glow.setViewRange(GLOW_DISTANCE);
        
        // Figure out transformation vector
        Vector3f scale = new Vector3f(loc2.getBlockX() - loc1.getBlockX() - 1, loc2.getBlockY() - loc1.getBlockY() - 1, 1);
        Transformation trans = new Transformation(new Vector3f(), new AxisAngle4f(), scale, new AxisAngle4f());
        glow.setTransformation(trans);
        
        // Set glow
        Color teamColor = loc1.getZ() < 0 ? Color.BLUE : Color.RED;
        glow.setGlowColorOverride(teamColor);
        glow.setGlowing(true);
        
        glow.setVisibleByDefault(false);
    }
    
    /**
     * Removes and restores the glow for this portal. Only
     * useful in arenas where portals can regenerate
     */
    public void resetGlow() {
        removeGlow();
        setupGlow();
    }
    
    /**
     * Remove the glow from this portal for all players
     */
    public void removeGlow() {
        if (glow == null) {
            return;
        }
        
        glow.remove();
        glow = null;
    }
    
    public boolean isAlive() {
        return alive;
    }
    
    /**
     * If alive is set to false, the glow for the portal
     * will be removed for all players
     * 
     * @param alive
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
        if (!alive) {
            removeGlow();
        }
    }
    
    /**
     * Makes the portal glow for a certain player
     * 
     * @param player
     */
    public void glow(MissileWarsPlayer mwp) {
        if (mwp == null) {
            return;
        }
        
        Player player = mwp.getMCPlayer();
        if (glow == null || player == null || !player.isOnline()) {
            return;
        }
        
        if (player.canSee(glow)) {
            return;
        }
        
        player.showEntity(plugin, glow);
    }
    
    /**
     * Hides portal glow for a player
     * 
     * @param player
     */
    public void hideGlow(MissileWarsPlayer mwp) {
        if (mwp == null) {
            return;
        }
        
        Player player = mwp.getMCPlayer();
        if (glow == null || player == null || !player.isOnline()) {
            return;
        }
        
        if (!player.canSee(glow)) {
            return;
        }
        
        player.hideEntity(plugin, glow);
    }
    
    /**
     * Call during onEnable to start the glow timer.
     */
    public static void onEnable() {
        if (GLOW_SAFE) {
            return;
        }
        
        ConfigUtils.schedule(GLOW_WAIT, () -> GLOW_SAFE = true);
    }
}
