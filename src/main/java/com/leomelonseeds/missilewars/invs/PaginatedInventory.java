package com.leomelonseeds.missilewars.invs;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public abstract class PaginatedInventory extends MWInventory {
    
    private static ItemStack lastPageItem;
    private static ItemStack nextPageItem;
    
    private int page;
    private int lastPageSlot; // Also happens to be the size of the paginated part of the inventory
    private int nextPageSlot;

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
        
        // Initialize next and last page items if not
        if (lastPageItem == null) {
            lastPageItem = new ItemStack(Material.ARROW);
            ItemMeta lastPageMeta = lastPageItem.getItemMeta();
            lastPageMeta.displayName(ConfigUtils.toComponent("&fLast Page"));
            InventoryUtils.setMetaString(lastPageMeta, InventoryUtils.ITEM_GUI_KEY, "last-page");
            lastPageItem.setItemMeta(lastPageMeta);

            nextPageItem = new ItemStack(Material.ARROW);
            ItemMeta nextPageMeta = nextPageItem.getItemMeta();
            nextPageMeta.displayName(ConfigUtils.toComponent("&fNext Page"));
            InventoryUtils.setMetaString(nextPageMeta, InventoryUtils.ITEM_GUI_KEY, "next-page");
            nextPageItem.setItemMeta(nextPageMeta);
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
        inv.clear();
        List<ItemStack> paginatedItems = getPaginatedItems();
        int keySize = paginatedItems.size();
        double maxPages = Math.ceil((double) keySize / lastPageSlot);

        updateNonPaginatedSlots();
        
        // Epic pagination
        if (page > 0) {
            inv.setItem(lastPageSlot, lastPageItem);
        }
        
        if (page < maxPages - 1) {
            inv.setItem(nextPageSlot, nextPageItem);
        }
        
        // Finally set all the paginated items
        for (int i = page * lastPageSlot; i < Math.min(keySize, page * lastPageSlot + lastPageSlot); i++) {
            inv.setItem(i % lastPageSlot, paginatedItems.get(i));
        }
    }
    
    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        
        String guiItem = InventoryUtils.getGUIFromItem(item);
        if ("next-page".equals(guiItem)) {
            page++;
            updateInventory();
        } else if ("last-page".equals(guiItem)) {
            page--;
            updateInventory();
        } else {
            registerPaginatedClick(slot, type, item);
        }
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
}
