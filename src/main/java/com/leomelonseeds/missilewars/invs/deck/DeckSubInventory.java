package com.leomelonseeds.missilewars.invs.deck;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public abstract class DeckSubInventory {
    
    protected Inventory inv;
    
    public DeckSubInventory(Inventory inv) {
        this.inv = inv;
    }
    
    public abstract void fillItems();
    
    public abstract void registerClick(ItemStack item, int slot, ClickType type);
    
    public abstract String getSubTitle();

}
