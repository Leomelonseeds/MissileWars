package com.leomelonseeds.missilewars.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.comphenix.protocol.wrappers.BlockPosition;

/**
 * Represents a slime, honey, or TNT block which can be instantly mined and defuse a missile
 */
public class DefuseBlock {
    
    private World world;
    private int x;
    private int y;
    private int z;
    private int lastZ;
    private int ticks;
    private Material type;
    
    public DefuseBlock(Block block, BlockFace direction) {
        Location loc = block.getLocation();
        world = loc.getWorld();
        x = loc.getBlockX();
        y = loc.getBlockY();
        lastZ = loc.getBlockZ();
        z = direction == BlockFace.SOUTH ? lastZ + 1 : lastZ - 1;
        ticks = 0;
        type = block.getType();
    }
    
    /**
     * @param newZ
     * 
     * Updates the Z location of the defuse block, also resetting the tick counter
     */
    public void setZ(int newZ) {
        lastZ = z;
        z = newZ;
        ticks = 0;
    }
    
    public int getZ() {
        return z;
    }
    
    /**
     * Increases the ticks since the block has been there
     */
    public void increaseTime() {
        ticks++;
    }
    
    public int getTicks() {
        return ticks;
    }
    
    /**
     * @param location
     * @return If this block is the same as the param, used for piston block checks
     */
    public boolean checkEquality(Location l, Material curType) {
        return l.getWorld().toString().equals(world.toString()) && 
                l.getBlockX() == x && l.getBlockY() == y && l.getBlockZ() == z && curType == type;
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
