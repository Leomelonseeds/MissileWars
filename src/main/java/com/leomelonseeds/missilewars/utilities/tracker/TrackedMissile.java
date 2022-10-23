package com.leomelonseeds.missilewars.utilities.tracker;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class TrackedMissile {
    
    public static final Map<String, Integer> speeds = new HashMap<>();
    
    private String name;
    private Player player;
    Location pos1;
    Location pos2;
    BlockFace direction;
    BukkitTask updatePosition;
    
    public TrackedMissile(String name, Player player, Location pos1, Location pos2, BlockFace direction) {
        this.name = name;
        this.player = player;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.direction = direction;
        
        FileConfiguration itemConfig = ConfigUtils.getConfigFile("items.yml");
        
        
        initializeTask();
    }
    
    private void initializeTask() {
        updatePosition = new BukkitRunnable() {
            @Override
            public void run() {
               
            }
            
        }.runTaskTimer(null, 0, 0);
    }
}