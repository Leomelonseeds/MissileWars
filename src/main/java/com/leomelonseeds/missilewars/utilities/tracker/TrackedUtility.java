package com.leomelonseeds.missilewars.utilities.tracker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class TrackedUtility extends Tracked {

    public static final int minBlocksBeforeRemoval = 5;

    public TrackedUtility(String name, int level, Player player, Location pos1, Location pos2, BlockFace direction, boolean isRed) {
        super(player, pos1, pos2, direction, isRed);
        
        if (name.contains("obsidianshield")) {
            int duration = (int) ConfigUtils.getItemValue(name, level, "duration");
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                remove();
            }, duration * 20);
        }
    }

    @Override
    public boolean testForRemoval() {
        int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        
        int blockCount = 0;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    Location l = new Location(pos1.getWorld(), x, y, z);
                    Block b = l.getBlock();
                    if (b.getType() != Material.AIR) {
                        blockCount++;
                    }
                    if (blockCount > minBlocksBeforeRemoval) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

}
