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
        
        // Rewrite to relative teleport
        if (player.hasPermission("umw.positionrubberbandfix")) {
            posPacket.setRelative(RelativeFlag.X, true);
            posPacket.setRelative(RelativeFlag.Y, true);
            posPacket.setRelative(RelativeFlag.Z, true);
            posPacket.setPosition(new Vector3d());
            
            // Sync server location with client by teleporting them
            UUID uuid = player.getUniqueId();
            if (!teleportQueue.contains(uuid)) {
                com.github.retrooper.packetevents.protocol.world.Location loc = clientPosition.get(uuid);
                Location teleportTo = new Location(player.getWorld(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
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
