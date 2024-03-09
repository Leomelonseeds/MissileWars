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
    private int curZ;
    private int ticks;
    
    public DefuseBlock(Location loc, BlockFace direction, DefuseHelper dfh) {
        this.ticks = 0;
        setZ(loc, direction);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (ticks <= TICKS_BEFORE_REMOVAL) {
                    ticks++;
                    return;
                }

                dfh.removeDefuseBlock(loc);
                this.cancel();
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 1, 1);
    }
    
    public int getZ() {
        return curZ;
    }
    
    public void setZ(Location loc, BlockFace direction) {
        curZ = loc.getBlockZ() + (direction == BlockFace.SOUTH ? 1 : -1);
    }
    
    public int getTicks() {
        return ticks;
    }
    
    /**
     * Use for if a new defuseblock is created here
     */
    public void resetTicks() {
        this.ticks = 0;
    }
}
