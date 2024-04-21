package com.leomelonseeds.missilewars.listener.packets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
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
import org.bukkit.scheduler.BukkitTask;

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
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

/**
 * This class helps high-ping users break TNT and slime
 */
public class DefuseHelper extends PacketAdapter implements Listener {
    
    private static final int TICKS_BEFORE_REMOVAL = 7;
    
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
    
    private Map<Location, Pair<DefuseBlock, BukkitTask>> blocks;
    private long stupid;
    private MissileWarsPlugin plugin;

    public DefuseHelper(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG);
        this.plugin = plugin;
        this.blocks = new HashMap<>();
    }
     
    @Override
    public void onPacketReceiving(PacketEvent event) {
        // Start destroy block is sent when an instabreak block is broken
        long time = System.currentTimeMillis();
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
        
        // Dumb way of waiting a bit (it works, don't touch it)
        this.stupid = 0;
        Random rand = new Random(0);
        for (int i = 0; i < plugin.getConfig().getInt("experimental.dh-iterations"); i++) {
            this.stupid += rand.nextInt(0, 10);
        }
        plugin.debug("DH process delay: " + (System.currentTimeMillis() - time) + ", Avg tick time: " + Bukkit.getAverageTickTime());
        
        Player player = event.getPlayer();
        World world = player.getWorld();
        Location checkLoc = new Location(world, bp.getX(), bp.getY(), bp.getZ());
        for (int i = 0; i <= 1; i++) {
            Pair<DefuseBlock, BukkitTask> pdb = blocks.get(checkLoc);
            if (pdb == null) {
                return;
            }
            
                
            // As mentioned above, only continue if piston was pushed less than player ping time ago
            DefuseBlock db = pdb.getLeft();
            long aliveTime = db.aliveTime();
            long adjustedPing = player.getPing() + plugin.getConfig().getInt("experimental.dh-bias");
            plugin.debug("Block found, alive time: " + aliveTime);
            if (aliveTime > adjustedPing) {
                plugin.debug("RIP, adjusted ping was " + adjustedPing);
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
                int delay = (int) (aliveTime / 50) * -1 + 2;
                ConfigUtils.schedule(delay, () -> {
                    Block cur = world.getBlockAt(bploc);
                    if (cur.getType() != Material.AIR) {
                        player.breakBlock(cur);
                        sm.write(0, newbp);
                    }
                    ProtocolLibrary.getProtocolManager().receiveClientPacket(player, packet, false);
                });
                plugin.debug("b36: Packet sent forward " + delay + "t");
                return;
            } else if (block.getType() == Material.AIR) {
                if (i == 1) {
                    return;
                }

                // If AIR, do the loop again to check if
                // any block have moved 2 forward
                plugin.debug("Found air, checking again...");
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
            Pair<DefuseBlock, BukkitTask> pdb = blocks.get(loc);
            if (pdb != null) {
                pdb.getRight().cancel();
            }
            
            int nextZ = loc.getBlockZ() + (dir == BlockFace.SOUTH ? 1 : -1);
            DefuseBlock db = new DefuseBlock(nextZ, dir);
            blocks.put(loc, Pair.of(db, ConfigUtils.schedule(TICKS_BEFORE_REMOVAL, () -> blocks.remove(loc))));
        }
    }
    
    public long placeholder() {
        return stupid;
    }
}
