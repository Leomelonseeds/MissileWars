package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.invs.ConfirmAction;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.JSONManager;

import net.kyori.adventure.text.Component;

public class OldDeckCustomizer extends MWInventory {
    
    private JSONObject init;
    private JSONObject presetjson;
    private String deck;
    private String preset;
    private FileConfiguration itemConfig;
    private DeckManager deckManager;
    private JSONManager jsonManager;
    private String[] items;
    private String[] abilities;
    
    public OldDeckCustomizer(Player player, String deck, String preset) {
        super(player, 54, 
            ConfigUtils.getConfigFile("items.yml")
                .getString("title.deck")
                .replace("%deck%", deck)
                .replace("%preset%", preset)
        );
        deckManager = MissileWarsPlugin.getPlugin().getDeckManager();
        jsonManager = MissileWarsPlugin.getPlugin().getJSON();
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        init = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        items = new String[] {"missiles", "utility"};
        abilities = new String[] {"gpassive", "passive", "ability"};
        this.deck = deck;
        this.preset = preset;
    }

    @Override
    public void updateInventory() {
        // TODO: Optimize by splitting into different functions and only updating what is needed
        // when a click is registered
        presetjson = init.getJSONObject(deck).getJSONObject(preset);
        // Add indicators
        for (String key : itemConfig.getConfigurationSection("indicators").getKeys(false)) {
            String slotPath = "indicators." + key + ".slot-old";
            if (!itemConfig.contains(slotPath)) {
                continue;
            }
            
            ItemStack item = InventoryUtils.createItem("indicators." + key);
            if (key.equals("info")) {
                ItemMeta meta = item.getItemMeta();
                List<String> newLore = new ArrayList<>();
                for (Component c : item.lore()) {
                    String info = ConfigUtils.toPlain(c);
                    if (info.contains("Balance")) {
                        double bal = MissileWarsPlugin.getPlugin().getEconomy().getBalance(player);
                        info = info.replace("null", bal + "");
                    } else if (info.contains("Available Skillpoints")) {
                        info = info.replace("null", presetjson.getInt("skillpoints") + "");
                    }
                    newLore.add(info);
                }
                meta.lore(ConfigUtils.toComponent(newLore));
                item.setItemMeta(meta);
            }
            inv.setItem(itemConfig.getInt(slotPath), item);
        }
        
        // Add panes and misc items
        for (int i = 0; i < 6; i++) {
            inv.setItem(i * 9 + 1, blankName(new ItemStack(Material.IRON_NUGGET)));
            inv.setItem(i * 9 + 7, blankName(new ItemStack(Material.IRON_BARS)));
        }
        inv.setItem(35, blankName(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        inv.setItem(44, blankName(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        
        // Missile + Utility items
        for (String s : items) {
            int index = getIndex(s);
            for (String u : presetjson.getJSONObject(s).keySet()) {
                ItemStack item = deckManager.createItem(u, presetjson.getJSONObject(s).getInt(u), 
                        s.equals("missiles"), init, deck, false, preset);
                int add = itemConfig.getInt(u + ".index");
                if (s.equals("utility")) {
                    add -= 5;
                }
                inv.setItem(index + add, item);
            }
        }
        
        // Enchantments
        int index_e = getIndex("enchants");
        for (String key : itemConfig.getConfigurationSection(deck + ".enchants").getKeys(false)) {
            ItemStack item = deckManager.createItem(deck + ".enchants." + key, 
                    presetjson.getJSONObject("enchants").getInt(key), false, init, deck, true, preset);
            inv.setItem(index_e, item);
            index_e++;
        }
        
        // Global Passives
        int index_g = getIndex("gpassive");
        JSONObject gpassivejson = presetjson.getJSONObject("gpassive");
        for (String key : itemConfig.getConfigurationSection("gpassive").getKeys(false)) {
            int level = 0;
            if (gpassivejson.getString("selected").equals(key)) {
                level = gpassivejson.getInt("level");
            }
            ItemStack item = deckManager.createItem("gpassive." + key, level, 
                        false, init, deck, true, preset);
            inv.setItem(index_g, item);
            index_g++;
        }
        
        // Normal passives and abilities
        for (String p : new String[] {"passive", "ability"}) {
            int index = getIndex(p);
            JSONObject currentjson = presetjson.getJSONObject(p);
            for (String key : itemConfig.getConfigurationSection(deck + "." + p).getKeys(false)) {
                int level = 0;
                if (currentjson.getString("selected").equals(key)) {
                    level = currentjson.getInt("level");
                }
                ItemStack item = deckManager.createItem(deck + "." + p + "." + key, level, 
                            false, init, deck, true, preset);
                inv.setItem(index, item);
                index++;
            }
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        // Back button
        if (slot == itemConfig.getInt("indicators.back.slot-old")) {
            new OldPresetSelector(player, deck);
            return;
        }
        
        // Reset to default config
        if (slot == itemConfig.getInt("indicators.reset.slot-old")) {
            new ConfirmAction("Reset Preset", player, this, (confirm) -> {
                if (!confirm) {
                    return;
                }
                JSONObject def = jsonManager.getDefaultPreset(deck);
                init.getJSONObject(deck).put(preset, def);
                updateInventory();
            });
            return;
        }
        
        // Give back all skillpoints (oh boy this is a toughie) (wait no nevermind)
        if (slot == itemConfig.getInt("indicators.skillpoints.slot-old")) {
            new ConfirmAction("Reclaim Skillpoints", player, this, (confirm) -> {
                if (!confirm) {
                    return;
                }
                for (String key : presetjson.keySet()) {
                    if (presetjson.get(key) instanceof Integer) {
                        presetjson.put(key, 0);
                    }
                }
                for (String s : items) {
                    for (String key : presetjson.getJSONObject(s).keySet()) {
                        presetjson.getJSONObject(s).put(key, 1);
                    }
                }
                for (String s : abilities) {
                    JSONObject j = presetjson.getJSONObject(s);
                    j.put("selected", "None");
                    j.put("level", 0);
                }
                presetjson.put("skillpoints", jsonManager.getMaxSkillpoints(player.getUniqueId()));
                updateInventory();
            });
            return;
        }
        
        ItemStack item = inv.getItem(slot);
        
        // Ensure we got one
        if (item == null || item.getItemMeta() == null) {
            return;
        }
        
        // Ensure it's clickable :))
        String storedName = InventoryUtils.getStringFromItem(item, "item-gui");
        if (storedName == null) {
            return;
        }
        
        String[] args = storedName.split("-");
        String name = args[0];
        int level = Integer.parseInt(args[1]);
        
        String args2[] = name.split("\\.");
        String realname = args2[args2.length - 1];
        
        // Unlock a locked item
        if (item.getType().toString().equals(itemConfig.getString("intangibles.locked"))) {
            double bal = MissileWarsPlugin.getPlugin().getEconomy().getBalance(player);
            int cost = (int) ConfigUtils.getItemValue(name, level, "cost");
            if (bal >= cost) {
                new ConfirmAction("Purchase '" + realname + "'", player, this, (confirm) -> {
                    if (!confirm) {
                        return;
                    }
                    if (init.has(realname)) {
                        init.put(realname, true);
                    } else if (init.getJSONObject(deck).has(realname)) {
                        init.getJSONObject(deck).put(realname, true);
                    }
                    MissileWarsPlugin.getPlugin().getEconomy().withdrawPlayer(player, cost);
                    updateInventory();
                });
            } else {
                ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
            }
            return;
        }
        
        int sp = presetjson.getInt("skillpoints");
        
        // Upgrade/downgade missiles/utility
        for (String s : ArrayUtils.add(items, "enchants")) {
            JSONObject cjson = presetjson.getJSONObject(s);
            if (cjson.has(realname)) {
                processClick(sp, name, level, cjson, type, realname);
                return;
            }
        }
        
        // Global Passive
        if (itemConfig.getConfigurationSection("gpassive").getKeys(false).contains(realname)) {
            processAbilityClick("gpassive", sp, name, realname, level, type);
            return;
        }
        
        // Passives or abilities
        for (String p : new String[] {"passive", "ability"}) {
            if (itemConfig.getConfigurationSection(deck + "." + p).getKeys(false).contains(realname)) {
                processAbilityClick(p, sp, name, realname, level, type);
                return;
            }
        }
    }
    
    // Return an item with a blank name
    private ItemStack blankName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String name = item.getType() == Material.IRON_NUGGET ? "&f→" : "";
        meta.displayName(ConfigUtils.toComponent(name));
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Get index of item
     * 
     * @param key
     * @return
     */
    private int getIndex(String key) {
        return itemConfig.getInt("indicators." + key + ".slot-old") + 2;
    }
    
    /**
     * Process a click for a missile/utility/enchantment upgrade
     * 
     * @param sp
     * @param name
     * @param level
     * @param json
     * @param type
     */
    private void processClick(int sp, String name, int level, JSONObject json, ClickType type, String realname) {
        
        // Left click to upgrade...
        if (type == ClickType.LEFT) {
            if (level >= deckManager.getMaxLevel(name)) {
                return;
            }
            
            int spcost = (int) ConfigUtils.getItemValue(name, level + 1, "spcost");
            
            if (sp < spcost) {
                ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
                return;
            }
            
            json.put(realname, json.getInt(realname) + 1);
            presetjson.put("skillpoints", sp - spcost);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            
            checkTutorial();
            updateInventory();
            return;
        }
        
        // Right click to downgrade.
        if (type == ClickType.RIGHT) {
            // Enchantments have min level 0
            if (level <= (name.contains(".") ? 0 : 1)) {
                return;
            }
            
            int spgain = (int) ConfigUtils.getItemValue(name, level, "spcost");
            
            json.put(realname, json.getInt(realname) - 1);
            presetjson.put("skillpoints", sp + spgain);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            
            checkTutorial();
            updateInventory();
            return;
        }
    }

    // Register stage completion if player upgrades or downgrades and enchant
    private void checkTutorial() {
        if (!deck.equals("Berserker")) {
            return;
        }
        
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena instanceof TutorialArena &&
            presetjson.getJSONObject("missiles").getInt("warhead") == 2) {
            ((TutorialArena) arena).registerStageCompletion(player, 6);
        }
    }
    
    /**
     * Process click for global passive/passive/ability
     * 
     * @param type
     * @param sp
     * @param name
     * @param level
     * @param clickType
     */
    private void processAbilityClick(String type, int sp, String name, String realname, int level, ClickType clickType) {
        JSONObject json = presetjson.getJSONObject(type);
        
        // Purchase this ability if player doesn't have one selected
        if (json.getString("selected").equals("None") && clickType == ClickType.LEFT) {
            int spcost = (int) ConfigUtils.getItemValue(name, 1, "spcost");
            
            if (sp < spcost) {
                ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
                return;
            }
            
            json.put("selected", realname);
            json.put("level", 1);
            presetjson.put("skillpoints", sp - spcost);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            updateInventory();
            return;
        }
        
        // Otherwise make sure the one we clicked on is selected one
        if (!json.getString("selected").equals(realname)) {
            ConfigUtils.sendConfigMessage("messages.cannot-purchase", player, null, null);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        if (clickType == ClickType.LEFT) {
            
            if (level >= deckManager.getMaxLevel(name)) {
                return;
            }
            
            int spcost = (int) ConfigUtils.getItemValue(name, level + 1, "spcost");
            
            if (sp < spcost) {
                ConfigUtils.sendConfigMessage("messages.purchase-unsuccessful", player, null, null);
                return;
            }
            
            json.put("level", level + 1);
            presetjson.put("skillpoints", sp - spcost);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            updateInventory();
            return;
        }
        
        if (clickType == ClickType.RIGHT) {
            if (level <= 0) {
                return;
            }
            
            int spgain = (int) ConfigUtils.getItemValue(name, level, "spcost");
            
            if (level - 1 == 0) {
                json.put("selected", "None");
            }
            
            json.put("level", level - 1);
            presetjson.put("skillpoints", sp + spgain);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            updateInventory();
            return;
        }
    }
}
