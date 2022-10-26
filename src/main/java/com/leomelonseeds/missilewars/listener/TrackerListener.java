package com.leomelonseeds.missilewars.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.utilities.tracker.Tracker;

public class TrackerListener implements Listener {
    
    @EventHandler
    public void tntPrimed(TNTPrimeEvent e) {
        // Get arena
        World world = e.getBlock().getWorld();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(world);
        if (arena == null) {
            return;
        }
        
        Tracker t = arena.getTracker();
        t.assignPrimeSource(e);
    }
    
    @EventHandler
    public void pistonExtend(BlockPistonExtendEvent e) {
        // Get arena
        World world = e.getBlock().getWorld();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(world);
        if (arena == null) {
            return;
        }
        
        Tracker t = arena.getTracker();
        t.registerPistonEvent(e);
    }
}
