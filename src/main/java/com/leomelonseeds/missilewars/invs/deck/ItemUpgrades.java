package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.WordUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class ItemUpgrades extends DeckSubInventory {
    
    private String type;
    private String preset;
    private JSONObject presetJson;
    private JSONObject typeJson;
    private boolean isEnchants;
    private List<String> enchants; // Used to get the order of enchants, if they exist

    /**
     * ItemUpgrades for type "missiles", "utility", or "enchants"
     */
    public ItemUpgrades(MWInventory mwinv, String deck, FileConfiguration itemConfig, JSONObject playerJson, String type, String preset, JSONObject presetJson) {
        super(mwinv, deck, itemConfig, playerJson);
        this.type = type;
        this.preset = preset;
        this.presetJson = presetJson;
        this.typeJson = presetJson.getJSONObject(type);
        this.isEnchants = type.equals("enchants");
        if (isEnchants) {
            this.enchants = new ArrayList<>(itemConfig.getConfigurationSection(deck + ".enchants").getKeys(false));
        }
    }

    @Override
    public void fillItems() {
        Set<String> items = typeJson.keySet();
        int initSlot = getFirstSlot(items.size());
        for (String itemString : items) {
            ItemStack item = MissileWarsPlugin.getPlugin().getDeckManager().createItem(
                    isEnchants ? deck + ".enchants." + itemString : itemString,
                    typeJson.getInt(itemString), 
                    type.equals("missiles"), 
                    playerJson, deck, isEnchants, preset);
            int slotAdd;
            if (isEnchants) {
                slotAdd = enchants.indexOf(itemString);
            } else {
                slotAdd = itemConfig.getInt(itemString + ".index");
                if (type.equals("utility")) {
                    slotAdd -= 5;
                }
            }
            
            inv.setItem(initSlot + slotAdd, item);
        }
    }

    @Override
    public boolean registerClick(ItemStack item, int slot, ClickType type, Player player) {
        // Ensure it's clickable :))
        String storedName = InventoryUtils.getGUIFromItem(item);
        if (storedName == null) {
            return false;
        }
        
        String[] args = storedName.split("-");
        String name = args[0];
        int level = Integer.parseInt(args[1]);
        
        String args2[] = name.split("\\.");
        String realname = args2[args2.length - 1];
        
        // Check for purchases
        if (checkPurchase(item, name, level, realname, player)) {
            return false;
        }
        
        // Check if the clicked item is actually in this json
        if (!typeJson.has(realname)) {
            return false;
        }
        
        // Left click to upgrade...
        int sp = presetJson.getInt("skillpoints");
        if (type == ClickType.LEFT) {
            if (level >= MissileWarsPlugin.getPlugin().getDeckManager().getMaxLevel(name)) {
                return false;
            }
            
            int spcost = (int) ConfigUtils.getItemValue(name, level + 1, "spcost");
            if (sp < spcost) {
                ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
                return false;
            }
            
            typeJson.put(realname, typeJson.getInt(realname) + 1);
            presetJson.put("skillpoints", sp - spcost);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return true;
        }
        
        // Right click to downgrade.
        if (type == ClickType.RIGHT) {
            // Enchantments have min level 0
            if (level <= (name.contains(".") ? 0 : 1)) {
                return false;
            }
            
            int spgain = (int) ConfigUtils.getItemValue(name, level, "spcost");
            typeJson.put(realname, typeJson.getInt(realname) - 1);
            presetJson.put("skillpoints", sp + spgain);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return true;
        }
        
        return false;
    }

    @Override
    public String getSubTitle() {
        return WordUtils.capitalize(type) + " (" + presetJson.getInt("skillpoints") + "sp)";
    }

}
