package com.leomelonseeds.missilewars.utilities.tracker;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class TrackedMissile extends Tracked {
    
    public static final Map<Double, Integer> speeds = new HashMap<>();
    public static final int minPistonEvents = 2;
    
    private String name;
    private int pistonEventCount;
    private boolean inMotion;
    
    public TrackedMissile(String name, int level, Player player, Location pos1, Location pos2, BlockFace direction) {
        super(player, pos1, pos2, direction);
        this.name = name;
        this.pistonEventCount = 0;
        this.inMotion = true;
        
        double speed = (double) ConfigUtils.getItemValue(name, level, "speed");
        int timer = speeds.get(speed);
        
        removalTasks.add(new BukkitRunnable() {
            @Override
            public void run() {
               updatePosition();
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), timer, timer));
        
        MissileWarsPlugin.getPlugin().log("Tracking a new missile spawned by player " + player.getName());
    }
    
    private void updatePosition() {
        // Checks if piston events were detected
        if (pistonEventCount < minPistonEvents) {
            inMotion = false;
            return;
        }
        
        Vector dir = direction.getDirection().normalize();
        if (name.contains("supersonic")) {
            dir.multiply(2);
        }
        
        pos1.add(dir);
        pos2.add(dir);
        inMotion = true;
        pistonEventCount = 0;
    }
    
    @Override
    public BlockFace getDirection() {
        return direction;
    }
    
    public void registerPiston() {
        pistonEventCount++;
    }

    /**
     *  Returns true if this missile should be removed
     */
    @Override
    public boolean testForRemoval() {
        if (inMotion) {
            return false;
        }
        
        int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        
        int stickyPistonCount = 0;
        int pistonCount = 0;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    Location l = new Location(pos1.getWorld(), x, y, z);
                    Block b = l.getBlock();
                    if (b.getType() == Material.PISTON) {
                        pistonCount++;
                    } else if (b.getType() == Material.STICKY_PISTON) {
                        stickyPistonCount++;
                    }
                    if (stickyPistonCount >= 1 && pistonCount >= 1) {
                        return false;
                    }
                }
            }
        }
        MissileWarsPlugin.getPlugin().log("A missile spawned by player " + player.getName() + " tested positive for removal by emptiness.");
        return true;
    }
}