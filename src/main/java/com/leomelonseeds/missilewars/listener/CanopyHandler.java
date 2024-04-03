package com.leomelonseeds.missilewars.listener;

import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CanopyHandler {
    
    public static HashMap<Player, CanopyHandler> instances;
    
    private Player player;
    private ItemStack item;
    
    public CanopyHandler(Player player, ItemStack item) {
        this.player = player;
        this.item = item;
    }

}
