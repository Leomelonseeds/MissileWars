package com.leomelonseeds.missilewars.invs;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class PresetSelector implements MWInventory {
    
    private Inventory inv;
    private String deck;
    private Player player;
    
    public PresetSelector(Player player, String deck) {
        this.player = player;
        this.deck = deck;
    }

    @Override
    public void updateInventory() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // TODO Auto-generated method stub

    }

    @Override
    public Inventory getInventory() {
        // TODO Auto-generated method stub
        return null;
    }

}
