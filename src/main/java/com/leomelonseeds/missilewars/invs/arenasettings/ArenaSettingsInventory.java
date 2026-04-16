package com.leomelonseeds.missilewars.invs.arenasettings;

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
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public abstract class ArenaSettingsInventory extends MWInventory {
    
    private static NamespacedKey SETTING_KEY;

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
     * @param title
     */
    public ArenaSettingsInventory(Player player, int size, String title, boolean viewOnly, ArenaSettings arenaSettings, MWInventory fromInv) {
        super(player, size, title);
        this.viewOnly = viewOnly;
        this.arenaSettings = arenaSettings;
        this.fromInv = fromInv;
        this.size = size;
        this.settingConfig = ConfigUtils.getConfigFile("messages.yml").getConfigurationSection("settings");
        this.settingSlots = getSettingSlots();
        if (SETTING_KEY == null) {
            SETTING_KEY = new NamespacedKey(MissileWarsPlugin.getPlugin(), "arena-setting");
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
        ItemStack item = new ItemStack(Material.valueOf(sec.getString("item")));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ConfigUtils.toComponent(sec.getString("color") + getSettingDisplayName(settingString)));
        
        switch (type) {
            case "int": return createIntSettingItem(setting);
            case "boolean": return createBooleanSettingItem(setting);
            case "enum": return createEnumSettingItem(setting);
        }
        
        return null;
    }
    
    private ItemStack createIntSettingItem(ArenaSetting setting) {
        ConfigurationSection sec = settingConfig.getConfigurationSection("format.int");
        ItemStack item = new ItemStack(Material.valueOf(sec.getString("item")));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ConfigUtils.toComponent(sec.getString("color") + getSettingDisplayName(setting.toString())));
        return null;
    }
    
    private ItemStack createBooleanSettingItem(ArenaSetting setting) {
        
        return null;
    }
    
    private ItemStack createEnumSettingItem(ArenaSetting setting) {
        
        return null;
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
