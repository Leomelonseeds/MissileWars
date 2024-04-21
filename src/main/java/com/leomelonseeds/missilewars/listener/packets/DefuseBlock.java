package com.leomelonseeds.missilewars.listener.packets;

import org.bukkit.block.BlockFace;

/**
 * Represents a slime, honey, or TNT block which can be instantly mined and defuse a missile
 */
public class DefuseBlock {

    // Max ping to account for is 350, so 7 ticks before removal
    private int nextZ;
    private long since;
    
    public DefuseBlock(int nextZ, BlockFace direction) {
        this.nextZ = nextZ;
        this.since = System.currentTimeMillis();
    }
    
    public int getZ() {
        return nextZ;
    }
    
    public long aliveTime() {
        return System.currentTimeMillis() - since;
    }
}
