package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.PaginatedInventory;

public class EditRandomItems extends PaginatedInventory {
    
    private RandomItemDistributionSettings distributionSettings;
    private boolean viewOnly;
    private MWInventory fromInv;

    public EditRandomItems(Player player, RandomItemDistributionSettings distributionSettings, boolean viewOnly, MWInventory fromInv) {
        super(player, 36, "Edit Random Items" + (viewOnly ? " (View Only)" : ""));
        this.distributionSettings = distributionSettings;
        this.viewOnly = viewOnly;
        this.fromInv = fromInv;
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
