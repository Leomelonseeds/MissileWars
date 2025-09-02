package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;

// ---------------------------------------------------------
// This class ignites tnt if b36 hit with flaming projectile
// ---------------------------------------------------------
public class MovingTNTHandler implements Listener {
    
    private static MovingTNTHandler instance;

    private Map<Location, Location> tnt;
    private Random random;
    
    private MovingTNTHandler() {
        this.tnt = new HashMap<>();
        this.random = new Random();
    }
    
    /**
     * Attempts to ignite a TNT block at this location
     * 
     * @param block
     * @param source
     * @return true if a TNT block was ignited for the specified block
     */
    public boolean igniteTNT(Block block, Player source) {
        return igniteTNT(block, source, 0);
    }
    
    /**
     * Attempts to ignite a TNT block at this location
     * 
     * @param block
     * @param source
     * @param maxFuseDeviation
     * @return true if a TNT block was ignited for the specified block
     */
    public boolean igniteTNT(Block block, Player source, int maxFuseDeviation) {
        Location spawnLoc;
        if (block.getType() == Material.TNT) {
            spawnLoc = block.getLocation().toCenterLocation().subtract(0, 0.49, 0);
        } else if (block.getType() == Material.MOVING_PISTON) {
            Location blockLoc = block.getLocation().toCenterLocation();
            if (!tnt.containsKey(blockLoc)) {
                return false;
            }
            
            spawnLoc = tnt.remove(blockLoc).subtract(0, 0.49, 0);
        } else {
            return false;
        }
        
        block.setType(Material.AIR);
        TNTPrimed primed = (TNTPrimed) block.getWorld().spawnEntity(spawnLoc, EntityType.TNT);
        int fuseTicks = 80;
        if (maxFuseDeviation != 0) {
            fuseTicks = random.nextInt(fuseTicks - maxFuseDeviation, fuseTicks + 1);
        }
        primed.setFuseTicks(80);
        
        // Set source if specified
        if (source != null) {
            primed.setSource(source);
        }
        
        return true;
    }
    
    @EventHandler
    private void extendTNT(BlockPistonExtendEvent e) {
        e.getBlocks().forEach(b -> addToList(b, e));
        
    }
    
    @EventHandler
    private void retractTNT(BlockPistonRetractEvent e) {
        e.getBlocks().forEach(b -> addToList(b, e));
    }
    
    @EventHandler
    private void onFlameHit(ProjectileHitEvent e) {
        EntityType type = e.getEntityType();
        boolean isArrow = (type.toString().contains("ARROW") || type == EntityType.TRIDENT) && 
                e.getEntity().getFireTicks() > 0;
        if (!isArrow && type != EntityType.SMALL_FIREBALL ) {
            return;
        }
        
        if (e.getHitBlock() == null) {
            return;
        }
        
        Block b = e.getHitBlock();
        ProjectileSource shooter = e.getEntity().getShooter();
        igniteTNT(b, shooter instanceof Player ? (Player) shooter : null);
    }
    
    private void addToList(Block b, BlockPistonEvent e) {
        if (b.getType() != Material.TNT) {
            return;
        }
        
        // Add location of where the block WILL BE to a list
        Location loc = b.getLocation().toCenterLocation();
        Vector direction = e.getDirection().getDirection().normalize();
        final Location finalLoc = loc.clone().add(direction);
        tnt.put(finalLoc, loc);
        
        // Update the spawning location of the tnt such that it spawns smoothly
        for (int i = 1; i <= 3; i++) {
            Vector toAdd = direction.clone().multiply(0.33 * i);
            int index = i;
            ConfigUtils.schedule(i, () -> {
                if (!tnt.containsKey(finalLoc)) {
                    return;
                }
                
                if (index < 3) {
                    tnt.get(finalLoc).add(toAdd);
                } else {
                    tnt.remove(finalLoc);
                }
            });
        }
    }
    
    public static MovingTNTHandler getInstance() {
        if (instance == null) {
            instance = new MovingTNTHandler();
        }
        
        return instance;
    }

}
