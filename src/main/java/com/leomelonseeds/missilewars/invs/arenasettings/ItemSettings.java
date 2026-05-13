package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
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
        boolean randomItems = arena.getBooleanSetting(ArenaSetting.ENABLE_RANDOM_ITEM_DISTRIBUTION);
        setSelectionItem("deck-item-distribution", !randomItems);
        setSelectionItem("random-item-distribution", randomItems);
    }
    
    private void setSelectionItem(String key, boolean enabled) {
        // Create item
        ItemStack item = new ItemStack(Material.valueOf(itemsSection.getString(key + ".item")));
        String enabledString = enabled ? "enabled" : "disabled";
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ConfigUtils.toComponent(itemsSection.getString(key + ".name-" + enabledString)));
        List<String> lore = new ArrayList<>(itemsSection.getStringList(key + ".lore"));
        lore.addAll(itemsSection.getStringList(key + ".lore-" + enabledString));
        meta.lore(ConfigUtils.toComponent(lore));
        InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, key);
        item.setItemMeta(meta);
        
        // Set slot
        int slot = itemsSection.getInt(key + ".slot");
        inv.setItem(slot, item);
        
        // Get glass panes
        Material pane = enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        int upper = slot - 9;
        int lower = slot + 9;
        for (int s : new int[] {upper - 1, upper, upper + 1, slot - 1, slot + 1, lower - 1, lower, lower + 1}) {
            inv.setItem(s, InventoryUtils.createBlankItem(pane));
        }
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        String key = InventoryUtils.getGUIFromItem(item);
        if (key == null) {
            return;
        }

        boolean randomItems = arena.getBooleanSetting(ArenaSetting.ENABLE_RANDOM_ITEM_DISTRIBUTION);
        if (key.equals("deck-item-distribution")) {
            if (!randomItems) {
                new DeckDistributionSettings(player, viewOnly, arena, this);
                return;
            }
            
            if (viewOnly) {
                viewOnlyDeny();
                return;
            }
            
            if (type.isLeftClick()) {
                arenaSettings.set(ArenaSetting.ENABLE_RANDOM_ITEM_DISTRIBUTION, false);
                ConfigUtils.sendConfigSound("use-skillpoint", player);
                updateInventory();
            } else if (type.isRightClick()) {
                new DeckDistributionSettings(player, viewOnly, arena, this);
            }
            
            return;
        }
        
        if (key.equals("random-item-distribution")) {
            // TEMP
            if (!player.hasPermission("umw.admin")) {
                return;
            }
            
            if (randomItems) {
                // TODO
                return;
            }
            
            if (viewOnly) {
                viewOnlyDeny();
                return;
            }
            
            if (type.isLeftClick()) {
                arenaSettings.set(ArenaSetting.ENABLE_RANDOM_ITEM_DISTRIBUTION, true);
                if (arenaSettings.getRandomItemDistributor() == null) {
                    arenaSettings.setDefaultRandomItemDistributor();
                }
                ConfigUtils.sendConfigSound("use-skillpoint", player);
                updateInventory();
            } else if (type.isRightClick()) {
                // TODO
            }
            
            return;
        }
    }

}
