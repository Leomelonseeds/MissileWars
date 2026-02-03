package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class AbilityUpgrades extends DeckSubInventory {
    
    private String type;
    private String preset;
    private JSONObject presetJson;
    private JSONObject typeJson;
    private String path;
    private List<String> keys;

    /**
     * AbilityUpgrades for type "ability", "gpassive", or "passive"
     */
    public AbilityUpgrades(MWInventory mwinv, String deck, FileConfiguration itemConfig, JSONObject playerJson, String type, String preset, JSONObject presetJson) {
        super(mwinv, deck, itemConfig, playerJson);
        this.type = type;
        this.preset = preset;
        this.presetJson = presetJson;
        this.typeJson = presetJson.getJSONObject(type);
        
        // Load in ability keys
        this.path = type.equals("gpassive") ? "gpassive" : deck + "." + type;
        this.keys = new ArrayList<>(itemConfig.getConfigurationSection(path).getKeys(false));
    }

    @Override
    public void fillItems() {
        int initSlot = getFirstSlot(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int level = 0;
            if (typeJson.getString("selected").equals(key)) {
                level = typeJson.getInt("level");
            }
            ItemStack item = MissileWarsPlugin.getPlugin().getDeckManager().createItem(
                    path + "." + key, level, false, playerJson, deck, true, preset);
            inv.setItem(initSlot + i, item);
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
        
        // See if the type keys actually contains this item
        if (!keys.contains(realname)) {
            return false;
        }
        
        // Purchase this ability if player doesn't have one selected
        int sp = presetJson.getInt("skillpoints");
        if (typeJson.getString("selected").equals("None") && type == ClickType.LEFT) {
            int spcost = (int) ConfigUtils.getItemValue(name, 1, "spcost");
            if (sp < spcost) {
                ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
                return false;
            }
            
            typeJson.put("selected", realname);
            typeJson.put("level", 1);
            presetJson.put("skillpoints", sp - spcost);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return true;
        }
        
        // Otherwise make sure the one we clicked on is selected one
        if (!typeJson.getString("selected").equals(realname)) {
            ConfigUtils.sendConfigMessage("messages.cannot-purchase", player, null, null);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return false;
        }
        
        if (type == ClickType.LEFT) {
            if (level >= MissileWarsPlugin.getPlugin().getDeckManager().getMaxLevel(name)) {
                return false;
            }
            
            int spcost = (int) ConfigUtils.getItemValue(name, level + 1, "spcost");
            if (sp < spcost) {
                ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
                return false;
            }
            
            typeJson.put("level", level + 1);
            presetJson.put("skillpoints", sp - spcost);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return true;
        }
        
        if (type == ClickType.RIGHT) {
            if (level <= 0) {
                return false;
            }
            
            int spgain = (int) ConfigUtils.getItemValue(name, level, "spcost");
            if (level - 1 == 0) {
                typeJson.put("selected", "None");
            }
            
            typeJson.put("level", level - 1);
            presetJson.put("skillpoints", sp + spgain);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return true;
        }
        
        return false;
    }

    @Override
    public String getSubTitle() {
        String displayType = "";
        switch (type) {
            case "gpassive":
                displayType = "Global ";
            case "passive":
                displayType += "Passive";
                break;
            default:
                displayType = "Ability";
        }
        return displayType + " (" + presetJson.getInt("skillpoints") + "sp)";
    }

}
