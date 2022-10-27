package com.leomelonseeds.missilewars.utilities.tracker;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockPistonExtendEvent;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;
import com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Tracked source = null;
            // Check if any most recently spawned missile contains this primed TNT location
            for (int i = tracked.size() - 1; i >= 0; i--) {
                Tracked t = tracked.get(i);
                if (t instanceof TrackedMissile && t.contains(l)) {
                    // Shortcut if explosion occurs for an embedded missile
                    if (reason == PrimeReason.EXPLOSION) {
                        if (isEmbedded(t)) {
                            source = t;
                            break;
                        }
                        continue;
                    }
                    source = t;
                    // If missile is moving, check for collisions that caused this primer
                    // Iterate in reverse to check for more recently spawned missile
                    if (((TrackedMissile) t).isInMotion()) {
                        for (int j = tracked.size() - 1; j > i; j--) {
                            Tracked t2 = tracked.get(j);
                            // Cannot be the same team
                            if (t.isRed() == t2.isRed()) {
                                continue;
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
    
    // Checks whether the given tracked object is embedded in the opponent's base
    private boolean isEmbedded(Tracked t) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Arena arena = plugin.getArenaManager().getArena(t.getPos1().getWorld());
        String oppositeTeam = t.isRed() ? "blue" : "red";
        return ConfigUtils.inShield(arena, t.getPos1(), oppositeTeam) || ConfigUtils.inShield(arena, t.getPos2(), oppositeTeam);
    }
}
