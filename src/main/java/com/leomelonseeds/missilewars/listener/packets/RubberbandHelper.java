package com.leomelonseeds.missilewars.listener.packets;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.tracker.Tracked;
import com.leomelonseeds.missilewars.arenas.tracker.TrackedMissile;

public class RubberbandHelper extends PacketAdapter implements Listener {
    
    // When you're on a missile, each time you are pushed by a piston the server will
    // update your location by sending a movement packet. This movement packet unfortunately
    // only takes into account the yaw and pitch during the push. Since the packet takes
    // ping / 2 time to get sent to the user, by the time the user receives it they might
    // already have a different yaw/pitch, and thus their camera gets glitched back to the
    // position they were ping / 2 time ago.
    
    // Instead, when the packet is sent, we cancel the packet and use our own teleport code
    // to relative teleport the player to the position he is supposed to be in instead. Do this 
    // only if the player is detected to be within the bounds of a tracked missile.
    
    // Relative teleport modifies the RelativeMovement flags of the PlayServerPosition packet
    // by hooking into NMS. This was absolutely insane to figure out.
    
    private static final Class<?> RELATIVE_MOVEMENT_CLASS = MinecraftReflection.getMinecraftClass("world.entity.RelativeMovement");
    private MissileWarsPlugin plugin;
    public static Set<UUID> teleportQueue = new HashSet<>();
    
    public RubberbandHelper(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.POSITION);
        this.plugin = plugin;
    }
    
    public enum PlayerTeleportFlag {
        X, Y, Z, Y_ROT, X_ROT
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        // Make sure player is in an arena
        Player player = event.getPlayer();
        ArenaManager manager = plugin.getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        
        if (playerArena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }
        
        // If yaw or pitch is an exact multiple of 90 then it's a server teleport
        PacketContainer packet = event.getPacket();
        StructureModifier<Float> smf = packet.getFloat(); // 0 yaw 1 pitch
        if (smf.read(0) % 90 == 0 || smf.read(1) % 90 == 0) {
            return;
        }
        
        // Check if the toLocation is a missile
        StructureModifier<Double> smd = packet.getDoubles(); // 0 x 1 y 2 z
        Location toLoc = new Location(player.getWorld(), smd.read(0), smd.read(1), smd.read(2));
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
        
        // EXTREMELY EXPERIMENTAL RUBBERBAND FIXER
        // Sync server location with client by teleporting them
        UUID uuid = player.getUniqueId();
        if (!teleportQueue.contains(uuid)) {
            PacketContainer clientPacket = PositionListener.clientPosition.get(uuid);
            StructureModifier<Double> lcsmd = clientPacket.getDoubles(); // 0 x 1 y 2 z
            StructureModifier<Float> lcsmf = clientPacket.getFloat(); // 0 yaw 1 pitch
            float yaw = lcsmf.read(0) == 0 ? smf.read(0) : lcsmf.read(0);
            float pitch = lcsmf.read(1) == 0 ? smf.read(1) : lcsmf.read(1);
            teleportQueue.add(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location teleportTo = new Location(player.getWorld(), lcsmd.read(0), lcsmd.read(1), lcsmd.read(2), yaw, pitch);
                player.teleport(teleportTo);
            });
        } else {
            teleportQueue.remove(uuid);
        }
        
        // Rewrite to relative teleport
        Set<PlayerTeleportFlag> flags = new HashSet<>();
        flags.add(PlayerTeleportFlag.X);
        flags.add(PlayerTeleportFlag.Y);
        flags.add(PlayerTeleportFlag.Z);
        flags.add(PlayerTeleportFlag.X_ROT);
        flags.add(PlayerTeleportFlag.Y_ROT);
        packet.getSets(EnumWrappers.getGenericConverter(RELATIVE_MOVEMENT_CLASS, PlayerTeleportFlag.class)).write(0, flags);
        smf.write(0, 0.0F);
        smf.write(1, 0.0F);
        smd.write(0, 0.0);
        smd.write(1, 0.0);
        smd.write(2, 0.0);
    }
}
