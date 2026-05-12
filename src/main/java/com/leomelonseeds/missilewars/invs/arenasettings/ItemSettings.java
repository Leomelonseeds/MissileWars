package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class ItemSettings extends ArenaSettingsInventory {
    
    private ConfigurationSection itemsSection;

    public ItemSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 54, "Item Settings", viewOnly, arena, fromInv);
        this.itemsSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection("arena-settings.item-settings");
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            36, ArenaSetting.ENABLE_DECREASING_COOLDOWNS,
            37, ArenaSetting.ENABLE_TEAM_BALANCING
        );
    }

    @Override
    public void updateSettingsInventory() {
        // Extra row of glass panes
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        // Deck vs random items
        
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        // TODO Auto-generated method stub

    }

}
