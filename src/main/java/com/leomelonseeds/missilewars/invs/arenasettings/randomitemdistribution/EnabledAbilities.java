package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class EnabledAbilities extends MWInventory {
    
    private static final String sec = "arena-settings.random-item-distribution.edit-abilities";
    
    private FileConfiguration itemsConfig;
    private RandomItemDistributor distributor;
    private boolean viewOnly;
    private MWInventory fromInv;
    private DeckManager deckManager;

    public EnabledAbilities(Player player, RandomItemDistributor distributor, boolean viewOnly, MWInventory fromInv) {
        super(player, 45, "Edit Abilities and Passives" + (viewOnly ? " (View Only)" : ""));
        this.distributor = distributor;
        this.viewOnly = viewOnly;
        this.fromInv = fromInv;
        this.itemsConfig = ConfigUtils.getConfigFile("items.yml");
        this.deckManager = MissileWarsPlugin.getPlugin().getDeckManager();
    }

    @Override
    public void updateInventory() {
        Map<String, Integer> deckSlots = new HashMap<>();
        ConfigurationSection itemsSection = itemsConfig.getConfigurationSection(sec);
        for (String key : itemsSection.getKeys(false)) {
            ItemStack item = InventoryUtils.createItem(sec + "." + key);
            item.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            int slot = itemsSection.getInt(key + ".slot");
            inv.setItem(slot, item);
            deckSlots.put(key, slot + 1);
        }
        
        for (String abilityPath : itemsConfig.getStringList("random-item-abilities")) {
            String deck = abilityPath.split("\\.")[0];
            ItemStack item = getAbilityItem(abilityPath);
            int slot = deckSlots.get(deck);
            inv.setItem(slot, item);
            deckSlots.put(deck, slot + 1);
        }
        
        fillBottomRow();
    }
    
    private ItemStack getAbilityItem(String abilityPath) {
        String ability = getAbilityString(abilityPath);
        int curLevel = distributor.getAbilityLevel(ability);
        ItemStack item = deckManager.createItem(abilityPath, curLevel, null, null, true, null, true);
        InventoryUtils.setMetaString(item, InventoryUtils.ITEM_GUI_KEY, abilityPath + "," + curLevel);
        return item;
    }
    
    private String getAbilityString(String abilityPath) {
        String[] args = abilityPath.split("\\.");
        return args[args.length - 1];
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
        
        String key = InventoryUtils.getGUIFromItem(item);
        if (key == null) {
            return;
        }
        
        if (viewOnly) {
            ConfigUtils.sendConfigMessage("settings.view-only", player);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        String[] args = key.split(",");
        String abilityPath = args[0];
        int level = Integer.parseInt(args[1]);
        if (type == ClickType.LEFT) {
            if (level >= deckManager.getMaxLevel(abilityPath)) {
                return;
            }
            
            distributor.setAbilityLevel(getAbilityString(abilityPath), level + 1);
            inv.setItem(slot, getAbilityItem(abilityPath));
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return;
        }
        
        if (type == ClickType.RIGHT) {
            if (level <= 0) {
                return;
            }
            
            distributor.setAbilityLevel(getAbilityString(abilityPath), level - 1);
            inv.setItem(slot, getAbilityItem(abilityPath));
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return;
        }
    }

}
