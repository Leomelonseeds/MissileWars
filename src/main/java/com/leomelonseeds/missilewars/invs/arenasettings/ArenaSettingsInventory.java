package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.arenas.settings.IntSettingModifier;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public abstract class ArenaSettingsInventory extends MWInventory {
    
    // To store the value that clicking this setting will give
    // boolean: true/false
    // int: "l-r" split on "-"
    // enum: string value of the next enum to uppercase
    private static NamespacedKey SETTING_VALUE;

    private Map<Integer, ArenaSetting> settingSlots;
    private boolean viewOnly;
    private ArenaSettings arenaSettings;
    private MWInventory fromInv;
    private int size;
    private ConfigurationSection settingConfig;

    /**
     * An inventory that provides methods for adding arena settings items, and
     * checks for arena settings clicks. The bottom row is automatically filled
     * with black stained glass panes and the back item.
     * 
     * @param player
     * @param size
     * @param title does not have to be adjusted for view only
     */
    public ArenaSettingsInventory(Player player, int size, String title, boolean viewOnly, ArenaSettings arenaSettings, MWInventory fromInv) {
        super(player, size, viewOnly ? title + " (View Only)" : title);
        this.viewOnly = viewOnly;
        this.arenaSettings = arenaSettings;
        this.fromInv = fromInv;
        this.size = size;
        this.settingConfig = ConfigUtils.getConfigFile("messages.yml").getConfigurationSection("settings");
        this.settingSlots = getSettingSlots();
        if (SETTING_VALUE == null) {
            SETTING_VALUE = new NamespacedKey(MissileWarsPlugin.getPlugin(), "arena-setting");
        }
    }
    
    public abstract Map<Integer, ArenaSetting> getSettingSlots();
    
    @Override
    public void updateInventory() {
        for (int i = size - 9; i < size; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        inv.setItem(size - 5, InventoryUtils.getBackItem());
        
        for (Entry<Integer, ArenaSetting> s : settingSlots.entrySet()) {
            inv.setItem(s.getKey(), createSettingsItem(s.getValue()));
        }
        
        updateSettingsInventory();
    }
    
    public abstract void updateSettingsInventory();

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
        
        ArenaSetting setting = settingSlots.get(slot);
        if (setting == null) {
            registerClick(slot, type, item);
            return;
        }
        
        // Process settings
        if (viewOnly) {
            ConfigUtils.sendConfigMessage("settings.view-only", player);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        String value = InventoryUtils.getStringFromItemKey(item, SETTING_VALUE);
        if (value == null) {
            return;
        }
        
        String settingType = settingConfig.getString("settings." + setting.toString() + ".type");
        // TODO: If type is int we need to parse the value
        if (!arenaSettings.set(setting, value, settingType, true)) {
            ConfigUtils.sendConfigMessage("settings.error", player);
            return;
        }
        
        ConfigUtils.sendConfigSound("use-skillpoint", player);
        inv.setItem(slot, createSettingsItem(setting));
    }
    
    /**
     * Register a click. item is guaranteed not null
     * 
     * @param slot
     * @param type
     * @param item
     */
    public abstract void registerClick(int slot, ClickType type, ItemStack item);
    
    /**
     * Creates a clickable item to edit an arena setting
     * 
     * @param setting
     * @return
     */
    private ItemStack createSettingsItem(ArenaSetting setting) {
        // Get section info
        String settingString = setting.toString();
        String type = settingConfig.getString("settings." + settingString + ".type");
        ConfigurationSection sec = settingConfig.getConfigurationSection("format." + type);
        
        // Create item and set name
        String material;
        if (type.equals("boolean")) {
            material = (boolean) arenaSettings.get(setting) ? sec.getString("item-enabled") : sec.getString("item-disabled");
        } else {
            material = sec.getString("item");
        }
        
        ItemStack item = new ItemStack(Material.valueOf(material));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ConfigUtils.toComponent(sec.getString("color") + getSettingDisplayName(settingString)));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(settingConfig.getStringList("settings." + settingString + ".description"));
        if (arenaSettings.isQueued(setting)) {
            lore.addAll(settingConfig.getStringList("format.queued"));
        }
        
        // Add specific lores and metadata for each item
        if (type.equals("int")) {
            lore.addAll(getIntSettingLore(setting, sec, meta));
        } else if (type.equals("enum")) {
            lore.addAll(getEnumSettingLore(setting, sec, meta));
        } else {
            lore.addAll(getBooleanSettingLore(setting, sec, meta));
        }

        meta.lore(ConfigUtils.toComponent(lore));
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Get int setting lore, while also adding the values to the persistent data container
     * 
     * @param setting
     * @param sec
     * @param meta
     * @return
     */
    private List<String> getIntSettingLore(ArenaSetting setting, ConfigurationSection sec, ItemMeta meta) {
        List<String> res = new ArrayList<>();
        IntSettingModifier intModifier = setting.getIntModifier();
        int cur = (int) arenaSettings.get(setting);
        Integer left = cur <= intModifier.getMin() ? null : cur - intModifier.getChange();
        Integer right = cur >= intModifier.getMax() ? null : cur + intModifier.getChange();
        String unit = settingConfig.getString("settings." + setting.toString() + ".unit");
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                res.add(line);
                continue;
            }
            
            line = line.replace("%left-value%", left == null ? "" : left + "");
            line = line.replace("%left-arrow%", left == null ? "" : sec.getString("left-arrow"));
            line = line.replace("%right-value%", right == null ? "" : right + "");
            line = line.replace("%right-arrow%", right == null ? "" : sec.getString("right-arrow"));
            line = line.replace("%value%", cur + "");
            line = line.replace("%unit%", unit);
            line = line.replace("%default%", setting.getDefaultValue() + "");
            res.add(line);
        }
        
        if (left != null) {
            res.add(sec.getString("lore-decreasable"));
        }
        
        if (right != null) {
            res.add(sec.getString("lore-increasable"));
        }
        
        // This right here is bad coding practice in multiple ways
        InventoryUtils.setMetaString(meta, SETTING_VALUE, left + "-" + right);
        return res;
    }

    private List<String> getEnumSettingLore(ArenaSetting setting, ConfigurationSection sec, ItemMeta meta) {
        List<String> res = new ArrayList<>();
        Object val = arenaSettings.get(setting);
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                res.add(line);
                continue;
            }
            
            line = line.replace("%value%", val.toString());
            line = line.replace("%default%", setting.getDefaultValue().toString());
            res.add(line);
        }
        
        // Get the next in line
        Object[] vals = val.getClass().getEnumConstants();
        int i = 0;
        while (i < vals.length) {
            if (vals[i].equals(arenaSettings.get(setting))) {
                break;
            }
            
            i++;
        }
        
        InventoryUtils.setMetaString(meta, SETTING_VALUE, vals[(i + 1) % vals.length].toString().toUpperCase());
        return res;
    }

    private List<String> getBooleanSettingLore(ArenaSetting setting, ConfigurationSection sec, ItemMeta meta) {
        List<String> res = new ArrayList<>();
        boolean enabled = (boolean) arenaSettings.get(setting);
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                res.add(line);
                continue;
            }
            
            line = line.replace("%enabled%", enabled ? "&aTrue" : "&cFalse");
            line = line.replace("%default%", (boolean) setting.getDefaultValue() ? "&2True" : "&4False");
            res.add(line);
        }

        InventoryUtils.setMetaString(meta, SETTING_VALUE, enabled ? "false" : "true");
        return res;
    }
    
    private String getSettingDisplayName(String setting) {
        StringBuilder ret = new StringBuilder();
        for (String s : setting.split("_")) {
            ret.append(s.substring(0, 1).toUpperCase());
            ret.append(s.substring(1).toLowerCase());
            ret.append(" ");
        }
        
        ret.deleteCharAt(ret.length() - 1);
        return ret.toString();
    }
}
