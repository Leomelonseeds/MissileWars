package com.leomelonseeds.missilewars.arenas.tracker;

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
    public static final int minPistons = 1;
    public static final int minStickyPistons = 1;
    public static final int minSlimeBlocks = 2;
    
    private int pistonEventCount;
    private boolean inMotion;
    private String name;
    
    public TrackedMissile(String name, int level, Player player, Location pos1, Location pos2, BlockFace direction, boolean isRed) {
        super(player, pos1, pos2, direction, isRed);
        this.pistonEventCount = 0;
        this.inMotion = false;
        this.name = name;
        
        double speed = (double) ConfigUtils.getItemValue(name, level, "speed");
        int timer = speeds.get(speed);
        
        removalTasks.add(new BukkitRunnable() {
            @Override
            public void run() {
               updatePosition();
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), timer, timer));
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
        pistonEventCount = 0;
        inMotion = true;
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
        int slimeCount = 0;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    Location l = new Location(pos1.getWorld(), x, y, z);
                    Block b = l.getBlock();
                    if (b.getType() == Material.PISTON) {
                        pistonCount++;
                    } else if (b.getType() == Material.STICKY_PISTON) {
                        stickyPistonCount++;
                    } else if (b.getType() == Material.SLIME_BLOCK) {
                        slimeCount++;
                    }
                    if (stickyPistonCount >= minStickyPistons && pistonCount >= minPistons && slimeCount >= minSlimeBlocks) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    public boolean isInMotion() {
        return inMotion;
    }
}