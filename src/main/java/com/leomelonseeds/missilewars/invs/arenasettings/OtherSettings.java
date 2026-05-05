package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;

public class OtherSettings extends ArenaSettingsInventory {

    public OtherSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 36, "Other Settings", viewOnly, arena, fromInv);
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        Map<Integer, ArenaSetting> settingSlots = new HashMap<>();
        settingSlots.put(1, ArenaSetting.ENABLE_AUTO_START);
        settingSlots.put(2, ArenaSetting.START_TIMER);
        settingSlots.put(3, ArenaSetting.TIE_TIMER);
        settingSlots.put(4, ArenaSetting.IS_INFINITE_TIME);
        settingSlots.put(5, ArenaSetting.END_IF_NO_PLAYERS);
        
        
        return settingSlots;
    }

    @Override
    public void updateSettingsInventory() {
        
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        // Nothing...
    }

}
