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
import com.leomelonseeds.missilewars.utilities.JSONManager;
import com.leomelonseeds.missilewars.utilities.RankUtils;

import net.kyori.adventure.text.Component;

public class DeckCustomizer implements MWInventory {
    
    private Inventory inv;
    private JSONObject init;
    private JSONObject presetjson;
    private Player player;
    private String deck;
    private String preset;
    private FileConfiguration itemConfig;
    private DeckManager deckManager;
    private JSONManager jsonManager;
    private String[] items;
    private String[] abilities;
    
    public DeckCustomizer(Player player, String deck, String preset) {
        deckManager = MissileWarsPlugin.getPlugin().getDeckManager();
        jsonManager = MissileWarsPlugin.getPlugin().getJSON();
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        init = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        items = new String[] {"missiles", "utility"};
        abilities = new String[] {"gpassive", "passive", "ability"};
        this.player = player;
        this.deck = deck;
        this.preset = preset;
        
        String title = itemConfig.getString("title.deck").replace("%deck%", deck).replace("%preset%", preset);
        inv = Bukkit.createInventory(null, 54, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
    }

    @Override
    public void updateInventory() {
        presetjson = init.getJSONObject(deck).getJSONObject(preset);
        // Add indicators
        for (String key : itemConfig.getConfigurationSection("indicators").getKeys(false)) {
            ItemStack item = deckManager.createItem("indicators." + key, 0, false);
            if (key.equals("skillpoints")) {
                ItemMeta meta = item.getItemMeta();
                String name = ConfigUtils.toPlain(item.getItemMeta().displayName());
                meta.displayName(ConfigUtils.toComponent(name.replace("%sp%", presetjson.getInt("skillpoints") + "")));
                item.setItemMeta(meta);
            }
            inv.setItem(itemConfig.getInt("indicators." + key + ".slot"), item);
        }
        
        // Add panes and misc items
        for (int i = 0; i < 6; i++) {
            inv.setItem(i * 9 + 1, blankName(new ItemStack(Material.IRON_BARS)));
        }
        
        // Too lazy to come up with formula
        for (int i : new int[]{35, 44}) {
            inv.setItem(i, blankName(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
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
                        false, init, deck, true);
            inv.setItem(index_g, item);
            index_g++;
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        // Back button
        if (slot == 53) {
            new PresetSelector(player, deck);
            return;
        }
        
        // Reset to default config
        if (slot == 26) {
            new ConfirmAction("Reset Preset", player, this, (confirm) -> {
                if (confirm) {
                    init.getJSONObject(deck).put(preset, jsonManager.getDefaultPreset(deck));
                    updateInventory();
                }
                return;
            });
        }
        
        // Give back all skillpoints (oh boy this is a toughie) (wait no nevermind)
        if (slot == 17) {
            new ConfirmAction("Reclaim Skillpoints", player, this, (confirm) -> {
                if (confirm) {
                    for (String key : presetjson.keySet()) {
                        if (presetjson.get(key) instanceof Integer) {
                            presetjson.put(key, 0);
                        }
                        int exp = MissileWarsPlugin.getPlugin().getSQL().getExpSync(player.getUniqueId());
                        int level = RankUtils.getRankLevel(exp);
                        int sp = itemConfig.getInt("default-skillpoints") + level;
                        presetjson.put("skillpoints", sp);
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
        
        // Ensure it's clickable :))
        if (!item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-gui"),
                PersistentDataType.STRING)) {
            return;
        }
        
        String storedName = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-gui"),
                PersistentDataType.STRING);
        String[] args = storedName.split("_");
        String name = args[0];
        int level = Integer.parseInt(args[1]);
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
}
