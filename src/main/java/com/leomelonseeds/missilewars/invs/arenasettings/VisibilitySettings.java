package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;

public class VisibilitySettings extends ArenaSettingsInventory {

    public VisibilitySettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 18, "Visibility Settings", viewOnly, arena, fromInv);
        // Nothing here yet...
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            0, ArenaSetting.CAPACITY,
            1, ArenaSetting.ENABLE_POISON,
            2, ArenaSetting.WORLD_DIFFICULTY
        );
    }

    @Override
    public void updateSettingsInventory() {
        // Nothing here...
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        // Also nothing yet...
    }

}
