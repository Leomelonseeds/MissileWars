package com.leomelonseeds.missilewars.invs.deck;

import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.invs.ConfirmAction;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public abstract class DeckSubInventory {
    
    protected MWInventory mwinv;
    protected Inventory inv;
    protected String deck;
    protected FileConfiguration itemConfig;
    protected JSONObject playerJson;
    
    public DeckSubInventory(MWInventory mwinv, String deck, FileConfiguration itemConfig, JSONObject playerJson) {
        this.mwinv = mwinv;
        this.inv = mwinv.getInventory();
        this.deck = deck;
        this.itemConfig = itemConfig;
        this.playerJson = playerJson;
    }
    
    public abstract void fillItems();
    
    /**
     * Returns true if the menu changed as a result of this click
     * 
     * @param item
     * @param slot
     * @param type
     * @param player
     * @return
     */
    public abstract boolean registerClick(ItemStack item, int slot, ClickType type, Player player);
    
    public abstract String getSubTitle();
    
    /**
     * For sub inventories that list items in a row, we
     * center the items in the GUI. Finds the first slot
     * that the first item should be placed at
     * 
     * Examples:
     * 1 item = 22
     * 2 items = 21
     * 3 items = 21
     * 4 items = 20
     * 
     * @return
     */
    protected int getFirstSlot(int itemAmount) {
        return 22 - (itemAmount / 2);
    }
    
    // I could put the below methods in their own abstract class but naaahhhh
    
    /**
     * Replace %ability%, %passive%, and %gpassive% placeholders with the appropriate
     * names given the current deck and json
     * 
     * @param line
     * @param presetJson
     * @return the same line
     */
    protected String replaceAbilityNames(String line, JSONObject presetJson) {
        if (presetJson == null) {
            line = line
                .replace("%ability%", "None")
                .replace("%passive%", "None")
                .replace("%gpassive%", "None");
            return line;
        }
        
        for (String type : new String[] {"ability", "passive", "gpassive"}) {
            String placeholder = "%" + type + "%";
            String ability = presetJson.getJSONObject(type).getString("selected");
            if (ability.equals("None")) {
                line = line.replace(placeholder, "None");
            } else {
                String path = type.equals("gpassive") ? 
                    "gpassive." + ability + ".name" :
                    deck + "." + type + "." + ability + ".name"; 
                line = line.replace(placeholder, itemConfig.getString(path));
            }
        }
        
        return line;
    }
    
    /**
     * Make the player use the specified preset of this deck
     * 
     * @param preset
     * @param playerJson
     * @param player
     */
    protected void selectPreset(String preset, JSONObject playerJson, Player player) {
        playerJson.put("Deck", deck);
        playerJson.put("Preset", preset);
        playerJson.getJSONObject(deck).put("last-preset", preset);
        
        ConfigUtils.sendConfigMessage("change-preset", player, Map.of("%deck%", deck, "%preset%", preset));
        ConfigUtils.sendConfigSound("change-preset", player);
    }
    
    /**
     * Checks for the purchase of an item, and prompts player to purchase
     * if they have enough Mokens
     * 
     * @param item
     * @param name
     * @param level
     * @param realname
     * @param player
     * @return true if this item is purchasable
     */
    protected boolean checkPurchase(ItemStack item, String name, int level, String realname, Player player) {
        if (!item.getType().toString().equals(itemConfig.getString("intangibles.locked"))) {
            return false;
        }
        
        double bal = MissileWarsPlugin.getPlugin().getEconomy().getBalance(player);
        int cost = (int) ConfigUtils.getItemValue(name, level, "cost");
        if (bal < cost) {
            ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
            return true;
        }
        
        new ConfirmAction("Purchase '" + realname + "'", player, mwinv, (confirm) -> {
            if (!confirm) {
                return;
            }
            if (playerJson.has(realname)) {
                playerJson.put(realname, true);
            } else if (playerJson.getJSONObject(deck).has(realname)) {
                playerJson.getJSONObject(deck).put(realname, true);
            }
            MissileWarsPlugin.getPlugin().getEconomy().withdrawPlayer(player, cost);
            fillItems();
        });
        
        return true;
    }
}
