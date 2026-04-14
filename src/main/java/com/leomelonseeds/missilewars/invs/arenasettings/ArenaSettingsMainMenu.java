package com.leomelonseeds.missilewars.invs.arenasettings;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class ArenaSettingsMainMenu extends MWInventory {
    
    private boolean viewOnly;
    private Player player;
    private Arena arena;
    private FileConfiguration itemConfig;
    private MWInventory fromInv;

    public ArenaSettingsMainMenu(Player player, Arena arena, boolean viewOnly, MWInventory fromInv) {
        super(
            player,
            viewOnly ? 36 : 45,
            "Arena Settings" + (viewOnly ? " (View only)" : "")
        );
        
        this.player = player;
        this.arena = arena;
        this.viewOnly = viewOnly;
        this.itemConfig = ConfigUtils.getConfigFile("items.yml");
        this.fromInv = fromInv;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void updateInventory() {
        String mainSec = "arena-settings.main-menu";
        for (String key : itemConfig.getConfigurationSection(mainSec).getKeys(false)) {
            String sec = mainSec + "." + key;
            int slot = itemConfig.getInt(sec + ".slot");
            if (slot >= inv.getSize()) {
                continue;
            }
            
            ItemStack item = InventoryUtils.createItem(sec);
            ItemMeta meta = item.getItemMeta();
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, key);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }
        
        for (int i = 27; i <= 35; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        inv.setItem(viewOnly ? 31 : 40, InventoryUtils.getBackItem());
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        
        if (item.equals(InventoryUtils.getBackItem())) {
            if (fromInv != null) {
                manager.registerInventory(player, fromInv);
            } else {
                player.closeInventory();
            }
            
            return;
        }
    }
}
