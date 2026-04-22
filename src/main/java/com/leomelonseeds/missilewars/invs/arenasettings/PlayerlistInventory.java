package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.invs.PaginatedInventory;

public abstract class PlayerlistInventory extends PaginatedInventory {

    public PlayerlistInventory(Player player, String title, Set<UUID> players, Arena arena) {
        super(player, 36, title);
        // TODO Auto-generated constructor stub
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
