package com.leomelonseeds.missilewars.listener.packets;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

/**
 * Represents a slime, honey, or TNT block which can be instantly mined and defuse a missile
 */
public class DefuseBlock {

    // Max ping to account for is 350, so 7 ticks before removal
    private static final int TICKS_BEFORE_REMOVAL = 7;
    private Location loc;
    private int curZ;
    private int ticks;
    
    public DefuseBlock(Location loc, BlockFace direction, DefuseHelper dfh) {
        this.loc = loc;
        curZ = loc.getBlockZ() + (direction == BlockFace.SOUTH ? 1 : -1);
        ticks = 0;
        
        // Add/Removal task
        dfh.setCMELock(true);
        dfh.getList().add(this);
        dfh.setCMELock(false);
        DefuseBlock block = this;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (ticks > TICKS_BEFORE_REMOVAL) {
                    dfh.setCMELock(true);
                    dfh.getList().remove(block);
                    dfh.setCMELock(false);
                    this.cancel();
                    return;
                }
                
                ticks++;
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 1, 1);
    }
    
    public int getZ() {
        return curZ;
    }
    
    public int getTicks() {
        return ticks;
    }
    
    public Location getLastLoc() {
        return loc;
    }
}
