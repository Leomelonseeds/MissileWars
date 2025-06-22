package com.leomelonseeds.missilewars.listener.packets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.schem.SchematicLoadResult;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

public class MissilePreview extends BukkitRunnable implements PacketListener {
    
    // In how many ms should we update after the user isn't moving
    private static final int UPDATE_FREQUENCY = 100;
    
    // Keeps track of preview specific variables
    private Player player;
    private User packetUser;
    private boolean isRed;
    private String lastName;
    private Location lastLoc;
    private Random random;
    private Map<Integer, String> entities;
    private SchematicLoadResult curResult;
    private Set<Integer> removalQueue;
    
    // Packetlistener player position tracking
    private PacketListenerCommon listener;
    private Location playerEyeLocation;
    private Vector playerEyeDirection;
    private boolean isShifted;
    
    // Update parameters
    boolean isUpdating;
    long lastMovementUpdateTime;
    long lastCollisionCheckTime;
    
    /**
     * Needs to be safe to run async.
     * 
     * @param player
     * @param isRed
     */
    public MissilePreview(Player player, boolean isRed) {
        this.player = player;
        this.packetUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
        this.isRed = isRed;
        this.lastName = "";
        this.random = new Random();
        this.entities = new HashMap<>();
        this.removalQueue = new HashSet<>();
        this.listener = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.MONITOR);
        this.playerEyeLocation = player.getLocation();
        this.playerEyeDirection = new Vector(0, 0, 1);
        this.runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), 20, 1);
    }

    @Override
    public void run() {
        if (System.currentTimeMillis() - lastMovementUpdateTime <= UPDATE_FREQUENCY) {
            return;
        }
        
        safeUpdate(false);
    }
    
    // Update location each time any packet that may change the player's eye location is received
    // Packet checks allow the preview to respond faster to changes
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!player.equals(event.getPlayer())) {
            return;
        }
        
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.PLAYER_INPUT) {
            WrapperPlayClientPlayerInput inputPacket = new WrapperPlayClientPlayerInput(event);
            if (inputPacket.isShift() == isShifted || player.isSwimming()) {
                return;
            }
            
            isShifted = !isShifted;
            if (isShifted) {
                playerEyeLocation.subtract(0, 0.35, 0);
            } else {
                playerEyeLocation.add(0, 0.35, 0);
            }
            movementUpdate();
            return;
        }
        
        if (type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerPositionAndRotation posPacket = new WrapperPlayClientPlayerPositionAndRotation(event);
            setPlayerPosition(posPacket.getPosition());
            setPlayerRotation(posPacket.getYaw(), posPacket.getPitch());
            movementUpdate();
            return;
        }
        
        if (type == PacketType.Play.Client.PLAYER_POSITION) {
            WrapperPlayClientPlayerPosition posPacket = new WrapperPlayClientPlayerPosition(event);
            setPlayerPosition(posPacket.getPosition());
            movementUpdate();
            return;
        }
        
        if (type == PacketType.Play.Client.PLAYER_ROTATION) {
            WrapperPlayClientPlayerRotation posPacket = new WrapperPlayClientPlayerRotation(event);
            setPlayerRotation(posPacket.getYaw(), posPacket.getPitch());
            movementUpdate();
            return;
        }
    }
    
    private void setPlayerPosition(Vector3d position) {
        double y = position.getY();
        if (player.isSwimming()) {
            y += 0.4;
        } else if (isShifted) {
            y += 1.27;
        } else {
            y += 1.62;
        }
        
        playerEyeLocation.set(position.getX(), y, position.getZ());
    }
    
    private void setPlayerRotation(double yaw, double pitch) {
        yaw = Math.toRadians(yaw);
        pitch = Math.toRadians(pitch);
        playerEyeDirection.setX(-Math.cos(pitch) * Math.sin(yaw));
        playerEyeDirection.setY(-Math.sin(pitch));
        playerEyeDirection.setZ(Math.cos(pitch) * Math.cos(yaw));
    }
    
    private void movementUpdate() {
        safeUpdate(true);
    }
    
    private void safeUpdate(boolean movement) {
        if (isUpdating) {
            return;
        }

        isUpdating = true;
        update();
        if (movement) {
            lastMovementUpdateTime = System.currentTimeMillis();
        }
        isUpdating = false;
    }
    
    private void update() {
        if (!removalQueue.isEmpty()) {
            removeEntities(removalQueue);
        }
        
        if (playerEyeLocation.getY() < -64) {
            return;
        }
        
        // Raytrace blocks from player direction
        // Moving pistons don't get detected by raytraces, so we need another check for those
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(playerEyeLocation, playerEyeDirection, 4.5);
        for (int i = 1; i <= 9; i++) {
            double dist = i * 0.5;
            Location check = playerEyeLocation.clone().add(playerEyeDirection.clone().multiply(dist));
            if (check.getBlock().getType() != Material.MOVING_PISTON) {
                continue;
            }
            
            // Check if the moving piston is closer to the player than the raytraced block
            // We don't break early if another block is detected since this check doesn't consider exact hitboxes
            if (rayTrace == null || dist * dist < rayTrace.getHitPosition().distanceSquared(playerEyeLocation.toVector())) {
                rayTrace = new RayTraceResult(check.toVector(), check.getBlock(), null);
            }
            
            break;
        }
            
        if (rayTrace == null) {
            removeEntities();
            return;
        }

        // Player must be holding item
        PlayerInventory inv = player.getInventory();
        ItemStack mainhand = inv.getItem(EquipmentSlot.HAND);
        ItemStack offhand = inv.getItem(EquipmentSlot.OFF_HAND);
        ItemStack hand = mainhand.getType() == Material.AIR ? offhand.getType() == Material.AIR ? null : offhand : mainhand;
        if (hand == null || player.hasCooldown(hand.getType())) {
            removeEntities();
            return;
        }

        // Item must be a missile
        String structureName = InventoryUtils.getStringFromItem(hand, "item-structure");
        if (!InventoryUtils.isMissile(structureName)) {
            removeEntities();
            return;
        }
        
        // Computation reducer:
        // If structure and location are the same, and we don't need to recheck collisions yet, then proceed
        Location loc = rayTrace.getHitBlock().getLocation();
        if (structureName.equals(lastName) && loc.equals(lastLoc)) {
            // If same missile in same location, we might still need to collision check
            boolean collisionChanged = false;
            if (System.currentTimeMillis() - lastCollisionCheckTime >= UPDATE_FREQUENCY) {
                boolean curAllowSpawn = curResult.isAllowSpawn();
                curResult.checkCollisions();
                if (curAllowSpawn != curResult.isAllowSpawn()) {
                    collisionChanged = true;
                }
                
                lastCollisionCheckTime = System.currentTimeMillis();
            }
            
            if (!entities.isEmpty() && !collisionChanged) {
                return;
            }
        } else {
            curResult = SchematicManager.loadNBTStructure(player, structureName, loc, isRed, structureName, true, true);
            lastCollisionCheckTime = System.currentTimeMillis();
        }
        
        // Write all packets at once and then flush
        // If spawning missile, remove old entities in the next update to ensure smoothness
        // It is basically guaranteed to have another update happen 1 tick later due to player movements
        removalQueue.addAll(entities.keySet());
        entities.clear();
        if (curResult.isAllowSpawn()) {
            curResult.getBlocks().forEach(p -> writeBlockDisplay(p.getLeft(), p.getRight()));
        } else {
            curResult.getBlocks().forEach(p -> writeOutlineDisplay(p.getLeft()));
            writeAddToTeam(MissileWarsPlugin.getPlugin().getVanillaTeams().getTeamWithColor(Color.RED));
            removeEntities(removalQueue);
        }
        packetUser.flushPackets();
        
        // Set previous variables to save on computations
        lastName = structureName;
        lastLoc = loc.clone();
    }
     
    // White-outlined display showing all the blocks of the missile
    private void writeBlockDisplay(Location loc, BlockData blockData) {
        // Spawn the display (move loc back slightly to remove z-fighting)
        int id = random.nextInt();
        UUID uuid = UUID.randomUUID();
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
            id, 
            UUID.randomUUID(), 
            SpigotConversionUtil.fromBukkitEntityType(EntityType.BLOCK_DISPLAY),
            SpigotConversionUtil.fromBukkitLocation(loc.clone().add(-0.001, -0.001, -0.001)),
            0,
            0,
            null
        );
        packetUser.writePacket(spawnPacket);
        
        // Put in metadata
        WrappedBlockState displayState = SpigotConversionUtil.fromBukkitBlockData(blockData);
        List<EntityData<?>> displayData = new ArrayList<>();
        displayData.add(new EntityData<Byte>(0, EntityDataTypes.BYTE, (byte) 0x40));
        displayData.add(new EntityData<Vector3f>(12, EntityDataTypes.VECTOR3F, new Vector3f(1.002f, 1.002f, 1.002f)));
        displayData.add(new EntityData<Integer>(23, EntityDataTypes.BLOCK_STATE, displayState.getGlobalId()));
        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(id, displayData);
        packetUser.writePacket(metaPacket);
        entities.put(id, uuid.toString());
    }
    
    /**
     * Add every entity in the currently spawned list to a vanilla team
     */
    private void writeAddToTeam(String teamName) {
        WrapperPlayServerTeams teamPacket = new WrapperPlayServerTeams(
                teamName,
                TeamMode.ADD_ENTITIES,
                (ScoreBoardTeamInfo) null,
                entities.values()
            );
        packetUser.writePacket(teamPacket);
    }
    
    // Red, outline-only display for when the missile cannot be spawned
    private void writeOutlineDisplay(Location loc) {
        // Spawn the slime
        int id = random.nextInt();
        UUID uuid = UUID.randomUUID();
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
            id, 
            uuid, 
            SpigotConversionUtil.fromBukkitEntityType(EntityType.SLIME),
            SpigotConversionUtil.fromBukkitLocation(loc.toCenterLocation().add(0, -0.5, 0)),
            0,
            0,
            null
        );
        packetUser.writePacket(spawnPacket);
        
        // Put in metadata (glowing + invisible)
        List<EntityData<?>> displayData = new ArrayList<>();
        displayData.add(new EntityData<Byte>(0, EntityDataTypes.BYTE, (byte) 0x60));
        displayData.add(new EntityData<Integer>(16, EntityDataTypes.INT, 2));
        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(id, displayData);
        packetUser.writePacket(metaPacket);
        entities.put(id, uuid.toString());
    }
    
    /**
     * Remove all entities
     */
    private void removeEntities() {
        removeEntities(entities.keySet());
    }
    
    /**
     * Remove all entities with ids
     * 
     * @param ents
     */
    private void removeEntities(Set<Integer> ents) {
        if (ents.isEmpty()) {
            return;
        }
        
        int[] ids = ents.stream().mapToInt(i -> i).toArray();
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(ids);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        ents.clear();
    }
    
    public void disable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        this.cancel();
        if (!removalQueue.isEmpty()) {
            removeEntities(removalQueue);
        }
        removeEntities();
    }
}
