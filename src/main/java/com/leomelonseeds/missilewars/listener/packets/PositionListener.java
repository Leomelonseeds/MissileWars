package com.leomelonseeds.missilewars.listener.packets;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.leomelonseeds.missilewars.MissileWarsPlugin;

public class PositionListener extends PacketAdapter {
    
    // Records client packets
    public static Map<UUID, PacketContainer> clientPosition = new HashMap<>();
    
    public PositionListener(MissileWarsPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.POSITION, PacketType.Play.Client.POSITION_LOOK);
        this.plugin = plugin;
    }
    
    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        clientPosition.put(player.getUniqueId(), event.getPacket());
    }
}
