package com.leomelonseeds.missilewars.invs.pagination;

import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class FilterSelectorInventory extends MWInventory {
    
    private Map<String, ItemFilter> filters;
    private Map<String, Predicate<ItemStack>> currentFilters;
    private PaginatedInventory fromInv;
    private ConfigurationSection itemSection;

    public FilterSelectorInventory(Player player, Map<String, ItemFilter> filters, 
        Map<String, Predicate<ItemStack>> currentFilters, PaginatedInventory fromInv) {
        super(player, ((filters.size() + 17) / 9) * 9, "Select Filters");
        this.filters = filters;
        this.currentFilters = currentFilters;
        this.fromInv = fromInv;
        this.itemSection = ConfigUtils.getConfigFile("messages.yml")
            .getConfigurationSection("inventories.pagination.filter-item");
    }

    @Override
    public void updateInventory() {
        fillBottomRow();
        
        int i = 0;
        for (ItemFilter filter : filters.values()) {
            boolean enabled = currentFilters.containsKey(filter.getId());
            ItemStack item = new ItemStack(filter.getGuiMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(ConfigUtils.toComponent(itemSection.getString("name")
                .replace("%filter%", filter.getDisplayName())));
            meta.lore(ConfigUtils.toComponent(itemSection.getStringList(enabled ? "lore-applied" : "lore")));
            if (enabled) {
                InventoryUtils.addGlow(meta);
            } else {
                InventoryUtils.removeGlow(meta);
            }
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, filter.getId());
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
        
        String filterKey = InventoryUtils.getGUIFromItem(item);
        if (filterKey == null) {
            return;
        }
        
        handleMutuallyExclusiveFilters(filterKey, "missiles", "utilities");
        handleMutuallyExclusiveFilters(filterKey, "deck", "non-deck");
        
        // If enabled disable it
        if (currentFilters.containsKey(filterKey)) {
            currentFilters.remove(filterKey);
        } else {
            currentFilters.put(filterKey, filters.get(filterKey).getPredicate());
        }
        
        fromInv.resetPage();
        fromInv.onFilterOrSearch();
        updateInventory();
    }
    
    private void handleMutuallyExclusiveFilters(String key, String filter1, String filter2) {
        if (key.equals(filter1)) {
            currentFilters.remove(filter2);
        } else if (key.equals(filter2)) {
            currentFilters.remove(filter1);
        }
    }

}
