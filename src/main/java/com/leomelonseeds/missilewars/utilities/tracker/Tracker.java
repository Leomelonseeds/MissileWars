package com.leomelonseeds.missilewars.utilities.tracker;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

public class Tracker {
    
    List<Tracked> tracked;
    
    public Tracker() {
        tracked = new ArrayList<>();
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
     * Registers an explosion to all missiles/utility that contain
     * the location of the event
     * 
     * @param e
     */
    public void registerExplosionEvent(EntityExplodeEvent e) {
        EntityType entity = e.getEntityType();
        if (entity == EntityType.PRIMED_TNT || entity == EntityType.MINECART_TNT ||
                entity == EntityType.CREEPER) {
            Location l = e.getLocation();
            for (Tracked t : new ArrayList<>(tracked)) {
                if (t.contains(l)) {
                    t.registerExplosion();
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
    public void assignPrimeSource(Location l) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Tracked source = null;
            // Check if any most recently spawned missile contains this primed TNT location
            for (int i = tracked.size() - 1; i >= 0; i--) {
                Tracked t = tracked.get(i);
                if (t.contains(l) && t instanceof TrackedMissile) {
                    source = t;
                    // If missile is moving, check for collisions that caused this primer
                    // Iterate in reverse to check for most recently spawned missile
                    if (((TrackedMissile) t).isInMotion()) {
                        for (int j = tracked.size() - 1; j >= 0; j--) {
                            Tracked t2 = tracked.get(j);
                            // Cannot be going in the same direction
                            if (t2.getDirection() == t.getDirection()) {
                                continue;
                            }
                            // If we got here, then there is no recently spawned structure
                            // that triggered this primer.
                            if (t.equals(t2)) {
                                break;
                            }
                            if (t.contains(t2)) {
                                source = t2;
                                break;
                            }
                        }
                    }
                    break;
                }
            }
            if (source == null) {
                return;
            }
            // Set the TNTPrimed in this location to use the player as source
            Tracked result = source;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Entity e : l.getNearbyEntities(1, 1, 1)) {
                    if (!(e instanceof TNTPrimed)) {
                        continue;
                    }
                    TNTPrimed tnt = (TNTPrimed) e;
                    tnt.setSource(result.getPlayer());
                }
            });
        });
    }
}
