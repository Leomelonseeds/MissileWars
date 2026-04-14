package com.leomelonseeds.missilewars.invs.arenasettings;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public abstract class ArenaSettingsInventory extends MWInventory {
    
    private static NamespacedKey SETTING_KEY;
    
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
        if (SETTING_KEY == null) {
            SETTING_KEY = new NamespacedKey(MissileWarsPlugin.getPlugin(), "arena-setting");
        }
    }
    
    @Override
    public void updateInventory() {
        for (int i = size - 9; i < size; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        inv.setItem(size - 5, InventoryUtils.getBackItem());
        
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
        
        String settingString = InventoryUtils.getStringFromItemKey(item, SETTING_KEY);
        if (settingString == null) {
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
    protected ItemStack createSettingsItem(ArenaSetting setting) {
        
        return null;
    }
}
