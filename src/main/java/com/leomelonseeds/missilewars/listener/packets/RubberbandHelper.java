package com.leomelonseeds.missilewars.listener.packets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.arenas.tracker.Tracked;
import com.leomelonseeds.missilewars.arenas.tracker.TrackedMissile;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

public class RubberbandHelper implements PacketListener, Listener {
    
    // When you're on a missile, each time you are pushed by a piston the server will
    // update your location by sending a movement packet. This movement packet unfortunately
    // only takes into account the yaw and pitch during the push. Since the packet takes
    // ping / 2 time to get sent to the user, by the time the user receives it they might
    // already have a different yaw/pitch, and thus their camera gets glitched back to the
    // position they were ping / 2 time ago.
    
    // Instead, when the packet is sent, we cancel the packet and use our own teleport code
    // to relative teleport the player to the position he is supposed to be in instead. Do this 
    // only if the player is detected to be within the bounds of a tracked missile.
    
    private MissileWarsPlugin plugin;
    private Set<UUID> teleportQueue;
    private Map<UUID, com.github.retrooper.packetevents.protocol.world.Location> clientPosition;
    
    public RubberbandHelper(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.teleportQueue = new HashSet<>();
        this.clientPosition = new HashMap<>();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        // Needs to be position packet
        if (event.getPacketType() != PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            return;
        }
        
        // Make sure player is in an arena
        Player player = event.getPlayer();
        ArenaManager manager = plugin.getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        
        if (playerArena.getTeam(player.getUniqueId()) == TeamName.NONE) {
            return;
        }
        
        // If yaw or pitch is an exact multiple of 90 then it's a server teleport
        WrapperPlayServerPlayerPositionAndLook posPacket = new WrapperPlayServerPlayerPositionAndLook(event);
        if (posPacket.getYaw() % 90 == 0 || posPacket.getPitch() % 90 == 0) {
            return;
        }
        
        // Check if the toLocation is a missile
        Location toLoc = new Location(player.getWorld(), posPacket.getX(), posPacket.getY(), posPacket.getZ());
        boolean isMissile = false;
        for (Tracked t : playerArena.getTracker().getMissiles()) {
            if (!(t instanceof TrackedMissile)) {
                continue;
            }
            
            if (t.contains(toLoc, 1)) {
                isMissile = true;
                break;
            }
        }

        if (!isMissile) {
            return;
        }

        // Checks if the player might fall off the missile if they weren't teleported
        // This checks the blocks around the current client position's hitbox to see if they would land on a block
        // To do this, we check 5 locations: the center and corners of the player's hitbox (which is 0.6 wide)
        boolean wouldFall = true;
        UUID uuid = player.getUniqueId();
        Location curPlayerPos = SpigotConversionUtil.toBukkitLocation(player.getWorld(), clientPosition.get(uuid));
        Location[] toCheck = new Location[5];
        toCheck[0] = curPlayerPos.clone().add( 0,   -1,    0);
        toCheck[1] = curPlayerPos.clone().add( 0.3, -1,  0.3);
        toCheck[2] = curPlayerPos.clone().add( 0.3, -1, -0.3);
        toCheck[3] = curPlayerPos.clone().add(-0.3, -1,  0.3);
        toCheck[4] = curPlayerPos.clone().add(-0.3, -1, -0.3);
        for (Location check : toCheck) {
            if (!check.getBlock().getType().isAir()) {
                wouldFall = false;
                break;
            }
        }
        
        // Cancel the teleport, but only if the player would not fall off the missile
        if (player.hasPermission("umw.positionrubberbandfix") && !wouldFall) {
            posPacket.setRelative(RelativeFlag.X, true);
            posPacket.setRelative(RelativeFlag.Y, true);
            posPacket.setRelative(RelativeFlag.Z, true);
            posPacket.setPosition(new Vector3d());
            
            // Sync server location with client by teleporting them
            if (!teleportQueue.contains(uuid)) {
                Location teleportTo = curPlayerPos.clone();
                if (teleportTo.getYaw() == 0) {
                    teleportTo.setYaw(player.getYaw());
                    teleportTo.setPitch(player.getPitch());
                }
                teleportQueue.add(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> player.teleport(teleportTo));
            } else {
                teleportQueue.remove(uuid);
            }
        }

        posPacket.setRelative(RelativeFlag.PITCH, true);
        posPacket.setRelative(RelativeFlag.YAW, true);
        posPacket.setPitch(0);
        posPacket.setYaw(0);
        posPacket.write();
    }
    
    // Tracks the client's position so we know where to teleport them to
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
            WrapperPlayClientPlayerPosition posPacket = new WrapperPlayClientPlayerPosition(event);
            clientPosition.put(event.getUser().getUUID(), posPacket.getLocation());
        }
        
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerPositionAndRotation posPacket = new WrapperPlayClientPlayerPositionAndRotation(event);
            clientPosition.put(event.getUser().getUUID(), posPacket.getLocation());
        }
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clientPosition.remove(event.getPlayer().getUniqueId());
    }
}
