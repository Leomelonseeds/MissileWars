package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.RandomItem;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.pagination.ItemFilter;
import com.leomelonseeds.missilewars.invs.pagination.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class AddableRandomItems extends PaginatedInventory {
    
    private enum ItemFilters {
        
        MISSILES(new ItemFilter("missiles", "&fMissiles &c🚀", Material.CREEPER_SPAWN_EGG, item -> 
            item.getType().toString().endsWith("SPAWN_EGG")
        )),
        
        UTILITIES(new ItemFilter("utilities", "&fUtilities &9★", Material.SNOWBALL, item -> 
            item.getType().toString().endsWith("SPAWN_EGG")
        )),
        
        DECK_ITEMS(new ItemFilter("deck", "&fDeck Items &b✟", Material.SNOWBALL, item -> {
            // TODO
            return false;
        }));
        
        private ItemFilter filter;
        
        private ItemFilters(ItemFilter filter) {
            this.filter = filter;
        }
    }
    
    private RandomItemDistributor distributor;
    private List<String> itemList;
    private FileConfiguration itemConfig;
    private MWInventory fromInv;

    public AddableRandomItems(Player player, RandomItemDistributor distributor, MWInventory fromInv) {
        super(player, 54, "Choose Item to Add");
        this.distributor = distributor;
        this.fromInv = fromInv;
        this.itemConfig = ConfigUtils.getConfigFile("items.yml");
        this.itemList = itemConfig.getStringList("random-items");
        this.async = true;
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (String idString : itemList) {
            if (distributor.contains(idString)) {
                continue;
            }
            
            String[] args = idString.split("-");
            int level = Integer.parseInt(args[1]);
            ItemStack item = MissileWarsPlugin.getPlugin().getDeckManager().createRandomItem(args[0], level);
            ItemMeta meta = item.getItemMeta();
            List<Component> lore = meta.lore();
            lore.addAll(ConfigUtils.toComponent(itemConfig.getStringList("text.addable-item")));
            meta.lore(lore);
            InventoryUtils.setMetaString(meta, InventoryUtils.UUID_KEY, idString); // Use UUID key for this one
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    @Override
    protected void updateNonPaginatedSlots() {} // Nothing to put here tbh

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, fromInv);
            return;
        }
        
        // Filter and sort
        String guiKey = InventoryUtils.getGUIFromItem(item);
        if (guiKey != null) {
            //  TODO
            return;
        }
        
        // Check for addable
        String id = InventoryUtils.getUUIDFromItem(item);
        if (id == null) {
            return;
        }
        
        RandomItem ri = new RandomItem(id);
        new RandomItemEditor(player, ri, fromInv, distributor, this);
    }

}
