package com.leomelonseeds.missilewars.listener.packets;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.leomelonseeds.missilewars.MissileWarsPlugin;

public class PositionListener extends PacketAdapter {
    
    public static Map<UUID, Location> clientPosition = new HashMap<>();
    
    public PositionListener(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.POSITION);
    }
    
    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        StructureModifier<Double> smd = event.getPacket().getDoubles();
        clientPosition.put(player.getUniqueId(), new Location(player.getWorld(), smd.read(0), smd.read(1), smd.read(2)));
    }
}
