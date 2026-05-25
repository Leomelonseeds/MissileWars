package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.settings.IntSettingModifier;
import com.leomelonseeds.missilewars.arenas.settings.RandomItem;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemSetting;
import com.leomelonseeds.missilewars.invs.ConfirmAction;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.pagination.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class RandomItemsList extends PaginatedInventory {
    
    private final static String secString = "arena-settings.random-item-distribution.item-list";
    
    private RandomItemDistributor distributor;
    private Set<RandomItem> selectedItems;
    private boolean viewOnly;
    private MWInventory fromInv;
    private List<String> addStr;
    private ConfigurationSection itemsSection;

    public RandomItemsList(Player player, RandomItemDistributor distributor, boolean viewOnly, MWInventory fromInv) {
        super(player, 36, "Edit Random Items" + (viewOnly ? " (View Only)" : ""));
        this.selectedItems = new HashSet<>();
        this.distributor = distributor;
        this.viewOnly = viewOnly;
        this.fromInv = fromInv;
        FileConfiguration itemConfig = ConfigUtils.getConfigFile("items.yml");
        this.itemsSection = itemConfig.getConfigurationSection(secString);
        this.addStr = new ArrayList<>();
        this.addStr.add(itemConfig.getString("text.itemstats-random-weight"));
        this.addStr.addAll(itemConfig.getStringList("text.itemstats-random-menu"));
        this.async = true;
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (RandomItem ri : distributor.getRandomItems()) {
            items.add(getItemFromRandomItem(ri));
        }
        
        Collections.sort(items, (i1, i2) -> {
           boolean isMissile1 = i1.getType().toString().endsWith("SPAWN_EGG");
           boolean isMissile2 = i2.getType().toString().endsWith("SPAWN_EGG");
           if (isMissile1 == isMissile2) {
               return 0;
           }
           
           return isMissile1 ? -1 : 1;
        });
        
        return items;
    }
    
    private ItemStack getItemFromRandomItem(RandomItem ri) {
        ItemStack item = ri.getItem();
        ItemMeta meta = item.getItemMeta();
        boolean selected = selectedItems.contains(ri);
        List<Component> lore = meta.lore();
        for (String s : addStr) {
            lore.add(ConfigUtils.toComponent(
                s.replace("%weight%", ri.getWeight() + "")
                 .replace("%select%", selected ? "deselect" : "select")
            ));
        }
        meta.lore(lore);
        if (selected) {
            InventoryUtils.addGlow(meta);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        List<String> keys = new ArrayList<>();
        keys.add("select-all");
        
        if (!selectedItems.isEmpty()) {
            keys.add("edit-all-weights");
            keys.add("edit-all-maxes");
            keys.add("edit-all-amounts");
            keys.add("remove-all");
            keys.add("deselect-all");
        } else {
            keys.add("add-item");
            keys.add("random-selection");
        }
        
        for (int i : new int[] {28, 29, 30, 32, 33, 34}) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        for (String key : keys) {
            ItemStack item = InventoryUtils.createItem(secString + "." + key);
            InventoryUtils.setMetaString(item, InventoryUtils.ITEM_GUI_KEY, key);
            inv.setItem(itemsSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, fromInv);
            return;
        }
        
        if (viewOnly) {
            ConfigUtils.sendConfigMessage("settings.view-only", player);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        // Add or create items
        if (slot >= 27) {
            String key = InventoryUtils.getGUIFromItem(item);
            if (key == null) {
                return;
            }
            
            if (key.equals("add-item")) {
                new AddableRandomItems(player, distributor, this);
                return;
            }
            
            if (key.equals("random-selection")) {
                // TODO
                return;
            }
            
            if (key.equals("select-all")) {
                selectedItems.addAll(distributor.getRandomItems());
                updateInventoryAsync();
                ConfigUtils.sendConfigSound("use-skillpoint", player);
                return;
            }
            
            if (key.equals("deselect-all")) {
                selectedItems.clear();
                updateInventoryAsync();
                ConfigUtils.sendConfigSound("use-skillpoint", player);
                return;
            }
            
            if (key.equals("edit-all-weights")) {
                bulkEdit(type, RandomItemSetting.WEIGHT, ri -> ri.getWeight(), (ri, n) -> ri.setWeight(n));
                return;
            }
            
            if (key.equals("edit-all-maxes")) {
                bulkEdit(type, RandomItemSetting.MAX, ri -> ri.getMax(), (ri, n) -> ri.setMax(n));
                return;
            }
            
            if (key.equals("edit-all-amounts")) {
                bulkEdit(type, RandomItemSetting.AMOUNT, ri -> ri.getAmount(), (ri, n) -> ri.setAmount(n));
                return;
            }
            
            if (key.equals("remove-all")) {
                new ConfirmAction("Remove Selected Items", player, this, res -> {
                   if (res) {
                       for (RandomItem ri : selectedItems) {
                           distributor.removeItem(ri);
                       }
                       
                       selectedItems.clear();
                       updateInventoryAsync();
                   }
                });
                
                return;
            }
            
            return;
        }
        
        String uuidString = InventoryUtils.getUUIDFromItem(item);
        if (uuidString == null) {
            return;
        }
        
        UUID uuid = UUID.fromString(uuidString);
        RandomItem ri = distributor.getRandomItem(uuid);
        if (ri == null) {
            // Perhaps the distribution has changed?
            updateInventoryAsync();
            return;
        }
        
        if (type.isShiftClick()) {
            distributor.removeItem(ri);
            updateInventoryAsync();
            return;
        }
        
        if (type.isLeftClick()) {
            new RandomItemEditor(player, ri, this);
            return;
        }
        
        if (type.isRightClick()) {
            if (selectedItems.contains(ri)) {
                selectedItems.remove(ri);
            } else {
                selectedItems.add(ri);
            }

            inv.setItem(slot, getItemFromRandomItem(ri));
            updateNonPaginatedSlots();
            return;
        }
    }
    
    private void bulkEdit(ClickType type, RandomItemSetting setting, Function<RandomItem, Integer> get, BiConsumer<RandomItem, Integer> set) {
        IntSettingModifier modifier = setting.getModifier();
        if (type.isShiftClick()) {
            ArenaUtils.manualIntSetting(setting, modifier, player, this, res -> {
                selectedItems.forEach(ri -> set.accept(ri, res));
                updateInventoryAsync();
            });
            return;
        }
        
        if (type.isLeftClick()) {
            selectedItems.forEach(ri -> set.accept(ri, Math.min(modifier.getMax(), get.apply(ri) + 1)));
        } else if (type.isRightClick()) {
            selectedItems.forEach(ri -> set.accept(ri, Math.max(modifier.getMin(), get.apply(ri) - 1)));
        } else {
            return;
        }
        
        updateInventoryAsync();
        ConfigUtils.sendConfigSound("bulk-edit", player);
    }
}
