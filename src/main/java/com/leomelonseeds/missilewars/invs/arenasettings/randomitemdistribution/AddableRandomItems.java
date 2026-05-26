package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.RandomItem;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.invs.pagination.ItemFilter;
import com.leomelonseeds.missilewars.invs.pagination.ItemSort;
import com.leomelonseeds.missilewars.invs.pagination.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CooldownUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class AddableRandomItems extends PaginatedInventory {
    
    private static final List<ItemFilter> FILTERS = List.of(
            
        new ItemFilter("missiles", "&fMissiles &c🚀", Material.CREEPER_SPAWN_EGG, item -> 
            item.getType().toString().endsWith("SPAWN_EGG")),
        
        new ItemFilter("utilities", "&fUtilities &9★", Material.SNOWBALL, item -> 
            !item.getType().toString().endsWith("SPAWN_EGG")),
        
        new ItemFilter("deck", "&fDeck Items &b✟", Material.BOOKSHELF, item -> {
            FileConfiguration items = ConfigUtils.getConfigFile("items.yml");
            String key = InventoryUtils.getUUIDFromItem(item);
            return items.getConfigurationSection(key.split("-")[0]).contains("index");
        }),
        
        new ItemFilter("non-deck", "&fNon-Deck Items &c🃟", Material.CHISELED_BOOKSHELF, item -> {
            FileConfiguration items = ConfigUtils.getConfigFile("items.yml");
            String key = InventoryUtils.getUUIDFromItem(item);
            return !items.getConfigurationSection(key.split("-")[0]).contains("index");
        })
    );
    
    private static final List<ItemSort> SORTS = List.of(
        new ItemSort("name-a-to-z", "&fName (A-Z)", Material.BOOK, (i1, i2) -> {
            String s1 = ConfigUtils.stripString(ConfigUtils.toPlain(i1.effectiveName()));
            String s2 = ConfigUtils.stripString(ConfigUtils.toPlain(i2.effectiveName()));
            return s1.compareTo(s2);
        }),
        
        new ItemSort("name-z-to-a", "&fName (Z-A)", Material.BOOK, (i1, i2) -> {
            String s1 = ConfigUtils.stripString(ConfigUtils.toPlain(i1.effectiveName()));
            String s2 = ConfigUtils.stripString(ConfigUtils.toPlain(i2.effectiveName()));
            return s2.compareTo(s1);
        }),
        
        new ItemSort("tnt-amount", "&fTNT Amount", Material.TNT, Comparator.comparingDouble(item -> {
            String key = InventoryUtils.getUUIDFromItem(item);
            if (key.equals("thunderbolt-2")) {
                return 13;
            }
            
            String[] keyArgs = key.split("-");
            Object amount = ConfigUtils.getItemValue(keyArgs[0], Integer.parseInt(keyArgs[1]), "tnt");
            return amount == null ? 0 : Double.valueOf(amount + "");
        }), true),
        
        new ItemSort("missile-speed", "&fMissile Speed", Material.FEATHER, Comparator.comparingDouble(item -> {
            String[] keyArgs = InventoryUtils.getUUIDFromItem(item).split("-");
            Object amount = ConfigUtils.getItemValue(keyArgs[0], Integer.parseInt(keyArgs[1]), "speed");
            return amount == null ? 0 : Double.valueOf(amount + "");
        }), true)
    );

    private final static NamespacedKey cooldownKey = new NamespacedKey(MissileWarsPlugin.getPlugin(), "addable-random-item-list");
    private final static UseCooldownComponent COOLDOWN_COMPONENT = CooldownUtils.generateCustomCooldownComponent(cooldownKey);
    private final static String sec = "arena-settings.random-item-distribution.addable-items-list";
    
    private RandomItemDistributor distributor;
    private List<String> selectedItems;
    private List<String> itemList;
    private FileConfiguration itemConfig;
    private RandomItemsList fromInv;

    public AddableRandomItems(Player player, RandomItemDistributor distributor, RandomItemsList fromInv) {
        super(player, 54, "Choose Item to Add");
        this.distributor = distributor;
        this.selectedItems = new ArrayList<>();
        this.fromInv = fromInv;
        this.itemConfig = ConfigUtils.getConfigFile("items.yml");
        this.itemList = itemConfig.getStringList("random-items");
        player.setCooldown(cooldownKey, CooldownUtils.MAX_COOLDOWN);
        this.async = true;
        
        FILTERS.forEach(f -> addFilter(f));
        SORTS.forEach(s -> addSort(s));
        enableSearch();
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (String idString : itemList) {
            if (distributor.contains(idString)) {
                continue;
            }
            
            items.add(getItemFromID(idString));
        }
        return items;
    }
    
    private ItemStack getItemFromID(String idString) {
        String[] args = idString.split("-");
        int level = Integer.parseInt(args[1]);
        ItemStack item = MissileWarsPlugin.getPlugin().getDeckManager().createRandomItem(args[0], level);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        boolean selected = selectedItems.contains(idString);
        for (String s : itemConfig.getStringList("text.addable-item")) {
            lore.add(ConfigUtils.toComponent(s.replace("%select%", selected ? "deselect" : "select")));
        }
        meta.lore(lore);
        InventoryUtils.setMetaString(meta, InventoryUtils.UUID_KEY, idString); // Use UUID key for this one
        if (selected) {
            InventoryUtils.addGlow(meta);
            meta.setUseCooldown(COOLDOWN_COMPONENT);
        } else {
            InventoryUtils.removeGlow(meta);
            meta.setUseCooldown(null);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        if (selectedItems.isEmpty()) {
            return;
        }
        
        for (String key : itemConfig.getConfigurationSection(sec).getKeys(false)) {
            ItemStack item = InventoryUtils.createItem(sec + "." + key);
            InventoryUtils.setMetaString(item, InventoryUtils.ITEM_GUI_KEY, key);
            inv.setItem(itemConfig.getInt(sec + "." + key + ".slot"), item);
        }
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, fromInv);
            return;
        }
        
        // Check for GUI
        String gui = InventoryUtils.getGUIFromItem(item);
        if (gui != null) {
            if (gui.equals("add-all")) {
                Set<RandomItem> toAdd = new HashSet<>();
                for (String id : selectedItems) {
                    RandomItem ri = new RandomItem(id);
                    distributor.addItem(ri);
                    toAdd.add(ri);
                }
                
                fromInv.setSelectedItems(toAdd);
                manager.registerInventory(player, fromInv);
                ConfigUtils.sendConfigSound("bulk-edit", player);
                return;
            }
            
            if (gui.equals("deselect-all")) {
                selectedItems.clear();
                updateInventoryAsync();
                ConfigUtils.sendConfigSound("bulk-edit", player);
                return;
            }
            
            return;
        }
        
        // Check for addable
        String id = InventoryUtils.getUUIDFromItem(item);
        if (id == null) {
            return;
        }
        
        if (type.isShiftClick()) {
            RandomItem ri = new RandomItem(id);
            distributor.addItem(ri);
            updateInventoryAsync();
            ConfigUtils.sendConfigSound("purchase-item", player);
            return;
        }
        
        if (type.isLeftClick()) {
            RandomItem ri = new RandomItem(id);
            new RandomItemEditor(player, ri, fromInv, distributor, this);
            return;
        }
        
        if (type.isRightClick()) {
            if (selectedItems.contains(id)) {
                selectedItems.remove(id);
            } else {
                selectedItems.add(id); 
            }
            
            inv.setItem(slot, getItemFromID(id));
            updateNonPaginatedSlots();
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return;
        }
        
    }
    
    @Override
    protected void onFilterOrSearch() {
        selectedItems.clear();
    }

}
