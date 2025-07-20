package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
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
    private String preset;
    private FileConfiguration itemConfig;
    private JSONManager jsonManager;
    private JSONObject playerJson;
    private JSONObject deckJson;
    private JSONObject presetJson;
    private BiMap<Integer, String> indicators;
    private DeckSubInventory curSubInventory;
    private SubInventory curSubInventoryType;
    private HashMap<SubInventory, DeckSubInventory> subInventoryCache;
    
    public DeckInventory(Player player, String deck) {
        super(player, 54, deck);
        this.deck = deck;
        this.indicators = new BiMap<>();
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        jsonManager = MissileWarsPlugin.getPlugin().getJSON();
        playerJson = jsonManager.getPlayer(player.getUniqueId());
        deckJson = playerJson.getJSONObject(deck);
        preset = getPreset();
        if (deckJson.has(preset)) {
            presetJson = deckJson.getJSONObject(preset);
        }
        
        // Set main menu as the default sub inventory
        subInventoryCache = new HashMap<>();
        curSubInventory = new MainMenu(inv, deck, preset, playerJson, presetJson, itemConfig, o -> openSubInventory(SubInventory.PRESETS));
        curSubInventoryType = SubInventory.MAIN;
        subInventoryCache.put(curSubInventoryType, curSubInventory);
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
        curSubInventory.fillItems();
        updateTitle();
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
        
        curSubInventory.registerClick(item, slot, type);
    }
    
    /**
     * Opens the sub inventory, updating title, adding to cache if needed.
     * Sub inventory is only opened if it is not the current sub inventory
     * It then clears the current items in the middle and fills
     * 
     * @param type
     */
    private void openSubInventory(SubInventory type) {
        if (type.equals(curSubInventoryType)) {
            return;
        }

        curSubInventoryType = type;
        if (subInventoryCache.containsKey(type)) {
            curSubInventory = subInventoryCache.get(type);
        } else {
            // Set curSubInventory here
            switch (type) {
                case MISSILES:
                    break;
                case UTILITY:
                    break;
                case ENCHANTS:
                    break;
                case ABILITIES:
                    break;
                case GPASSIVES:
                    break;
                case PASSIVES:
                    break;
                case PRESETS:
                    break;
                default:
                    // This cannot possibly happen
                    break;
            }
            
            subInventoryCache.put(type, curSubInventory);
        }
        
        updateTitle();
        clearMiddleSlots();
        curSubInventory.fillItems();
    }
    
    /**
     * Updates the title according to currently selected deck, preset, and sub inventory
     */
    private void updateTitle() {
        setTitle(deck + " [" + preset + "] - " + curSubInventory.getSubTitle());
    }
    
    /**
     * Clear all the middle slots for swithcing between tabs
     */
    private void clearMiddleSlots() {
        for (int i = 10; i <= 34; i++) {
            if (isMiddleSlot(i)) {
                inv.setItem(i, null);
            }
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
    
    private String getPreset() {
        return deckJson.getString("last-preset");
    }
}
