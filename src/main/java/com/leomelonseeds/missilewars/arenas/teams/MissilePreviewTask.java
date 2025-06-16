package com.leomelonseeds.missilewars.arenas.teams;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.SchematicManager;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

public class MissilePreviewTask extends BukkitRunnable {
    
    private Player player;
    private boolean isRed;
    private String lastName;
    private Location lastLoc;
    private Random random;
    private Set<Integer> entities;
    
    /**
     * Needs to be safe to run async. Create structures synchronously
     * 
     * @param player
     * @param isRed
     */
    public MissilePreviewTask(Player player, boolean isRed) {
        this.player = player;
        this.isRed = isRed;
        this.lastName = "";
        this.random = new Random();
        this.entities = new HashSet<>();
    }
    
    // TODO:
    // - Use FAWE to get clipboard of missile so it doesn't have to be spawned
    // - Save all displayLocs and blockdatas for each spawn
    // - Save a new SchematicLoadResult to the MissileWarsPlayer so structure spawning is only computed once here

    @Override
    public void run() {
        if (player == null) {
            return;
        }
        
        if (player.getLocation().getY() < -64) {
            return;
        }

        // Make sure player is aiming for a block
        Block target = player.getTargetBlock(null, 4);
        if (target == null || target.getType() == Material.AIR) {
            removePreview();
            return;
        }

        // Player must be holding item
        PlayerInventory inv = player.getInventory();
        ItemStack mainhand = inv.getItem(EquipmentSlot.HAND);
        ItemStack offhand = inv.getItem(EquipmentSlot.OFF_HAND);
        ItemStack hand = mainhand.getType() == Material.AIR ? offhand.getType() == Material.AIR ? null : offhand : mainhand;
        if (hand == null || player.hasCooldown(hand.getType())) {
            removePreview();
            return;
        }

        // Item must be a missile
        String structureName = InventoryUtils.getStringFromItem(hand, "item-structure");
        Location loc = target.getLocation();
        if (lastName.equals(structureName) && loc.equals(lastLoc)) {
            return;
        } else {
            removePreview();
        }
        
        if (structureName == null || structureName.contains("shield-") || structureName.contains("platform-") || 
                structureName.contains("torpedo-") || structureName.contains("canopy")) {
            return;
        }
        
        lastName = structureName;
        lastLoc = loc.clone();
        Location temp = new Location(loc.getWorld(), 0, -50, 0);
        Location[] spawns = SchematicManager.getCorners(structureName, temp, isRed, player.hasPermission("umw.oldoffsets"));
        SchematicManager.spawnNBTStructure(player, structureName, temp, isRed, structureName, true, false);
        Location dist = loc.subtract(temp);
        int x1 = Math.min(spawns[0].getBlockX(), spawns[1].getBlockX());
        int y1 = Math.min(spawns[0].getBlockY(), spawns[1].getBlockY());
        int z1 = Math.min(spawns[0].getBlockZ(), spawns[1].getBlockZ());
        int x2 = Math.max(spawns[0].getBlockX(), spawns[1].getBlockX());
        int y2 = Math.max(spawns[0].getBlockY(), spawns[1].getBlockY());
        int z2 = Math.max(spawns[0].getBlockZ(), spawns[1].getBlockZ());
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    Location cur = new Location(loc.getWorld(), x, y, z);
                    Block block = cur.getBlock();
                    if (block.getType().isAir()) {
                        continue;
                    }
                    
                    // If block is command block, set a placeholder to hide it
                    BlockData data = block.getBlockData();
                    if (block.getType() == Material.COMMAND_BLOCK) {
                        data = Material.CHISELED_DEEPSLATE.createBlockData();
                    }
                    
                    // Spawn display entity. To prevent Z fighting, make the entity very
                    // slightly larger and spawn it very slightly back
                    Location displayLoc = cur.add(dist).toBlockLocation().add(-0.001, -0.001, -0.001);
                    spawnDisplay(data, displayLoc);
                }
            }
        }
        
        SchematicManager.setAir(x1, y1, z1, x2, y2, z2, loc.getWorld(), false);
    }
    
    private void spawnDisplay(BlockData blockData, Location loc) {
        // Spawn the entity
        int id = random.nextInt();
        entities.add(id);
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
            id, 
            UUID.randomUUID(), 
            SpigotConversionUtil.fromBukkitEntityType(EntityType.BLOCK_DISPLAY),
            SpigotConversionUtil.fromBukkitLocation(loc),
            0,
            0,
            null
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        
        // Put in metadata
        WrappedBlockState displayState = SpigotConversionUtil.fromBukkitBlockData(blockData);
        List<EntityData<?>> displayData = new ArrayList<>();
        displayData.add(new EntityData<Byte>(0, EntityDataTypes.BYTE, (byte) 0x40));
        displayData.add(new EntityData<Vector3f>(12, EntityDataTypes.VECTOR3F, new Vector3f(1.002f, 1.002f, 1.002f)));
        displayData.add(new EntityData<Integer>(23, EntityDataTypes.BLOCK_STATE, displayState.getGlobalId()));
        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(id, displayData);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
    }
    
    private void removePreview() {
        if (entities.isEmpty()) {
            return;
        }
        
        int[] ids = entities.stream().mapToInt(i -> i).toArray();
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(ids);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        entities.clear();
    }
}
