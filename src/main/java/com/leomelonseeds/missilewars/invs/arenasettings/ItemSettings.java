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
import com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution.RandomItemDistributionSettings;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class ItemSettings extends ArenaSettingsInventory {
    
    private final static String sec = "arena-settings.item-settings";
    
    private ConfigurationSection itemsSection;

    public ItemSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 54, "Item Settings", viewOnly, arena, fromInv);
        this.itemsSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection(sec);
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            // Distribution settings
            28, ArenaSetting.ENABLE_DECREASING_COOLDOWNS,
            29, ArenaSetting.ENABLE_TEAM_BALANCING,
            
            // Usage settings
            37, ArenaSetting.ENABLE_SIDEWAYS_MISSILES,
            38, ArenaSetting.ENABLE_CHIRAL_MISSILES,
            39, ArenaSetting.MISSILE_OFFSET_MODIFIER_Z,
            40, ArenaSetting.MISSILE_OFFSET_MODIFIER_Y,
            41, ArenaSetting.FIREBALLS_NEED_TO_BE_PLACED
        );
    }

    @Override
    public void updateSettingsInventory() {
        // Deck vs random items
        boolean randomItems = arena.getBooleanSetting(ArenaSetting.ENABLE_RANDOM_ITEM_DISTRIBUTION);
        setSelectionItem("deck-item-distribution", !randomItems);
        setSelectionItem("random-item-distribution", randomItems);
        
        // Display
        inv.setItem(27, InventoryUtils.createItem(sec + ".distribution-settings"));
        inv.setItem(36, InventoryUtils.createItem(sec + ".usage-settings"));
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
        boolean selectedRandomItems = false;
        if (key.equals("random-item-distribution")) {
            selectedRandomItems = true;
        } else if (!key.equals("deck-item-distribution")) {
            return;
        }
        
        if (randomItems == selectedRandomItems) {
            if (selectedRandomItems) {
                new RandomItemDistributionSettings(player, viewOnly, arena, this);
            } else {
                new DeckDistributionSettings(player, viewOnly, arena, this);
            }
            return;
        }
        
        if (viewOnly) {
            viewOnlyDeny();
            return;
        }
        
        if (arena.isRunning() || arena.isResetting()) {
            ConfigUtils.sendConfigMessage("cannot-change-setting-while-running", player);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }

        if (type.isLeftClick()) {
            arena.setSetting(ArenaSetting.ENABLE_RANDOM_ITEM_DISTRIBUTION, selectedRandomItems + "", "boolean");
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            updateInventory();
        } else if (type.isRightClick()) {
            if (selectedRandomItems) {
                new RandomItemDistributionSettings(player, viewOnly, arena, this);
            } else {
                new DeckDistributionSettings(player, viewOnly, arena, this);
            }
        }
    }
}
