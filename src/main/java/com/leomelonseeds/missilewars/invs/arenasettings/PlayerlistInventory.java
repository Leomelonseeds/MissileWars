package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.invs.PaginatedInventory;

public class PlayerlistInventory extends PaginatedInventory {
    
    private String listType;
    private String listTypeCapital;
    private String listColor;
    private Set<UUID> players;

    public PlayerlistInventory(Player player, Arena arena, boolean isBlack) {
        super(player, 36, isBlack ? "&cBlacklist" : "&aWhitelist");
        this.listType = isBlack ? "black" : "white";
        this.listTypeCapital = isBlack ? "Black" : "White";
        this.listColor = isBlack ? "&c" : "&a";
        ArenaSettings settings = arena.getArenaSettings();
        this.players = isBlack ? settings.getBlacklist() : settings.getWhitelist();
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
        
    }

}
