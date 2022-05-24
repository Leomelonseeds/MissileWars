package com.leomelonseeds.missilewars.decks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

/** A class to manage Deck generation and selection. */
public class DeckManager {
    
    private MissileWarsPlugin plugin;
    
    private final List<String> presets;
    private final List<String> decks;
    
    FileConfiguration itemsConfig;

    /** Set up the DeckManager with loaded decks. */
    public DeckManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        
        presets = new ArrayList<>(List.of(new String[]{"A", "B", "C"}));
        decks = new ArrayList<>(List.of(new String[]{"Vanguard", "Sentinel", "Berserker", "Architect"}));
        
        itemsConfig = ConfigUtils.getConfigFile("items.yml");
    }
    
    
    /**
     * Gets a deck for a player
     * 
     * @param json
     * @param deck
     * @return
     */
    public Deck getPlayerDeck(UUID uuid) {
        
        JSONObject basejson = plugin.getJSON().getPlayer(uuid);
        String deck = basejson.getString("Deck");
        JSONObject json = plugin.getJSON().getPlayerPreset(uuid);;
        
        // There are 12 utility items in the game
        // Some items have their level determined on creation, while
        // some have their level determined on use
        // Items with level determined on creation:
        // Shield, Platform, Leaves, Arrows (both sentinel + berserk), Torpedo, Lingering Harming
        // Items with level determined on use:
        // Fireball, Splash, Obsidian Shield, Canopy, Spawn Creeper
        
        ArrayList<ItemStack> missiles = new ArrayList<>();
        ArrayList<ItemStack> utility = new ArrayList<>();
        ArrayList<ItemStack> gear = new ArrayList<>();
        
        // Create missiles
        for (String key : JSONObject.getNames(json.getJSONObject("missiles"))) {
            ItemStack m = createItem(key, json.getJSONObject("missiles").getInt(key), true, false);
            missiles.add(m);
        }
        
        // Create utility
        for (String key : JSONObject.getNames(json.getJSONObject("utility"))) {
            ItemStack u = createItem(key, json.getJSONObject("utility").getInt(key), false, false);
            utility.add(u);
        }
        
        // Create gear items
        switch (deck) {
        case "Vanguard": 
        {
            String name = "vanguard_sword";
            ItemStack sword = createItem(name, 0, false, false);
            addEnch(sword, "sharpness", name, json);
            addEnch(sword, "fire_aspect", name, json);
            gear.add(sword);
            ItemStack boots = new ItemStack(Material.GOLDEN_BOOTS);
            addEnch(boots, "feather_falling", name, json);
            gear.add(boots);
            
            break;
        }
        case "Berserker":
        {
            String name = "berserker_crossbow";
            ItemStack crossbow = createItem(name, 0, false, false);
            addEnch(crossbow, "sharpness", name, json);
            addEnch(crossbow, "multishot", name, json);
            addEnch(crossbow, "quick_charge", name, json);
            gear.add(crossbow);
            ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
            addEnch(boots, "blast_protection", name, json);
            gear.add(boots);
            
            break;
        }
        case "Sentinel":
        {
            String name = "sentinel_bow";
            ItemStack bow = createItem(name, 0, false, false);
            addEnch(bow, "sharpness", name, json);
            addEnch(bow, "flame", name, json);
            addEnch(bow, "punch", name, json);
            addEnch(bow, "power", name, json);
            gear.add(bow);
            ItemStack boots = new ItemStack(Material.IRON_BOOTS);
            addEnch(boots, "fire_protection", name, json);
            gear.add(boots);
            
            break;
        }
        case "Architect":
        {
            String name = "architect_pickaxe";
            ItemStack pick = createItem(name, 0, false, false);
            addEnch(pick, "sharpness", name, json);
            addEnch(pick, "efficiency", name, json);
            addEnch(pick, "haste", name, json);
            gear.add(pick);
            ItemStack boots = new ItemStack(Material.CHAINMAIL_BOOTS);
            addEnch(boots, "projectile_protection", name, json);
            gear.add(boots);
            
            break;
        }
        }
        
        // Make gear items unbreakable
        for (ItemStack item : gear) {
            ItemMeta meta = item.getItemMeta();
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        
        return new Deck(deck, gear, missiles, utility);
    }
    
    /**
     * Check validity of deck
     *
     * @return the decks
     */
    public List<String> getDecks() {
        return decks;
    }
    
    /**
     * Get list of presets
     * 
     * @return
     */
    public List<String> getPresets() {
        return presets;
    }
     
    /**
     * Add enchantment to level, making sure to not add if level is 0
     * 
     * @param item
     * @param ench
     * @param lvl
     */
    private void addEnch(ItemStack item, String ench, String itemname, JSONObject json) {
        int lvl = itemsConfig.getInt(itemname + ".enchants." + ench + "." + json.getInt(ench));
        if (lvl <= 0) {
            return;
        }
        // Add custom haste effect
        if (ench.equals("haste")) {
            ItemMeta meta = item.getItemMeta();
            List<Component> loreLines = meta.lore();
            List<Component> newLore = new ArrayList<>();
            newLore.add(Component.text(ChatColor.GRAY + "Haste " + roman(lvl)));
            for (Component c : loreLines) {
                newLore.add(c);
            }
            meta.lore(newLore);
            item.setItemMeta(meta);
            return;
        }
        Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(ench));
        item.addUnsafeEnchantment(enchant, lvl);
    }
    
    /**
     * Translate integer to roman, only for first 10 numbers lol
     * 
     * @param i
     * @return
     */
    public String roman(int i) {
        switch (i) {
        case 0:
            return "";
        case 1:
            return "I";
        case 2:
            return "II";
        case 3:
            return "III";
        case 4:
            return "IV";
        case 5:
            return "V";
        case 6:
            return "VI";
        case 7:
            return "VII";
        case 8:
            return "VIII";
        case 9:
            return "IX";
        case 10:
            return "X";
        }
        return null;
    }
    
    /**
     * Create an ItemStack from JSON
     *
     * @param name
     * @param level
     * @return an ItemStack
     */
    public ItemStack createItem(String name, int level, Boolean missile, Boolean isGUI) {
        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial((String) ConfigUtils.getItemValue(name, level, "item")));
        if (!isGUI) {
            if (ConfigUtils.getItemValue(name, level, "amount") != null) {
                item.setAmount((int) ConfigUtils.getItemValue(name, level, "amount"));
            }
            // Don't bother with arrows
            if (name.equals("arrows")) {
                return item;
            }
        } else {
            // Make item count reflect its level
            item.setAmount(Math.max(level, 1));
        }
        
        // Find item name and lore
        ItemMeta itemMeta = item.getItemMeta();
        if (ConfigUtils.getItemValue(name, level, "name") != null) {
            String displayName = (String) ConfigUtils.getItemValue(name, level, "name");
            itemMeta.displayName(ConfigUtils.toComponent(displayName.replace("%level%", roman(level))));
        }
        
        if (ConfigUtils.getItemValue(name, level, "lore") != null) {
            @SuppressWarnings("unchecked")
            List<String> lore = new ArrayList<>((ArrayList<String>) ConfigUtils.getItemValue(name, level, "lore"));
            setPlaceholders(lore, name, level, isGUI, missile);
            List<Component> finalLore = new ArrayList<>();
            for (String s : lore) {
                finalLore.add(Component.text(s));
            }
            itemMeta.lore(finalLore);
        }
        
        // Inject NBT data
        String id = "item-structure";
        if (isGUI) {
            id = "item-gui";
        } else if (ConfigUtils.getItemValue(name, level, "file") == null) {
            id = "item-utility";
        }
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, id),
                PersistentDataType.STRING, name + "_" + level);
        
        // Setup item meta for potions
        if (name.equals("splash")) {
            PotionMeta pmeta = (PotionMeta) itemMeta;
            PotionData pdata = new PotionData(PotionType.WATER);
            pmeta.setBasePotionData(pdata);
            itemMeta = pmeta;
        } else if (name.equals("lingering_harming")) {
            int amplifier = (int) ConfigUtils.getItemValue(name, level, "amplifier");
            int duration = (int) ConfigUtils.getItemValue(name, level, "duration");
            PotionMeta pmeta = (PotionMeta) itemMeta;
            pmeta.addCustomEffect(new PotionEffect(PotionEffectType.HARM, duration, amplifier), true);
            pmeta.setColor(Color.PURPLE);
            itemMeta = pmeta;
        } 
        item.setItemMeta(itemMeta);
        return item;
    }
    
    /**
     * Set placeholders in an itemstack lore
     * 
     * @param line
     * @param level
     * @param showNext
     * @return
     */
    public void setPlaceholders(List<String> lines, String name, int level, Boolean showNext, Boolean missile) {
        // Add missile stats for missiles
        if (missile) {
            List<String> stats = itemsConfig.getStringList("text.missilestats");
            lines.addAll(stats);
        }
        // Compile lore into single line
        String line = "";
        for (String s : lines) {
            line = line + " " + s;
        }
        // Match all instances of placeholders 
        Matcher matcher = Pattern.compile("%[^%]+%").matcher(line);
        Set<String> matches = new HashSet<>();
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        // Replace all placeholder matches with specific value
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            for (String m : matches) {
                if (!l.contains(m)) {
                    continue;
                }
                String get = m.replaceAll("%", "");
                String got1 = ConfigUtils.getItemValue(name, level, get) + "";
                String value = itemsConfig.getString("text.level").replace("%1%", got1);
                if (showNext && level < getMaxLevel(name)) {
                    String got2 = ConfigUtils.getItemValue(name, level + 1, get) + "";
                    value = value + itemsConfig.getString("text.nextlevel").replace("%2%", got2);
                }
                l = l.replaceAll(m, value);
            }
            lines.set(i, ChatColor.translateAlternateColorCodes('&', l));
        }
    }
    
    /**
     * Gets the max level of an item
     * 
     * @param name
     * @return
     */
    public int getMaxLevel(String name) {
        Set<String> keys = itemsConfig.getKeys(false);
        ArrayList<Integer> levels = new ArrayList<>();
        for (String key : keys) {
            try {
                int i = Integer.parseInt(key);
                levels.add(i);
            } catch (NumberFormatException e) {
                continue;
            }
        }
        int result = 0;
        for (int i : levels) {
            if (i > result) {
                result = i;
            }
        }
        return result;
    }
}
