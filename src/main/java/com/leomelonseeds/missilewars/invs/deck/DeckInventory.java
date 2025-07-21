package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.JSONManager;

import net.kyori.adventure.text.Component;

public class DeckInventory extends MWInventory {
    
    private enum SubInventory {
        MAIN,
        PRESETS,
        MISSILES,
        UTILITY,
        ENCHANTS,
        ABILITIES,
        PASSIVES,
        GPASSIVES
    }

    private String deck;
    private FileConfiguration itemConfig;
    private JSONManager jsonManager;
    private JSONObject playerJson;
    private JSONObject deckJson;
    private String preset;
    private JSONObject presetJson;
    private BiMap<Integer, String> indicators;
    private DeckSubInventory curSubInventory;
    private SubInventory curSubInventoryType;
    
    public DeckInventory(Player player, String deck) {
        super(player, 54, deck);
        this.deck = deck;
        this.indicators = new BiMap<>();
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        jsonManager = MissileWarsPlugin.getPlugin().getJSON();
        playerJson = jsonManager.getPlayer(player.getUniqueId());
        deckJson = playerJson.getJSONObject(deck);
        updatePreset();
    }
    
    @Override
    public void updateInventory() {
        // Add stained glass in a rectangle
        for (int i = 0; i <= 44; i++) {
            if (isMiddleSlot(i)) {
                continue;
            }
            
            Material color = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            switch (deck) {
            case "Architect":
                color = Material.GREEN_STAINED_GLASS_PANE;
                break;
            case "Berserker":
                color = Material.PINK_STAINED_GLASS_PANE;
                break;
            case "Vanguard":
                color = Material.ORANGE_STAINED_GLASS_PANE;
            }
            ItemStack item = new ItemStack(color);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(ConfigUtils.toComponent(""));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        
        // Add indicators
        for (String key : itemConfig.getConfigurationSection("indicators").getKeys(false)) {
            String slotPath = "indicators." + key + ".slot";
            if (!itemConfig.contains(slotPath)) {
                continue;
            }
            
            ItemStack item = InventoryUtils.createItem("indicators." + key);
            int slot = itemConfig.getInt(slotPath);
            indicators.put(slot, key);
            inv.setItem(slot, item);
        }
        
        updateInfoItem();
        
        // Open main menu if not set
        if (curSubInventoryType == null) {
            openSubInventory(SubInventory.MAIN);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        // Ensure we got an item
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) {
            return;
        }
        
        if (slot == indicators.getKey("back")) {
            if (curSubInventoryType != SubInventory.MAIN) {
                openSubInventory(SubInventory.MAIN);
            } else {
                String command = "bossshop open decks " + player.getName();
                Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            }
            
            return;
        }
        
        // Check if we can open a sub inventory
        String indicator = indicators.get(slot);
        if (indicator != null) {
            SubInventory toOpen;
            try {
                toOpen = SubInventory.valueOf(indicator.toUpperCase());
            } catch (IllegalArgumentException e) {
                return;
            }
            
            openSubInventory(toOpen, slot);
            return;
        }
        
        // If not, see if the currently open sub inventory can 
        if (curSubInventory.registerClick(item, slot, type, player)) {
            curSubInventory.fillItems();
            updateTitle();
        }
    }
    
    /**
     * Opens an un-tabbed sub inventory
     * 
     * @param type
     */
    private void openSubInventory(SubInventory type) {
        openSubInventory(type, -1);
    }
    
    /**
     * Opens the sub inventory, updating title, adding to cache if needed.
     * Sub inventory is only opened if it is not the current sub inventory
     * It then clears the current items in the middle and fills
     * 
     * @param type
     * @param slot the slot of the indicator, to change its color
     */
    private void openSubInventory(SubInventory type, int slot) {
        if (type.equals(curSubInventoryType)) {
            return;
        }

        updatePreset();
        curSubInventoryType = type;
        switch (type) {
            case MISSILES:
            case UTILITY:
            case ENCHANTS:
                String typeString = type.toString().toLowerCase();
                curSubInventory = new ItemUpgrades(this, deck, itemConfig, playerJson, typeString, preset, presetJson);
                break;
            case ABILITIES:
                break;
            case GPASSIVES:
                break;
            case PASSIVES:
                break;
            case PRESETS:
                curSubInventory = new PresetSelector(this, deck, itemConfig, playerJson, deckJson);
                break;
            case MAIN:
                curSubInventory = new MainMenu(this, deck, itemConfig, playerJson, preset, presetJson, () -> openSubInventory(SubInventory.PRESETS));
                break;
            default:
                // This cannot possibly happen
                break;
        }
        
        updateTitle();
        clearMiddleSlots();
        if (slot > 0) {
            InventoryUtils.addGlow(inv.getItem(slot));
            inv.setItem(slot - 9, InventoryUtils.createBlankItem(Material.LIME_STAINED_GLASS_PANE));
        }
        curSubInventory.fillItems();
    }
    
    /**
     * Updates the title according to currently selected deck, preset, and sub inventory
     */
    private void updateTitle() {
        setTitle(deck + " [" + updatePreset() + "] - " + curSubInventory.getSubTitle());
    }
    
    /**
     * Clear all the middle slots for swithcing between tabs
     * Also clear glow and stuff of bottom indicators
     */
    private void clearMiddleSlots() {
        for (int i = 10; i <= 34; i++) {
            if (isMiddleSlot(i)) {
                inv.setItem(i, null);
            }
        }
        
        for (int i = 37; i <= 43; i++) {
            Material defaultMat = inv.getItem(40).getType();
            inv.setItem(i, new ItemStack(defaultMat));
            inv.getItem(i + 9).removeEnchantment(Enchantment.UNBREAKING);
        }
    }
    
    /**
     * Checks if the slot is in the middle customizable area
     * 
     * @param i
     * @return
     */
    private boolean isMiddleSlot(int i) {
        return i > 9 && i < 35 && i % 9 % 8 != 0;
    }
    
    /**
     * Extremely scuffed way of updating the info item with the
     * appropriate skillpoint and moken balance
     */
    private void updateInfoItem() {
        ItemStack item = inv.getItem(indicators.getKey("info"));
        ItemMeta meta = item.getItemMeta();
        List<String> newLore = new ArrayList<>();
        for (Component c : item.lore()) {
            String info = ConfigUtils.toPlain(c);
            if (info.contains("Balance")) {
                double bal = MissileWarsPlugin.getPlugin().getEconomy().getBalance(player);
                info = info.replace("null", bal + "");
            } else if (info.contains("Available Skillpoints")) {
                info = info.replace("null", presetJson.getInt("skillpoints") + "");
            }
            newLore.add(info);
        }
        meta.lore(ConfigUtils.toComponent(newLore));
        item.setItemMeta(meta);
    }
    
    /**
     * Updates the preset with the last selected one.
     * presetJson is updated after this one
     * 
     * @return the preset string
     */
    private String updatePreset() {
        String newPreset = deckJson.getString("last-preset");
        if (!newPreset.equals(preset)) {
            preset = newPreset;
            presetJson = deckJson.getJSONObject(newPreset);
        }
        
        return newPreset;
    }
}
