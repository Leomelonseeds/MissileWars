package com.leomelonseeds.missilewars.utilities.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;
import com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

/* Tracks missiles, utilities, and TNT minecarts to be used for tracking kills/portal breaks */
public class Tracker {
    
    List<Tracked> tracked;
    Map<ExplosiveMinecart, Player> minecarts;
    
    public Tracker() {
        tracked = new ArrayList<>();
        minecarts = new HashMap<>();
    }
    
    public void add(Tracked t) {
        tracked.add(t);
    }
    
    public boolean contains(Tracked t) {
        return tracked.contains(t);
    }
    
    public void remove(Tracked t) {
        tracked.remove(t);
    }
    
    /**
     * Stops all tasks and removes all tracked missiles
     */
    public void stopAll() {
        for (Tracked t : tracked) {
            t.cancelTasks();
        }
        tracked.clear();
        minecarts.clear();
    }
    
    public void registerTNTMinecart(ExplosiveMinecart cart, Player player) {
        minecarts.put(cart, player);
    }
    
    public Player getTNTMinecartSource(ExplosiveMinecart cart) {
        return minecarts.get(cart);
    }
    
    /**
     * Registers an explosion, used for tnt minecarts/creepers
     * 
     * @param e
     */
    public void registerExplosion(EntityExplodeEvent e) {
        Entity exploded = e.getEntity();
        EntityType type = e.getEntityType();
        
        // Must be tnt minecart/creeper
        if (type != EntityType.CREEPER && type != EntityType.MINECART_TNT) {
            return;
        }
        
        // Must be in arena
        Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(exploded.getWorld());
        if (arena == null) {
            return;
        }
        
        // Must have a source
        Player source = ConfigUtils.getAssociatedPlayer(exploded, arena);
        if (source == null) {
            return;
        }
        
        // Register tnt around
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            // Set source
            for (Entity entity : exploded.getNearbyEntities(3, 3, 3)) {
                if (entity.getType() == EntityType.PRIMED_TNT) {
                    TNTPrimed tnt = (TNTPrimed) entity;
                    Entity actualSource = tnt.getSource();
                    if (actualSource == null || actualSource.getType() == type) {
                        tnt.setSource(source);
                    }
                }
            }
            
            // Remove from tracker if tnt minecart
            if (type == EntityType.MINECART_TNT) {
                ExplosiveMinecart cart = (ExplosiveMinecart) exploded;
                minecarts.remove(cart);
            }
        }, 1);
    }
    
    /**
     * Registers the piston event to all missiles that
     * contain the location of this event.
     * 
     * @param e
     */
    public void registerPistonEvent(BlockPistonExtendEvent e) {
        BlockFace direction = e.getDirection();
        Material material = e.getBlock().getType();
        for (Tracked t : tracked) {
            if (t instanceof TrackedMissile && t.contains(e.getBlock().getLocation())) {
                TrackedMissile missile = (TrackedMissile) t;
                if ((material == Material.PISTON && direction == missile.getDirection()) ||
                    (material == Material.STICKY_PISTON && direction == missile.getDirection().getOppositeFace())) {
                    missile.registerPiston();
                }
            }
        }
    }
    
    /**
     * Gets the most likely prime source given a tnt prime location
     * 
     * @param l
     * @return
     */
    public void assignPrimeSource(TNTPrimeEvent event) {
        // Check if explosion or redstone related event
        PrimeReason reason = event.getReason();
        if (!(reason == PrimeReason.REDSTONE || reason == PrimeReason.EXPLOSION)) {
            return;
        }
        Location l = event.getBlock().getLocation();
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player igniter = null;
        
        // Check if any oldest missile contained the tnt
        for (int i = 0; i < tracked.size(); i++) {
            Tracked t = tracked.get(i);
            if (!(t instanceof TrackedMissile && t.contains(l))) {
                continue;
            }
            TrackedMissile tm = (TrackedMissile) t;
            
            // Shortcut if explosion occurs for an embedded missile
            if (isEmbedded(tm)) {
                igniter = tm.getPlayer();
                break;
            }
            
            // Otherwise if it gets ignited by another tnt ignore
            if (reason == PrimeReason.EXPLOSION) {
                continue;
            }
            
            // If missile is not moving, then should probably check other
            // tracked objects before confirming the igniters identity
            igniter = tm.getPlayer();
            if (!tm.isInMotion()) {
                continue;
            }

            // Check for collisions to other objects, which override the blockbreak
            // Iterate in reverse to check for more recently spawned missile
            for (int j = tracked.size() - 1; j > i; j--) {
                Tracked t2 = tracked.get(j);
                // Cannot be the same team
                if (tm.isRed() == t2.isRed()) {
                    continue;
                }
                if (tm.contains(t2)) {
                    igniter = t2.getPlayer();
                    break;
                }
            }
            break;
        }
        
        if (igniter == null) {
            return;
        }
        
        // Set the TNTPrimed in this location to use the player as source
        Player result = igniter;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity e : l.getNearbyEntities(1, 1, 1)) {
                if (!(e instanceof TNTPrimed)) {
                    continue;
                }
                TNTPrimed tnt = (TNTPrimed) e;
                tnt.setSource(result);
            }
        });
    }
    
    // Checks whether the given tracked object is embedded in the opponent's base
    private boolean isEmbedded(Tracked t) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Arena arena = plugin.getArenaManager().getArena(t.getPos1().getWorld());
        String oppositeTeam = t.isRed() ? "blue" : "red";
        return ConfigUtils.inShield(arena, t.getPos1(), oppositeTeam) || ConfigUtils.inShield(arena, t.getPos2(), oppositeTeam);
    }
}
