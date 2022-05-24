package com.leomelonseeds.missilewars.invs;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

public interface MWInventory {
    InventoryManager manager = MissileWarsPlugin.getPlugin().getInvs();
    
    public void updateInventory();
    
    public void registerClick(int slot, ClickType type);
    
    public Inventory getInventory();
}
