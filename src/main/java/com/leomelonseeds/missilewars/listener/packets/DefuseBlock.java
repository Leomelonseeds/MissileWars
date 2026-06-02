package com.leomelonseeds.missilewars.listener.packets;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

/**
 * Represents a slime, honey, or TNT block which can be instantly mined and defuse a missile
 */
public class DefuseBlock {

    private Location nextLoc;
    private long since;
    private BukkitTask removalTask;
    
    public DefuseBlock(Location nextLoc, BukkitTask removalTask) {
        this.nextLoc = nextLoc;
        this.removalTask = removalTask;
        this.since = System.currentTimeMillis();
    }
    
    public Location getNextLoc() {
        return nextLoc;
    }
    
    public void cancelRemovalTask() {
        this.removalTask.cancel();
    }
    
    public long aliveTime() {
        return System.currentTimeMillis() - since;
    }
}
