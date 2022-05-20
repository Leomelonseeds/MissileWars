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

/** A class to manage Deck generation and selection. */
public class DeckManager {

    /** Set up the DeckManager with loaded decks. */
    public DeckManager() {
    }
    
    
    /**
     * Gets a deck for a player
     * 
     * @param json
     * @param deck
     * @return
     */
    public Deck getPlayerDeck(UUID uuid) {
        
        JSONObject basejson = MissileWarsPlugin.getPlugin().getJSON().getPlayer(uuid);
        String deck = basejson.getString("Deck");
        String preset = basejson.getString("Preset");
        JSONObject json = basejson.getJSONObject(deck).getJSONObject(preset);
        
        // There are 12 utility items in the game
        // Some items have their level determined on creation, while
        // some have their level determined on use
        // Items with level determined on creation:
        // Shield, Platform, Leaves, Arrows (both sentinel + berserk), Torpedo, Lingering Harming
        // Items with level determined on use:
        // Fireball, Splash, Obsidian Shield, Canopy
        // Spawn creeper is both: Different names on creation, different actions on place
        // For level determined on creation, fetch item from map based on level
        // For level determined on usage, fetch item from map and replace item %level%
        
        ArrayList<ItemStack> missiles = new ArrayList<>();
        ArrayList<ItemStack> utility = new ArrayList<>();
        ArrayList<ItemStack> gear = new ArrayList<>();
        
        // Create missiles
        for (String key : JSONObject.getNames(json.getJSONObject("missiles"))) {
            ItemStack m = createItem(key, json.getJSONObject("missiles").getInt(key));
            missiles.add(m);
        }
        
        // Create utility
        for (String key : JSONObject.getNames(json.getJSONObject("utility"))) {
            ItemStack u = createItem(key, json.getJSONObject("utility").getInt(key));
            utility.add(u);
        }
        
        // Create gear items
        switch (deck) {
        case "Vanguard": 
        {
            ItemStack sword = new ItemStack(Material.GOLDEN_SWORD);
            addEnch(sword, Enchantment.DAMAGE_ALL, json.getInt("sharp") * 2 - 1);
            addEnch(sword, Enchantment.KNOCKBACK, json.getInt("knockback"));
            addEnch(sword, Enchantment.FIRE_ASPECT, json.getInt("fireaspect"));
            gear.add(sword);
            ItemStack boots = new ItemStack(Material.GOLDEN_BOOTS);
            addEnch(boots, Enchantment.PROTECTION_FALL, json.getInt("featherfalling"));
            gear.add(boots);
            
            break;
        }
        case "Berserker":
        {
            ItemStack crossbow = new ItemStack(Material.CROSSBOW);
            addEnch(crossbow, Enchantment.DAMAGE_ALL, json.getInt("sharp") == 0 ? 0 : json.getInt("sharp") * 2 + 3);
            addEnch(crossbow, Enchantment.MULTISHOT, json.getInt("multishot"));
            addEnch(crossbow, Enchantment.QUICK_CHARGE, json.getInt("quickcharge"));
            gear.add(crossbow);
            ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
            addEnch(boots, Enchantment.PROTECTION_EXPLOSIONS, json.getInt("blastprot") * 3);
            gear.add(boots);
            
            break;
        }
        case "Sentinel":
        {
            ItemStack bow = new ItemStack(Material.BOW);
            addEnch(bow, Enchantment.DAMAGE_ALL, json.getInt("sharp") == 0 ? 0 : json.getInt("sharp") * 2 + 1);
            addEnch(bow, Enchantment.ARROW_DAMAGE, json.getInt("power"));
            addEnch(bow, Enchantment.ARROW_FIRE, json.getInt("flame"));
            addEnch(bow, Enchantment.ARROW_KNOCKBACK, json.getInt("punch"));
            gear.add(bow);
            ItemStack boots = new ItemStack(Material.IRON_BOOTS);
            addEnch(boots, Enchantment.PROTECTION_FIRE, json.getInt("fireprot") * 2);
            gear.add(boots);
            
            break;
        }
        case "Architect":
        {
            ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
            addEnch(pick, Enchantment.DAMAGE_ALL, json.getInt("sharp"));
            addEnch(pick, Enchantment.DIG_SPEED, json.getInt("efficiency"));
            gear.add(pick);
            ItemStack boots = new ItemStack(Material.CHAINMAIL_BOOTS);
            addEnch(boots, Enchantment.PROTECTION_PROJECTILE, json.getInt("projprot") * 2);
            gear.add(boots);
            
            break;
        }
        }
        
        return new Deck(deck, gear, missiles, utility);
    }
    
