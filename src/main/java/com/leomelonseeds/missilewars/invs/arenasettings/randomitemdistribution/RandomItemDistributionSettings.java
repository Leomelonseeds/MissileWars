package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.arenasettings.ArenaSettingsInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class RandomItemDistributionSettings extends ArenaSettingsInventory {
    
    private ConfigurationSection itemsSection;

    public RandomItemDistributionSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 54, "Random Item Settings", viewOnly, arena, fromInv);
        this.itemsSection = ConfigUtils.getConfigFile("items.yml")
            .getConfigurationSection("arena-settings.random-item-distribution.main-menu");
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            36, ArenaSetting.RANDOM_ITEM_DISTRIBUTION_TIMER,
            37, ArenaSetting.RANDOM_ITEM_BAG_DISTRIBUTION,
            38, ArenaSetting.RANDOM_ITEM_XP_TIMER,
            39, ArenaSetting.RANDOM_ITEM_INVENTORY_LIMIT
        );
    }

    @Override
    public void updateSettingsInventory() {
        // TODO Auto-generated method stub

    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        // TODO Auto-generated method stub

    }

}
