package com.leomelonseeds.missilewars.listener.packets;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.leomelonseeds.missilewars.MissileWarsPlugin;

public class PositionListener extends PacketAdapter {
    
    private MissileWarsPlugin plugin;
    
    public PositionListener(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.POSITION);
        this.plugin = plugin;
    }
    
    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        PacketContainer packet = event.getPacket();
        StructureModifier<Double> smd = packet.getDoubles(); // 0 x 1 y 2 z
        Location loc = new Location(player.getWorld(), smd.read(0), smd.read(1), smd.read(2));
        plugin.log("Sent packet location: " + loc);
        event.setCancelled(false);
    }
}
