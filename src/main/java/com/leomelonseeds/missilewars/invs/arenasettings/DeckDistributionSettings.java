package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class DeckDistributionSettings extends ArenaSettingsInventory {

    public DeckDistributionSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 27, "Deck Distribution Settings", viewOnly, arena, fromInv);
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            9, ArenaSetting.DECK_ITEM_MULTIPLIER
        );
    }

    @Override
    public void updateSettingsInventory() {
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        // Nothing
    }

}
