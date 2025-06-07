package com.leomelonseeds.missilewars.listener.packets;

import java.util.Random;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class Pinger implements PacketListener {
    
    Random random;
    Integer id;
    Player player;
    CommandSender sender;
    long sendTime;
    
    public Pinger() {
        random = new Random();
        id = null;
    }
    
    /**
     * Sends a ping packet to the player, and on receive, prints it to
     * the command sender
     * 
     * @param player
     * @param sender can be null
     */
    public void sendPing(Player player, CommandSender sender) {
        this.player = player;
        this.sender = sender;
        id = random.nextInt();
        WrapperPlayServerPing pingPacket = new WrapperPlayServerPing(id);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, pingPacket);
        sendTime = System.currentTimeMillis();
    }
    
    /**
     * Check player ping
     */
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PONG) {
            return;
        }
        
        WrapperPlayClientPong pongPacket = new WrapperPlayClientPong(event);
        if (pongPacket.getId() != id) {
            return;
        }
        
        long ping = System.currentTimeMillis() - sendTime;
        sender.sendMessage(ConfigUtils.toComponent("Live ping of " + player.getName() + ": " + ping));
    }

}
