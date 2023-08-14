package com.leomelonseeds.missilewars.listener.packets;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.tracker.Tracked;
import com.leomelonseeds.missilewars.arenas.tracker.TrackedMissile;

import net.minecraft.world.entity.RelativeMovement;

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
    private static final EquivalentConverter<RelativeMovement> RELATIVE_MOVEMENT_CONVERTER = new EnumWrappers.IndexedEnumConverter<>(RelativeMovement.class, RELATIVE_MOVEMENT_CLASS);
    private MissileWarsPlugin plugin;
    
    public RubberbandHelper(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.POSITION);
        this.plugin = plugin;
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
        
        // Rewrite to relative teleport. Prevent rubberbanding entirely if server
        // location is close enough to clients. Otherwise, send a packet to teleport
        // the client to the server position.
        Set<RelativeMovement> flags = new HashSet<>();
        Location server = player.getLocation();
        Location client = PositionListener.clientPosition.get(player.getUniqueId());
        if (server.distance(client) > 2) {
            // flags.add(RelativeMovement.X);
            // flags.add(RelativeMovement.Y);
            // flags.add(RelativeMovement.Z);
            smd.write(0, client.getX());
            smd.write(1, client.getY());
            smd.write(2, client.getZ());
        }
        flags.add(RelativeMovement.X_ROT);
        flags.add(RelativeMovement.Y_ROT);
        packet.getSets(RELATIVE_MOVEMENT_CONVERTER).write(0, flags);
        smf.write(0, 0.0F);
        smf.write(1, 0.0F);
        
        plugin.log("Server position: " + server);
        plugin.log("Client position: " + client);
        plugin.log("Difference: " + server.clone().subtract(client));
        plugin.log("Distance: " + server.distance(client));
    }
}
