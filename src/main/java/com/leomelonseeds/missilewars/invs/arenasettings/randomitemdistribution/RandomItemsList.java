package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.settings.IntSettingModifier;
import com.leomelonseeds.missilewars.arenas.settings.RandomItem;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemSetting;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.pagination.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class RandomItemsList extends PaginatedInventory {
    
    private final static String secString = "arena-settings.random-item-distribution.item-list";
    
    private RandomItemDistributor distributor;
    private boolean viewOnly;
    private MWInventory fromInv;
    private List<String> addStr;
    private ConfigurationSection itemsSection;

    public RandomItemsList(Player player, RandomItemDistributor distributor, boolean viewOnly, MWInventory fromInv) {
        super(player, 36, "Edit Random Items" + (viewOnly ? " (View Only)" : ""));
        this.distributor = distributor;
        this.viewOnly = viewOnly;
        this.fromInv = fromInv;
        FileConfiguration itemConfig = ConfigUtils.getConfigFile("items.yml");
        this.itemsSection = itemConfig.getConfigurationSection(secString);
        this.addStr = new ArrayList<>();
        this.addStr.add(itemConfig.getString("text.itemstats-random-weight"));
        this.addStr.addAll(itemConfig.getStringList("text.itemstats-random-menu"));
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (RandomItem ri : distributor.getRandomItems()) {
            ItemStack item = ri.getItem();
            ItemMeta meta = item.getItemMeta();
            List<Component> lore = meta.lore();
            for (String s : addStr) {
                lore.add(ConfigUtils.toComponent(s.replace("%weight%", ri.getWeight() + "")));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            items.add(item);
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

    @SuppressWarnings("deprecation")
    @Override
    protected void updateNonPaginatedSlots() {
        for (String key : itemsSection.getKeys(false)) {
            ItemStack item = InventoryUtils.createItem(secString + "." + key);
            item.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
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
            
            if (key.equals("edit-all-weights")) {
                int amount = type.isShiftClick() ? 10 : 1;
                bulkEdit(amount, type, RandomItemSetting.WEIGHT, ri -> ri.getWeight(), (ri, n) -> ri.setWeight(n));
                return;
            }
            
            if (key.equals("edit-all-maxes")) {
                bulkEdit(1, type, RandomItemSetting.MAX, ri -> ri.getMax(), (ri, n) -> ri.setMax(n));
                return;
            }
            
            if (key.equals("edit-all-amounts")) {
                bulkEdit(1, type, RandomItemSetting.AMOUNT, ri -> ri.getAmount(), (ri, n) -> ri.setAmount(n));
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
            updateInventory();
            return;
        }
        
        if (type.isShiftClick()) {
            distributor.removeItem(ri);
            updateInventory();
        } else {
            new RandomItemEditor(player, ri, this);
        }
    }
    
    private void bulkEdit(int amount, ClickType type, RandomItemSetting setting, Function<RandomItem, Integer> get, BiConsumer<RandomItem, Integer> set) {
        IntSettingModifier modifier = setting.getModifier();
        if (type.isLeftClick()) {
            for (RandomItem ri : distributor.getRandomItems()) {
                set.accept(ri, Math.min(modifier.getMax(), get.apply(ri) + amount));
            }
        } else if (type.isRightClick()) {
            for (RandomItem ri : distributor.getRandomItems()) {
                set.accept(ri, Math.max(modifier.getMin(), get.apply(ri) - amount));
            }
        } else {
            return;
        }
        
        updateInventory();
        ConfigUtils.sendConfigSound("bulk-edit", player);
    }
}
