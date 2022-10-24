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
    
    public void clear() {
        tracked.clear();
    }
    
    public void add(Tracked t) {
        tracked.add(t);
    }
    
    public void remove(Tracked t) {
        tracked.remove(t);
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
            if (t instanceof TrackedMissile) {
                if (t.contains(e.getBlock().getLocation())) {
                    continue;
                }
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
        if (e.getEntityType() != EntityType.PRIMED_TNT) {
            return;
        }
        Location l = e.getLocation();
        for (Tracked t : tracked) {
            if (t.contains(l)) {
                t.registerExplosion();
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
        MissileWarsPlugin.getPlugin().log("A TNT prime event was passed to the Tracker");
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Tracked source = null;
            // Check for collisions. If none, set source to missile tracked itself
            // Iterate in reverse to check for most recently spawned missile
            for (int i = tracked.size() - 1; i >= 0; i--) {
                Tracked t = tracked.get(i);
                if (t.contains(l)) {
                    source = t;
                    // Check for tracked objects that have collided with the given one
                    for (int j = tracked.size() - 1; j >= 0; j--) {
                        Tracked t2 = tracked.get(j);
                        // Cannot be the same one
                        if (t2.equals(t2)) {
                            continue;
                        }
                        // Cannot be going in the same direction
                        if (t2.getDirection() == t.getDirection()) {
                            continue;
                        }
                        if (t.contains(t2)) {
                            source = t2;
                            break;
                        }
                    }
                    break;
                }
            }
            if (source == null) {
                return;
            }
            MissileWarsPlugin.getPlugin().log("A TNT prime event's source was determined to be a tracked object spawned by " + source.getPlayer().getName());
            // Set the TNTPrimed in this location to use the player as source
            Tracked result = source;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Entity e : l.getNearbyEntities(1, 1, 1)) {
                    if (!(e instanceof TNTPrimed)) {
                        continue;
                    }
                    TNTPrimed tnt = (TNTPrimed) e;
                    tnt.setSource(result.getPlayer());
                    MissileWarsPlugin.getPlugin().log("A TNT's source was set to " + result.getPlayer().getName());
                }
            }, 1);
        });
    }
}
