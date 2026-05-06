package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class OtherSettings extends ArenaSettingsInventory {
    
    private static final String sec = "arena-settings.other-settings";
    
    private ConfigurationSection itemsSection;

    public OtherSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 36, "Other Settings", viewOnly, arena, fromInv);
        this.itemsSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection(sec);
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        Map<Integer, ArenaSetting> settingSlots = new HashMap<>();
        // Game mgmt
        settingSlots.put(1, ArenaSetting.ENABLE_AUTO_START);
        settingSlots.put(2, ArenaSetting.START_TIMER);
        settingSlots.put(3, ArenaSetting.TIE_TIMER);
        settingSlots.put(4, ArenaSetting.IS_INFINITE_TIME);
        settingSlots.put(5, ArenaSetting.END_IF_NO_PLAYERS);
        
        // Player/team mgmt
        settingSlots.put(10, ArenaSetting.ENABLE_UNFAIR_TEAMS);
        settingSlots.put(11, ArenaSetting.ONLY_JOIN_QUEUED_PLAYERS);
        settingSlots.put(12, ArenaSetting.ENABLE_AFK_KICK);
        
        // Game rules
        settingSlots.put(19, ArenaSetting.ENABLE_MISSILE_COOLDOWN);
        settingSlots.put(20, ArenaSetting.ENABLE_ALTITUDE_SICKNESS);
        settingSlots.put(21, ArenaSetting.WORLD_DIFFICULTY);
        return settingSlots;
    }

    @Override
    public void updateSettingsInventory() {
        for (String key : itemsSection.getKeys(false)) {
            ItemStack item = InventoryUtils.createItem(sec + "." + key);
            inv.setItem(itemsSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {}

}
