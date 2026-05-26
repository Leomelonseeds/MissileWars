package com.leomelonseeds.missilewars.invs.pagination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.listener.handler.ChatPrompt;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public abstract class PaginatedInventory extends MWInventory {
    
    private static ItemStack[] pageItems;
    private static ItemSort defaultSort = new ItemSort("default-sort", "Featured", Material.NETHER_STAR, (i1, i2) -> 0);
    
    private ConfigurationSection itemsSection;
    
    // List of filters and sorting options
    private Map<String, ItemFilter> filters;
    private Map<String, ItemSort> sorts;
    private boolean enableSearch;
    
    // Currently applied filters and sorting options by ID
    private Map<String, Predicate<ItemStack>> currentFilters;
    private String currentSort;
    private String searchTerm;
    
    private int page;
    private int lastPageSlot; // Also happens to be the size of the paginated part of the inventory
    private int nextPageSlot;
    
    private List<ItemStack> items;

    /**
     * A paginated inventory. The first and last slots of the
     * last row will be used for next and lage page buttons.
     * The paginated items will take up every row except the
     * last.
     * 
     * @param player
     * @param size the size of the entire inventory
     * @param title
     */
    public PaginatedInventory(Player player, int size, String title) {
        super(player, size, title);
        
        this.itemsSection = ConfigUtils.getConfigFile("messages.yml").getConfigurationSection("inventories.pagination");
        this.filters = new LinkedHashMap<>();
        this.sorts = new LinkedHashMap<>();
        this.currentFilters = new LinkedHashMap<>();
        
        // Initialize next and last page items if not
        if (pageItems == null) {
            pageItems = new ItemStack[2];
            List<String> pageItemList = List.of("last-page", "next-page");
            for (int i = 0; i < 2; i++) {
                String s = pageItemList.get(i);
                ItemStack pageItem = new ItemStack(Material.valueOf(itemsSection.getString(s + ".item")));
                ItemMeta pageMeta = pageItem.getItemMeta();
                pageMeta.displayName(ConfigUtils.toComponent(itemsSection.getString(s + ".name")));
                InventoryUtils.setMetaString(pageMeta, InventoryUtils.ITEM_GUI_KEY, s);
                pageItem.setItemMeta(pageMeta);
                pageItems[i] = pageItem;
            }
        }
        
        this.page = 0;
        this.nextPageSlot = size - 1;
        this.lastPageSlot = size - 9;
    }
    
    /**
     * Updates the paginated slots with the provided items. The slots
     * are filled from the items depending on the current page of the
     * inventory. "Next page" and "Last page" items are also updated
     * here.
     * 
     * Also calls an update to the non paginated slots (provided by
     * the subclass)
     */
    @Override
    public void updateInventory() {
        this.items = getPaginatedItems();
        
        // Check for filtering and sorting
        // If search term is not empty, add it as a filter then remove it later
        if (searchTerm != null) {
            currentFilters.put("search-term", item -> {
               String name = ConfigUtils.stripString(ConfigUtils.toPlain(item.effectiveName()));
               String term = ConfigUtils.removeNonAlpha(searchTerm);
               return name.contains(term.toLowerCase());
            });
        }
        
        // Filter
        if (!currentFilters.isEmpty()) {
            Iterator<ItemStack> filterIterator = items.iterator();
            while (filterIterator.hasNext()) {
                ItemStack item = filterIterator.next();
                for (Predicate<ItemStack> predicate : currentFilters.values()) {
                    if (!predicate.test(item)) {
                        filterIterator.remove();
                        break;
                    }
                }
            }
        }
        currentFilters.remove("search-term");
        
        // Sort
        if (currentSort != null) {
            ItemSort sort = sorts.get(currentSort);
            if (sort.isReverse()) {
                Collections.sort(items, sort.getComparator().reversed());
            } else {
                Collections.sort(items, sort.getComparator());
            }
        }
        
        // Add black and red glass panes. Red glass pane behavior should be handled by the subclass
        fillBottomRow();

        // Non-paginated slots can override the current panes, but items after
        // will override the subclass' items
        updateNonPaginatedSlots();
        
        // Now that all sorting and stuff is done we can add in the paginated items
        updatePaginatedSlots();
        
        // Add filter item
        if (!filters.isEmpty()) {
            ConfigurationSection sec = itemsSection.getConfigurationSection("filter");
            ItemStack filterItem = new ItemStack(Material.valueOf(sec.getString("item")));
            ItemMeta filterMeta = filterItem.getItemMeta();
            filterMeta.displayName(ConfigUtils.toComponent(sec.getString("name")));
            List<String> lore = new ArrayList<>();
            if (currentFilters.isEmpty()) {
                lore.addAll(sec.getStringList("lore-none"));
                lore.addAll(sec.getStringList("lore-click"));
            } else {
                InventoryUtils.addGlow(filterMeta);
                for (String filter : currentFilters.keySet()) {
                    String displayName = filters.get(filter).getDisplayName();
                    lore.add(sec.getString("lore-filter").replace("%filter%", displayName));
                }
                lore.addAll(sec.getStringList("lore-click"));
                lore.add(sec.getString("lore-filtered"));
            }
            filterMeta.lore(ConfigUtils.toComponent(lore));
            InventoryUtils.setMetaString(filterMeta, InventoryUtils.ITEM_GUI_KEY, "filter");
            filterItem.setItemMeta(filterMeta);
            inv.setItem(lastPageSlot + 3, filterItem);
        }
        
        // Add sort item. If sort is not empty a default sort must be set.
        if (!sorts.isEmpty()) {
            ConfigurationSection sec = itemsSection.getConfigurationSection("sort");
            ItemStack sortItem = new ItemStack(Material.valueOf(sec.getString("item")));
            ItemMeta sortMeta = sortItem.getItemMeta();
            String name = sec.getString("name") + sorts.get(currentSort).getDisplayName();
            sortMeta.displayName(ConfigUtils.toComponent(name));
            sortMeta.lore(ConfigUtils.toComponent(sec.getStringList("lore-click")));
            if (!currentSort.equals("default-sort")) {
                InventoryUtils.addGlow(sortMeta);
            }
            InventoryUtils.setMetaString(sortMeta, InventoryUtils.ITEM_GUI_KEY, "sort");
            sortItem.setItemMeta(sortMeta);
            inv.setItem(lastPageSlot + 2, sortItem);
        }
        
        // Then add search item
        if (enableSearch) {
            ConfigurationSection sec = itemsSection.getConfigurationSection("search");
            ItemStack searchItem = new ItemStack(Material.valueOf(sec.getString("item")));
            ItemMeta searchMeta = searchItem.getItemMeta();
            String currentSearch;
            if (searchTerm == null) {
                currentSearch = sec.getString("search-none");
            } else {
                InventoryUtils.addGlow(searchMeta);
                currentSearch = sec.getString("search-term").replace("%search%", searchTerm);
            }
            searchMeta.displayName(ConfigUtils.toComponent(sec.getString("name") + currentSearch));
            List<Component> lore = ConfigUtils.toComponent(sec.getStringList("lore-click"));
            if (searchTerm != null) {
                lore.add(ConfigUtils.toComponent(sec.getString("lore-searched")));
            }
            searchMeta.lore(lore);
            InventoryUtils.setMetaString(searchMeta, InventoryUtils.ITEM_GUI_KEY, "search");
            searchItem.setItemMeta(searchMeta);
            inv.setItem(lastPageSlot + 5, searchItem);
        }
    }
    
    /**
     * Only update the paginated slots, use for page changes
     */
    private void updatePaginatedSlots() {
        int lastPage = (items.size() - 1) / lastPageSlot;
        inv.setItem(lastPageSlot, page == 0 ? InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE) : pageItems[0]);
        inv.setItem(nextPageSlot, page >= lastPage ? InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE) : pageItems[1]);
        for (int i = page * lastPageSlot; i < page * lastPageSlot + lastPageSlot; i++) {
            inv.setItem(i % lastPageSlot, i < items.size() ? items.get(i) : null);
        }
    }
    
    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        
        String guiItem = InventoryUtils.getGUIFromItem(item);
        if (guiItem == null) {
            registerPaginatedClick(slot, type, item);
            return;
        }
        
        if (guiItem.equals("next-page")) {
            page++;
            updatePaginatedSlots();
            return;
        }
        
        if (guiItem.equals("last-page")) {
            page--;
            updatePaginatedSlots();
            return;
        }
        
        if (guiItem.equals("filter")) {
            if (type.isShiftClick() && !currentFilters.isEmpty()) {
                currentFilters.clear();
                resetPage();
                updateInventoryAsync();
            } else {
                new FilterSelectorInventory(player, filters, currentFilters, this);
            }
            return;
        }
        
        if (guiItem.equals("sort")) {
            new SortSelectorInventory(player, sorts, currentSort, this);
            return;
        }
        
        if (guiItem.equals("search")) {
            if (type.isShiftClick() && searchTerm != null) {
                searchTerm = null;
                resetPage();
                updateInventoryAsync();
                return;
            }
            
            player.closeInventory();
            ConfigUtils.sendConfigMessage("search-query", player);
            new ChatPrompt(player, 60, res -> {
                manager.registerInventory(player, this, false);
                if (res == null || res.length() < 3) {
                    ConfigUtils.sendConfigMessage("search-query-failed", player);
                    return;
                }
                
                if (res.equalsIgnoreCase("cancel")) {
                    return;
                }

                this.searchTerm = res;
                resetPage();
                onFilterOrSearch();
                updateInventoryAsync();
            });
            return;
        }
        
        registerPaginatedClick(slot, type, item);
    }
    
    /**
     * @param filter
     */
    protected void addFilter(ItemFilter filter) {
        filters.put(filter.getId(), filter);
    }
    
    /**
     * If sorts is empty, a default "Featured" sort
     * will also be added that does nothing. Make sure variable
     * defaultSortSlot is set so that this will work
     * 
     * @param sort
     */
    protected void addSort(ItemSort sort) {
        if (sorts.isEmpty()) {
            sorts.put(defaultSort.getId(), defaultSort);
            currentSort = defaultSort.getId();
        }
        
        sorts.put(sort.getId(), sort);
    }
    
    /**
     * @param sort does not check if the sort exists, make sure it exists!!!
     */
    protected void setSort(String sort) {
        currentSort = sort;
    }
    
    /**
     * Allow searching a string to match an item
     */
    protected void enableSearch() {
        this.enableSearch = true;
    }
    
    /**
     * Sets page to 0
     */
    protected void resetPage() {
        page = 0;
    }
    
    /**
     * This function will run first in updateInventory(), in case
     * a class variable needs info from the paginated items
     * in order to fill a non-paginated slot.
     * 
     * @return a list of items to put in the paginated slots
     */
    protected abstract List<ItemStack> getPaginatedItems();
    
    /**
     * Update bottom row items (does not include arrows). This method
     * runs before adding paginated items. Any items added here that
     * are in the same slots as paginated items (such as arrows) will
     * be overridden.
     */
    protected abstract void updateNonPaginatedSlots();
    
    /**
     * Register a click for any non-arrow item. The item will
     * never be null.
     * 
     * @param slot
     * @param type
     * @param item
     */
    protected abstract void registerPaginatedClick(int slot, ClickType type, ItemStack item);
    
    /**
     * This function is called when the search or filter functions
     * are used successfully to change the list of items
     */
    protected void onFilterOrSearch() {};
}
