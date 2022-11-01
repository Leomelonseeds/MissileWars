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
import org.bukkit.event.block.BlockBreakEvent;
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
     * Registers an explosion, used for tnt minecarts
     * 
     * @param e
     */
    public void registerExplosion(EntityExplodeEvent e) {
        if (e.getEntityType() != EntityType.MINECART_TNT) {
            return;
        }
        ExplosiveMinecart cart = (ExplosiveMinecart) e.getEntity();
        Player source = minecarts.remove(cart);
        // Sets source of other tnts
        for (Entity entity : cart.getNearbyEntities(3, 3, 3)) {
            if (entity.getType() == EntityType.PRIMED_TNT) {
                TNTPrimed tnt = (TNTPrimed) entity;
                if (tnt.getSource() == null) {
                    tnt.setSource(source);
                }
            }
        }
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
     * Registers the break event to the most recent missile found
     * 
     * @param e
     */
    public void registerBlockBreakEvent(BlockBreakEvent e) {
        Player player = e.getPlayer();
        for (int i = tracked.size() - 1; i >= 0; i--) {
            Tracked t = tracked.get(i);
            if (t instanceof TrackedMissile && t.contains(e.getBlock().getLocation())) {
                ((TrackedMissile) t).registerBlockBroke(player);
                break;
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
            Player igniter = null;
            // Check if any most recently spawned missile contains this primed TNT location
            for (int i = tracked.size() - 1; i >= 0; i--) {
                Tracked t = tracked.get(i);
                if (t instanceof TrackedMissile && t.contains(l)) {
                    TrackedMissile tm = (TrackedMissile) t;
                    // Shortcut if explosion occurs for an embedded missile
                    if (reason == PrimeReason.EXPLOSION) {
                        if (isEmbedded(tm)) {
                            igniter = tm.getPlayer();
                            break;
                        }
                        continue;
                    }
                    igniter = tm.getPlayer();
                    // If missile is moving, first check for recent blockbreaks registered to the missile
                    // Then check for collisions to other objects, which override the blockbreak
                    // Iterate in reverse to check for more recently spawned missile
                    if (tm.isInMotion()) {
                        if (tm.getBlockBroke() != null) {
                            igniter = tm.getBlockBroke();
                        }
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
                    }
                    break;
                }
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
