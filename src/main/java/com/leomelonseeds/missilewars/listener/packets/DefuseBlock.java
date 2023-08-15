package com.leomelonseeds.missilewars.listener.packets;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.leomelonseeds.missilewars.MissileWarsPlugin;

/**
 * Represents a slime, honey, or TNT block which can be instantly mined and defuse a missile
 */
public class DefuseBlock {

    // Max ping to account for is 350, so 7 ticks before removal
    private static final int TICKS_BEFORE_REMOVAL = 7;
    private World world;
    private int x;
    private int y;
    private int z;
    private int lastZ;
    private int nextZ;
    private int ticks;
    
    public DefuseBlock(Location loc, BlockFace direction, DefuseHelper dfh) {
        world = loc.getWorld();
        x = loc.getBlockX();
        y = loc.getBlockY();
        lastZ = loc.getBlockZ();
        int toAdd = direction == BlockFace.SOUTH ? 1 : -1;
        z = lastZ + toAdd;
        nextZ = z + toAdd;
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
        return z;
    }
    
    public int getNextZ() {
        return nextZ;
    }
    
    public int getTicks() {
        return ticks;
    }
    
    /**
     * @param bp
     * @param world
     * @return If this block is the same as the param, used for blockbreak packet checks.
     *         Checks if corresponding lastZ equals the z of the sent packet
     */
    public boolean checkEquality(BlockPosition bp, World world) {
        int curX = bp.getX();
        int curY = bp.getY();
        int curZ = bp.getZ();
        return this.world.toString().equals(world.toString()) && 
                curX == x && curY == y && curZ == lastZ;
    }
}
