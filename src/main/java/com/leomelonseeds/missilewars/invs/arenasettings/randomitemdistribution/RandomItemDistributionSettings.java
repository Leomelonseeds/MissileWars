package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.arenasettings.ArenaSettingsInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class RandomItemDistributionSettings extends ArenaSettingsInventory {
    
    private final static String sec = "arena-settings.random-item-distribution.main-menu";
    
    private ConfigurationSection itemsSection;

    public RandomItemDistributionSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 54, "Random Item Settings", viewOnly, arena, fromInv);
        this.itemsSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection(sec);
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
        for (int i = 0; i < 27; i++) {
            if (i <= 9 || i >= 17) {
                inv.setItem(i, InventoryUtils.createBlankItem(Material.MAGENTA_STAINED_GLASS_PANE));
            }
        }
        
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        for (String key : itemsSection.getKeys(false)) {
            ItemStack item = InventoryUtils.createItem(sec + "." + key);
            ItemMeta meta = item.getItemMeta();
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);    
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, key);
            item.setItemMeta(meta);
            inv.setItem(itemsSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        // TODO Auto-generated method stub
    }

}
