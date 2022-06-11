package com.leomelonseeds.missilewars.invs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class RankedDeckCustomizer implements MWInventory {
    
    private Inventory inv;
    private JSONObject init;
    private JSONObject presetjson;
    private Player player;
    private String preset;
    private String deck;
    private FileConfiguration itemConfig;
    private DeckManager deckManager;
    private String[] items;
    
    public RankedDeckCustomizer(Player player, String deck) {
        deckManager = MissileWarsPlugin.getPlugin().getDeckManager();
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        init = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        items = new String[] {"missiles", "utility"};
        this.preset = "R";
        this.player = player;
        this.deck = deck;
        
        String title = itemConfig.getString("title.deck").replace("%deck%", deck).replace("%preset%", "R");
        inv = Bukkit.createInventory(null, 27, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
    }

    @Override
    public void updateInventory() {
        presetjson = init.getJSONObject(deck).getJSONObject(preset);
        // Add indicators
        for (String key : itemConfig.getConfigurationSection("indicators").getKeys(false)) {
            if (itemConfig.contains("indicators." + key + ".rankedslot")) {
                ItemStack item = deckManager.createItem("indicators." + key, 0, false);
                if (key.equals("skillpoints")) {
                    ItemMeta meta = item.getItemMeta();
                    String name = ConfigUtils.toPlain(item.getItemMeta().displayName());
                    meta.displayName(ConfigUtils.toComponent(name.replace("%sp%", presetjson.getInt("skillpoints") + "")));
                    item.setItemMeta(meta);
                }
                inv.setItem(itemConfig.getInt("indicators." + key + ".rankedslot"), item);
            }
        }
        
        // Add panes and misc items
        for (int i = 0; i < 3; i++) {
            inv.setItem(i * 9 + 1, blankName(new ItemStack(Material.IRON_BARS)));
        }
        
        // Missile + Utility items
        for (String s : items) {
            int index = getIndex(s);
            for (String u : presetjson.getJSONObject(s).keySet()) {
                ItemStack item = deckManager.createItem(u, presetjson.getJSONObject(s).getInt(u), 
                        s.equals("missiles"), init, deck, false);
                inv.setItem(index, item);
                index++;
            }
        }
        
        // Enchantments
        int index_e = getIndex("enchants");
        for (String key : itemConfig.getConfigurationSection(deck + ".enchants").getKeys(false)) {
            ItemStack item = deckManager.createItem(deck + ".enchants." + key, presetjson.getInt(key), 
                        false, init, deck, true);
            if (item.getType().toString().equals(itemConfig.getString("intangibles.locked"))) {
                item.setType(Material.getMaterial(itemConfig.getString("intangibles.unlocked")));
            }
            inv.setItem(index_e, item);
            index_e++;
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        // Back button
        if (slot == 26) {
            new PresetSelector(player, deck);
            return;
        }
        
        // Give back all skillpoints (oh boy this is a toughie) (wait no nevermind)
        if (slot == 17) {
            new ConfirmAction("Reclaim Skillpoints", player, this, (confirm) -> {
                if (confirm) {
                    for (String key : presetjson.keySet()) {
                        if (presetjson.get(key) instanceof Integer) {
                            presetjson.put(key, 0);
                        }
                        presetjson.put("skillpoints", itemConfig.getInt("default-skillpoints-ranked"));
                    }
                    for (String s : items) {
                        for (String key : presetjson.getJSONObject(s).keySet()) {
                            presetjson.getJSONObject(s).put(key, 1);
                        }
                    }
                    updateInventory();
                }
                return;
            });
        }
        
        ItemStack item = inv.getItem(slot);
        
        // Ensure we got one
        if (item == null || item.getItemMeta() == null) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        
        // Ensure it's clickable :))
        if (!meta.getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-gui"),
                PersistentDataType.STRING)) {
            return;
        }
        
        String storedName = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-gui"),
                PersistentDataType.STRING);
        String[] args = storedName.split("-");
        String name = args[0];
        int level = Integer.parseInt(args[1]);
        
        String args2[] = name.split("\\.");
        String realname = args2[args2.length - 1];
        
        int sp = presetjson.getInt("skillpoints");
        
        // Upgrade/downgade missiles/utility/enchants
        for (String s : items) {
            JSONObject cjson = presetjson.getJSONObject(s);
            if (cjson.has(realname)) {
                processClick(sp, name, level, cjson, type, realname);
                return;
            }
        }
        
        // Enchantments
        if (presetjson.has(realname)) {
            processClick(sp, name, level, presetjson, type, realname);
            return;
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
    
    // Return an item with a blank name
    private ItemStack blankName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(""));
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
        return itemConfig.getInt("indicators." + key + ".slot") + 2;
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
            updateInventory();
            return;
        }
    }
}
