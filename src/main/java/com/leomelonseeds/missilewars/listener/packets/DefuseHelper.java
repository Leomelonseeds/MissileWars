package com.leomelonseeds.missilewars.listener.packets;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType;
import com.leomelonseeds.missilewars.MissileWarsPlugin;

/**
 * This class helps high-ping users break TNT and slime
 */
public class DefuseHelper extends PacketAdapter implements Listener {
    
    // Basically, if a slime/honey/tnt is broken that was recently pushed with a piston,
    // and the player mined the block on the client before that piston push was registered
    // on the player's client, then we modify the packet forward one block.
    // 
    // In the below example, assume the player has a ping of 200ms (100 each way)
    // Timeline:
    // 0: Piston extends on server
    // 50: Player mines block on client
    // 100: Piston extension registers on client
    // 150: Block mine registers on server. This packet has registered 150ms after the
    //      piston actually already extended, thus the original target block is no longer there, 
    //      and so nothing happens. However, we know that the player's ping is 200ms, which means
    //      it took 100ms for the piston extension to actually register on the client. Because
    //      current time - 100ms is BEFORE piston extension time + 100ms, we know the packet should
    //      be sent forward to the correct block.
    //      
    // Doing some algebraic simplifying, the condition becomes:
    // cur time < piston extension time + player ping
    // Setting cur time to 0, and assuming piston extension time was recorded and occurred some time
    // before the cur time:
    // 0 < -time since piston extension + ping
    // piston extension time < ping
    //
    // Therefore, if the positive amount of time since the piston extended is less than the player's 
    // ping, that fulfills the conditions needed to move the packet forward one block.
    
    // When a piston event, get the list of blocks. For each block, if it's already stored then
    // move it forward by one internally, and if it doesn't then add it to the list already moved.
    // Then reset the timer for ticks since last moved to 0 and update its direction. Only store
    // SLIME_BLOCK, HONEY_BLOCK, and TNT.
    
    // Every tick, add 1 to the time since every block is pushed. If the location of that block
    // no longer contains that specific block (is air) or has been there for longer than the 
    // slowest speed of missile engines, then remove it from the list
    
    // On packet event, get the world, location, and ping of player who sent the packet. Iterate 
    // through the list of blocks looking for a block that was moved less than player ping ago
    // that also has the same X and Y and has Z in the opposite direction of when it was last moved.
    
    private MissileWarsPlugin plugin;
    private Set<DefuseBlock> blocks;
    private boolean cmeLock;

    public DefuseHelper(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG);
        this.plugin = plugin;
        this.blocks = new HashSet<>();
        this.cmeLock = false;
    }
     
    @Override
    public void onPacketReceiving(PacketEvent event) {
        // Start destroy block is sent when an instabreak block is broken
        PacketContainer packet = event.getPacket();
        if (packet.getPlayerDigTypes().read(0) != PlayerDigType.START_DESTROY_BLOCK) {
            return;
        }

        // If y == 0, then it is instead a drop, eat, shoot arrow, or swap action
        StructureModifier<BlockPosition> sm = packet.getBlockPositionModifier();
        BlockPosition bp = sm.read(0);
        if (bp.getY() == 0) {
            return;
        }
        
        // In the rare cases where a CME may occur, simply quit
        if (cmeLock) {
            return;
        }

        // Once all the checks pass, construct a new BlockPosition(x, y, z +/- 1) depending on missile direction
        Player player = event.getPlayer();
        World world = player.getWorld();
        int ping = player.getPing();
        try {
            for (DefuseBlock db : blocks) {
                if (!db.checkEquality(bp, world)) {
                    continue;
                }
                
                // As mentioned above, only continue if piston was pushed less than player ping time ago
                // Add 10 to the ping for players hovering around the border of ticks
                int since = db.getTicks();
                if (since * 50 > ping + 10) {
                    return;
                }
                
                // If we're handling a moving piston, use spigot's setType to remove the block.
                // Otherwise do packet rewriting for the smoothest experience (no ghost blocks).
                // If the location already contains air, then move the packet forward another block.
                // Then do the same checks again. If the checks happen to fail, then do nothing.
                // I'm sure there's a way to write this with less lines but this is much easier to
                // understand for me.
                BlockPosition newbp = new BlockPosition(bp.getX(), bp.getY(), db.getZ());
                Location bploc = new Location(world, bp.getX(), bp.getY(), db.getZ());
                Block block = world.getBlockAt(bploc);
                if (block.getType() == Material.MOVING_PISTON) {
                    event.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.breakBlock(world.getBlockAt(bploc));
                    });
                    return;
                } else if (block.getType() == Material.AIR) {
                    bploc.setZ(db.getNextZ());
                    block = world.getBlockAt(bploc);
                    if (block.getType() == Material.MOVING_PISTON) {
                        event.setCancelled(true);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.breakBlock(world.getBlockAt(bploc));
                        });
                        return;
                    } else if (block.getType() == Material.AIR) {
                        return;
                    }
                    
                    newbp = new BlockPosition(bp.getX(), bp.getY(), db.getNextZ());
                }
                
                sm.write(0, newbp);  
                return;
            }  
        } catch (ConcurrentModificationException e) {
            plugin.log("Concurrent modification detected, cancelling DefuseHelper...");
            return;
        }
    }
    
    @EventHandler
    public void extend(BlockPistonExtendEvent e) {
        addToList(e.getBlocks(), e);
    }
    
    @EventHandler
    public void retract(BlockPistonRetractEvent e) {
        addToList(e.getBlocks(), e);
    }
    
    private void addToList(List<Block> affectedBlocks, BlockPistonEvent e) {
        // Only consider if piston is pushing north or south
        BlockFace dir = e.getDirection();
        if (!(dir == BlockFace.SOUTH || dir == BlockFace.NORTH)) {
            return;
        }
        
        // Add all non-instabreak blocks to defuseblock list
        for (Block block : affectedBlocks) {
            Material type = block.getType();
            if (type == Material.SLIME_BLOCK || type == Material.TNT || type == Material.HONEY_BLOCK) {
                new DefuseBlock(block.getLocation(), dir, this);
            }
        }
    }
    
    public void setCMELock(boolean yes) {
        cmeLock = yes;
    }
    
    public Set<DefuseBlock> getList(){
        return blocks;
    }
}
