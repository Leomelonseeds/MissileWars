package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

// ---------------------------------------------------------
// This class ignites tnt if b36 hit with flaming projectile
// Or if the TNT is within the vicinity of an explosion
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
        return igniteTNT(block, source, 0, 0);
    }
    
    /**
     * Attempts to ignite a TNT block at this location
     * 
     * @param block
     * @param source
     * @param minFuseTicks set both min and max fuse ticks for a range
     * @param maxFuseTicks
     * @return true if a TNT block was ignited for the specified block
     */
    public boolean igniteTNT(Block block, Player source, int minFuseTicks, int maxFuseTicks) {
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
        if (minFuseTicks != 0) {
            fuseTicks = random.nextInt(minFuseTicks, maxFuseTicks + 1);
        }
        primed.setFuseTicks(fuseTicks);
        
        // Set source if specified
        if (source != null) {
            primed.setSource(source);
        }
        
        return true;
    }
    
    @EventHandler
    private void onExplode(EntityExplodeEvent e) {
        Entity entity = e.getEntity();
        Player source = null;
        if (e.getEntityType() == EntityType.TNT_MINECART) {
            Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(e.getLocation().getWorld());
            if (arena != null) {
                source = arena.getTracker().getTNTMinecartSource((ExplosiveMinecart) entity);
            }
        } else if (e.getEntityType() == EntityType.TNT) {
            Entity tntSource = ((TNTPrimed) entity).getSource();
            if (tntSource instanceof Player) {
                source = (Player) tntSource;
            }
        } else if (e.getEntityType() == EntityType.FIREBALL) {
            ProjectileSource shooter = ((Fireball) entity).getShooter();
            if (shooter instanceof Player) {
                source = (Player) shooter;
            }
        } else {
            return;
        }
        
        Player finalSource = source;
        e.blockList().forEach(b -> igniteTNT(b, finalSource, 10, 30));
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
