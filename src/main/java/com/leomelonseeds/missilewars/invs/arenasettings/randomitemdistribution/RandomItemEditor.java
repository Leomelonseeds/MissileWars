package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.settings.IntSettingModifier;
import com.leomelonseeds.missilewars.arenas.settings.RandomItem;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class RandomItemEditor extends MWInventory {
    
    private Map<Integer, RandomItemSetting> settingSlots;
    private RandomItem randomItem;
    private MWInventory fromInv;
    private String weightStr;
    private ConfigurationSection settingConfig;

    public RandomItemEditor(Player player, RandomItem randomItem, MWInventory fromInv) {
        super(player, 54, "Editing " + ConfigUtils.toPlain(randomItem.getModifiableItem().getItemMeta().displayName()));
        this.randomItem = randomItem;
        this.fromInv = fromInv;
        this.weightStr = ConfigUtils.getConfigFile("items.yml").getString("text.itemstats-random-weight");
        this.settingConfig = ConfigUtils.getConfigFile("messages.yml").getConfigurationSection("settings");
        this.settingSlots = new HashMap<>();
        
        // Add setting slots
        settingSlots.put(29, RandomItemSetting.AMOUNT);
        settingSlots.put(31, RandomItemSetting.MAX);
        settingSlots.put(33, RandomItemSetting.WEIGHT);
        
        // If missile add custom offsets
        // TODO when sideways missiles are added
        // if (randomItem.getModifiableItem().getType().toString().endsWith("SPAWN_EGG")) {
        //     settingSlots.put(10, RandomItemSetting.MISSILE_OFFSET_Z);
        //     settingSlots.put(16, RandomItemSetting.MISSILE_OFFSET_Y);
        // }
    }

    @Override
    public void updateInventory() {
        // Main item
        ItemStack displayItem = randomItem.getItem();
        ItemMeta displayMeta = displayItem.getItemMeta();
        List<Component> lore = displayMeta.lore();
        lore.add(ConfigUtils.toComponent(weightStr.replace("%weight%", randomItem.getWeight() + "")));
        displayMeta.lore(lore);
        displayItem.setItemMeta(displayMeta);
        inv.setItem(13, displayItem);
        
        // Anciliary items
        for (Entry<Integer, RandomItemSetting> e : settingSlots.entrySet()) {
            inv.setItem(e.getKey(), createSettingsItem(e.getValue()));
        }
        
        // Green glass panes surrounding display item
        for (int i : new int[] {3, 4, 5, 12, 14, 21, 22, 23}) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.LIME_STAINED_GLASS_PANE));
        }
        
        // Blue glass panes
        for (int i : new int[] {1, 7, 9, 11, 15, 17, 19, 25, 27, 35, 37, 39, 41, 43}) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLUE_STAINED_GLASS_PANE));
        }

        // Last row as usual
        for (int i = 45; i < 54; i++) {
            if (i == 49) {
                inv.setItem(i, InventoryUtils.getBackItem());
            } else {
                inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, fromInv);
            return;
        }
        
        if (!settingSlots.containsKey(slot)) {
            return;
        }
        
        RandomItemSetting setting = settingSlots.get(slot);
        String value = InventoryUtils.getStringFromItemKey(item, InventoryUtils.SETTING_VALUE_KEY);
        String[] values = value.split("-");
        if (type.isRightClick()) {
            if (values[0].equals("null")) {
                ConfigUtils.sendConfigMessage("settings.int-minimum", player);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }

            value = values[0];
        } else if (type.isLeftClick()) {
            if (values[1].equals("null")) {
                ConfigUtils.sendConfigMessage("settings.int-maximum", player);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }
            
            value = values[1];
        } else {
            return;
        }
        
        setting.setValue(randomItem, Integer.parseInt(value));
        updateInventory();
        ConfigUtils.sendConfigSound("use-skillpoint", player);
    }
    
    /**
     * Creates a clickable item to edit a random item setting
     * I don't care I'm going to copy code from arena settings
     * Here assume the setting is always INT
     * 
     * @param setting
     * @return
     */
    private ItemStack createSettingsItem(RandomItemSetting setting) {
        // Get section info
        String settingString = setting.toString();
        ConfigurationSection sec = settingConfig.getConfigurationSection("format.int");
        
        // Create item and set name
        String material = settingConfig.getString("settings." + settingString + ".item");
        ItemStack item = new ItemStack(Material.valueOf(material));
        ItemMeta meta = item.getItemMeta();
        String name = sec.getString("color") + ConfigUtils.getEnumDisplayString(settingString);
        meta.displayName(ConfigUtils.toComponent(name));
        
        // Add int specific lore
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(settingConfig.getStringList("settings." + settingString + ".description"));
        
        // Add specific lores and metadata for each item
        IntSettingModifier intModifier = setting.getModifier();
        int cur = setting.getCurrentValue(randomItem);
        Integer left = cur <= intModifier.getMin() ? null : Math.max(cur - intModifier.getChange(), intModifier.getMin());
        Integer right = cur >= intModifier.getMax() ? null : Math.min(cur + intModifier.getChange(), intModifier.getMax());
        String unit = settingConfig.getString("settings." + settingString + ".unit");
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                lore.add(line);
                continue;
            }
            
            lore.add(line.replace("%left-value%", left == null ? "" : left + "")
                         .replace("%left-arrow%", left == null ? "" : sec.getString("left-arrow"))
                         .replace("%right-value%", right == null ? "" : right + "")
                         .replace("%right-arrow%", right == null ? "" : sec.getString("right-arrow"))
                         .replace("%value%", cur + "")
                         .replace("%unit%", unit)
                         .replace("%default%", setting.getDefaultValue() + ""));
        }
        
        if (right != null) {
            lore.add(sec.getString("lore-increasable"));
        }
        
        if (left != null) {
            lore.add(sec.getString("lore-decreasable"));
        }
        
        meta.lore(ConfigUtils.toComponent(lore));
        
        InventoryUtils.setMetaString(meta, InventoryUtils.SETTING_VALUE_KEY, left + "-" + right);

        item.setItemMeta(meta);
        
        return item;
    }

}
