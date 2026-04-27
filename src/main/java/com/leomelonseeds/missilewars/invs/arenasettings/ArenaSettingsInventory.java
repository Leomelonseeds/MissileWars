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
import com.leomelonseeds.missilewars.arenas.Arena;
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
    protected boolean viewOnly;
    private ArenaSettings arenaSettings;
    private Arena arena;
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
    public ArenaSettingsInventory(Player player, int size, String title, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, size, viewOnly ? title + " (View Only)" : title);
        this.viewOnly = viewOnly;
        this.arena = arena;
        this.arenaSettings = arena.getArenaSettings();
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
        
        // Make sure player has permission to use this setting
        if (!setting.hasPermission(player)) {
            String rank = settingConfig.getString("settings." + setting.toString() + ".rank");
            ConfigUtils.sendConfigMessage("settings.rank", player, Map.of("%rank%", rank));
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        String value = InventoryUtils.getStringFromItemKey(item, SETTING_VALUE);
        if (value == null) {
            return;
        }
        
        // Parse value if int
        String settingType = settingConfig.getString("settings." + setting.toString() + ".type");
        if (settingType.equals("int")) {
            String[] values = value.split("-");
            if (type.isRightClick()) {
                if (values[0].equals("null")) {
                    ConfigUtils.sendConfigMessage("settings.int-maximum", player);
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                    return;
                }

                value = values[0];
            } else if (type.isLeftClick()) {
                if (values[1].equals("null")) {
                    ConfigUtils.sendConfigMessage("settings.int-minimum", player);
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                    return;
                }
                
                value = values[1];
            } else {
                return;
            }
        }
        
        if (!arena.setSetting(setting, value, settingType)) {
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
            material = (boolean) arenaSettings.getWithQueue(setting) ? sec.getString("item-enabled") : sec.getString("item-disabled");
        } else {
            material = sec.getString("item");
        }
        
        ItemStack item = new ItemStack(Material.valueOf(material));
        ItemMeta meta = item.getItemMeta();
        String name = sec.getString("color") + ConfigUtils.getEnumDisplayString(settingString);
        if (setting.needsPermission()) {
            String rank = settingConfig.getString("settings." + settingString + ".rank");
            name = name + " &8(requires " + rank + "&8)";
        }
        meta.displayName(ConfigUtils.toComponent(name));
        
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
        int cur = (int) arenaSettings.getWithQueue(setting);
        Integer left = cur <= intModifier.getMin() ? null : cur - intModifier.getChange();
        Integer right = cur >= intModifier.getMax() ? null : cur + intModifier.getChange();
        String unit = settingConfig.getString("settings." + setting.toString() + ".unit");
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                res.add(line);
                continue;
            }
            
            res.add(line.replace("%left-value%", left == null ? "" : left + "")
                        .replace("%left-arrow%", left == null ? "" : sec.getString("left-arrow"))
                        .replace("%right-value%", right == null ? "" : right + "")
                        .replace("%right-arrow%", right == null ? "" : sec.getString("right-arrow"))
                        .replace("%value%", cur + "")
                        .replace("%unit%", unit)
                        .replace("%default%", setting.getDefaultValue() + ""));
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
        Object val = arenaSettings.getWithQueue(setting);
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                res.add(line);
                continue;
            }
            
            res.add(line.replace("%value%", val.toString())
                        .replace("%default%", setting.getDefaultValue().toString()));
        }
        
        // Get the next in line
        Object[] vals = val.getClass().getEnumConstants();
        int i = 0;
        while (i < vals.length) {
            if (vals[i].equals(val)) {
                break;
            }
            
            i++;
        }
        
        InventoryUtils.setMetaString(meta, SETTING_VALUE, vals[(i + 1) % vals.length].toString().toUpperCase());
        return res;
    }

    private List<String> getBooleanSettingLore(ArenaSetting setting, ConfigurationSection sec, ItemMeta meta) {
        List<String> res = new ArrayList<>();
        boolean enabled = (boolean) arenaSettings.getWithQueue(setting);
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                res.add(line);
                continue;
            }
            
            res.add(line.replace("%enabled%", enabled ? "&aTrue" : "&cFalse")
                        .replace("%default%", (boolean) setting.getDefaultValue() ? "True" : "False"));
        }

        InventoryUtils.setMetaString(meta, SETTING_VALUE, enabled ? "false" : "true");
        return res;
    }
}