    /**
     * Check validity of deck
     *
     * @return the decks
     */
    public Boolean getDeck(String name) {
        switch (name) {
        case "Vanguard":
            return true;
        case "Sentinel":
            return true;
        case "Berserker":
            return true;
        case "Architect":
            return true;
        }
        return false;
    }
    
    
    /**
     * Add enchantment to level, making sure to not add if level is 0
     * 
     * @param item
     * @param ench
     * @param lvl
     */
    private void addEnch(ItemStack item, Enchantment ench, int lvl) {
        if (lvl <= 0) {
            return;
        }
        item.addEnchantment(ench, lvl);
    }
    
    /**
     * Translate integer to roman, only for first 10 numbers lol
     * 
     * @param i
     * @return
     */
    public String roman(int i) {
        switch (i) {
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
    private ItemStack createItem(String name, int level) {
        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial((String) ConfigUtils.getItemValue(name, level, "item")));
        if (name.contains("leaves")) {
            item.setAmount((Integer) ConfigUtils.getItemValue(name, level, "amount"));
        }
        ItemMeta itemMeta = item.getItemMeta();
        String displayName = (String) ConfigUtils.getItemValue(name, level, "name");
        displayName = displayName.replace("%level%", roman(level));
        itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        List<String> lore = (ArrayList<String>) ConfigUtils.getItemValue(name, level, "lore");
        setPlaceholders(lore, name, level, false);
        for (String loreString : lore) {
            lore.add(ChatColor.translateAlternateColorCodes('&', loreString));
        }
        itemMeta.setLore(lore);
        
        // Determine structure/utility
        String id = "item-structure";
        if (ConfigUtils.getItemValue(name, level, "file") == null) {
            id = "item-utility";
        }
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), id),
                PersistentDataType.STRING, name + "_" + level);
        
        // Setup item meta for special case items
        if (name.equals("splash")) {
            PotionMeta pmeta = (PotionMeta) itemMeta;
            PotionData pdata = new PotionData(PotionType.WATER);
            pmeta.setBasePotionData(pdata);
            itemMeta = pmeta;
        } else if (name.contains("lingering")) {
            int amplifier = (Integer) ConfigUtils.getItemValue(name, level, "amplifier");
            int duration = (Integer) ConfigUtils.getItemValue(name, level, "duration");
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
    public void setPlaceholders(List<String> lines, String name, int level, Boolean showNext) {
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
        FileConfiguration deckConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "decks.yml");
        for (String m : matches) {
            for (int i = 0; i < lines.size(); i++) {
                String get = m.replaceAll("%", "");
                String got1 = ConfigUtils.getItemValue(name, level, get) + "";
                String value = deckConfig.getString("text.level").replace("%1%", got1);
                if (showNext && level < getMaxLevel(name)) {
                    String got2 = ConfigUtils.getItemValue(name, level + 1, get) + "";
                    value = value + deckConfig.getString("text.nextlevel").replace("%2%", got2);
                }
                lines.set(i, lines.get(i).replaceAll(m, value));
            }
        }
    }
    
    /**
     * Gets the max level of an item
     * 
     * @param name
     * @return
     */
    private int getMaxLevel(String name) {
        FileConfiguration itemsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "items.yml");
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
