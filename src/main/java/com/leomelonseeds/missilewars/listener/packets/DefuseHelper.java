package com.leomelonseeds.missilewars.listener.packets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.comphenix.protocol.ProtocolLibrary;
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
    private Map<Location, DefuseBlock> blocks;

    public DefuseHelper(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG);
        this.plugin = plugin;
        this.blocks = new HashMap<>();
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

        Player player = event.getPlayer();
        World world = player.getWorld();
        int ping = player.getPing();
        Location checkLoc = new Location(world, bp.getX(), bp.getY(), bp.getZ());
        for (int i = 0; i <= 1; i++) {
            DefuseBlock db = blocks.get(checkLoc);
            if (db == null) {
                return;
            }
                
            // As mentioned above, only continue if piston was pushed less than player ping time ago
            // Add 10 to the ping for players hovering around the border of ticks
            int since = db.getTicks();
            if (since * 50 > ping + 10) {
                return;
            }
            
            // If we're handling a moving piston, rewrite packet for the smoothest experience.
            // If the location already contains air, then move the packet forward another block.
            // Then do the same checks again. If the checks happen to fail, then do nothing.
            // Once all the checks pass, construct a new BlockPosition(x, y, z +/- 1) depending on missile direction
            BlockPosition newbp = new BlockPosition(bp.getX(), bp.getY(), db.getZ());
            Location bploc = new Location(world, bp.getX(), bp.getY(), db.getZ());
            Block block = world.getBlockAt(bploc);
            if (block.getType() == Material.MOVING_PISTON) {
                // Since must be 0 or 1 if its a moving piston
                // 0 = 2t delay, 1 = 1t delay
                event.setCancelled(true);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Block cur = world.getBlockAt(bploc);
                    if (cur.getType() != Material.AIR) {
                        player.breakBlock(cur);
                        sm.write(0, newbp);
                    }
                    ProtocolLibrary.getProtocolManager().receiveClientPacket(player, packet, false);
                }, since * -1 + 2);
                return;
            } else if (block.getType() == Material.AIR) {
                if (i == 1) {
                    return;
                }
                
                // If AIR, do the loop again to check if
                // any block have moved 2 forward
                checkLoc.setZ(db.getZ());
                continue;
            }

            sm.write(0, newbp);
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
            if (!(type == Material.SLIME_BLOCK || type == Material.TNT || type == Material.HONEY_BLOCK || type == Material.END_ROD)) {
                continue;
            }
            
            Location loc = block.getLocation();
            DefuseBlock db = blocks.get(loc);
            if (db == null) {
                db = new DefuseBlock(loc, dir, this);
                blocks.put(loc, db);
            } else {
                db.resetTicks();
                db.setZ(loc, dir);
            }
        }
    }
    
    public void removeDefuseBlock(Location loc) {
        blocks.remove(loc);
    }
}
