package com.leomelonseeds.missilewars.invs.pagination;

import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class SortSelectorInventory extends MWInventory {
    
    private Map<String, ItemSort> sorts;
    private String currentSort;
    private PaginatedInventory fromInv;
    private ConfigurationSection itemSection;

    public SortSelectorInventory(Player player, Map<String, ItemSort> sorts, String currentSort, PaginatedInventory fromInv) {
        super(player, ((sorts.size() + 8) / 9) * 9, "Select Sorting Type");
        this.sorts = sorts;
        this.currentSort = currentSort;
        this.fromInv = fromInv;
        this.itemSection = ConfigUtils.getConfigFile("messages.yml")
            .getConfigurationSection("inventories.pagination.sort-item");
    }

    @Override
    public void updateInventory() {
        fillBottomRow();
        
        int i = 0;
        for (ItemSort sort : sorts.values()) {
            boolean enabled = currentSort.equals(sort.getId());
            ItemStack item = new ItemStack(sort.getGuiMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(ConfigUtils.toComponent(itemSection.getString("name")
                .replace("%sort%", sort.getDisplayName())));
            meta.lore(ConfigUtils.toComponent(itemSection.getStringList(enabled ? "lore-applied" : "lore")));
            if (enabled) {
                InventoryUtils.addGlow(meta);
            }
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, sort.getId());
            item.setItemMeta(meta);
            inv.setItem(i, item);
            i++;
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
        
        String sortKey = InventoryUtils.getGUIFromItem(item);
        if (sortKey == null) {
            return;
        }
        
        fromInv.setSort(sortKey);
        fromInv.resetPage();
        manager.registerInventory(player, fromInv);
    }

}
