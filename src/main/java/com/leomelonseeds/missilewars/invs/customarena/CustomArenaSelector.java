package com.leomelonseeds.missilewars.invs.customarena;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.invs.PaginatedMWIInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class CustomArenaSelector extends PaginatedMWIInventory {
    
    private final static int SIZE = 36;
    
    private Player player;

    public CustomArenaSelector(Player player) {
        super(player, SIZE, ConfigUtils.getConfigText("inventories.custom-game-selector.title"));
        
        this.player = player;
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        // TODO Auto-generated method stub
        
    }
}
