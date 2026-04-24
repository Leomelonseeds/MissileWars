package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class VisibilitySettings extends ArenaSettingsInventory {
    
    private final static String configSec = "arena-settings.visibility-settings";
    
    private ConfigurationSection itemSection;
    private Arena arena;

    public VisibilitySettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 18, "Visibility Settings", viewOnly, arena, fromInv);
        this.itemSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection(configSec);
        this.arena = arena;
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            0, ArenaSetting.CAPACITY,
            1, ArenaSetting.IS_PRIVATE,
            2, ArenaSetting.IS_ALWAYS_ONLINE
        );
    }

    @Override
    public void updateSettingsInventory() {
        for (String key : itemSection.getKeys(false)) {
            String sec = configSec + "." + key;
            ItemStack item = InventoryUtils.createItem(sec);
            ItemMeta meta = item.getItemMeta();
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, key);
            item.setItemMeta(meta);
            inv.setItem(itemSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        String id = InventoryUtils.getGUIFromItem(item);
        if (id == null) {
            return;
        }
        
        if (id.equals("whitelist")) {
            new PlayerlistInventory(player, arena, false, this);
        } else if (id.equals("blacklist")) {
            new PlayerlistInventory(player, arena, true, this);
        }
    }

}
