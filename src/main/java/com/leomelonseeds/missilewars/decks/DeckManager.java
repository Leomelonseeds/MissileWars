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
    FileConfiguration deckConfig;

    /** Set up the DeckManager with loaded decks. */
    public DeckManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        
        presets = new ArrayList<>(List.of(new String[]{"A", "B", "C"}));
        decks = new ArrayList<>(List.of(new String[]{"Vanguard", "Sentinel", "Berserker", "Architect"}));
        
        itemsConfig = ConfigUtils.getConfigFile("items.yml");
        deckConfig = ConfigUtils.getConfigFile("decks.yml");
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
            ItemStack m = createItem(key, json.getJSONObject("missiles").getInt(key), true);
            missiles.add(m);
        }
        
        // Create utility
        for (String key : JSONObject.getNames(json.getJSONObject("utility"))) {
            ItemStack u = createItem(key, json.getJSONObject("utility").getInt(key), false);
            utility.add(u);
        }
        
        // Create gear items
        switch (deck) {
        case "Vanguard": 
        {
            ItemStack sword = createItem("vanguard_sword", 0, false);
            addEnch(sword, Enchantment.DAMAGE_ALL, json.getInt("sharpness") * 2 - 1);
            addEnch(sword, Enchantment.FIRE_ASPECT, json.getInt("fireaspect"));
            gear.add(sword);
            ItemStack boots = new ItemStack(Material.GOLDEN_BOOTS);
            addEnch(boots, Enchantment.PROTECTION_FALL, json.getInt("featherfalling"));
            gear.add(boots);
            
            break;
        }
        case "Berserker":
        {
            ItemStack crossbow = createItem("berserker_crossbow", 0, false);
            addEnch(crossbow, Enchantment.DAMAGE_ALL, json.getInt("sharpness") == 0 ? 0 : json.getInt("sharpness") * 2 + 3);
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
            ItemStack bow = createItem("sentinel_bow", 0, false);
            addEnch(bow, Enchantment.DAMAGE_ALL, json.getInt("sharpness") * 2);
            addEnch(bow, Enchantment.ARROW_FIRE, json.getInt("flame"));
            addEnch(bow, Enchantment.ARROW_KNOCKBACK, json.getInt("punch"));
            addEnch(bow, Enchantment.ARROW_KNOCKBACK, json.getInt("power"));
            gear.add(bow);
            ItemStack boots = new ItemStack(Material.IRON_BOOTS);
            addEnch(boots, Enchantment.PROTECTION_FIRE, json.getInt("fireprot") * 2);
            gear.add(boots);
            
            break;
        }
        case "Architect":
        {
            ItemStack pick = createItem("architect_pickaxe", 0, false);
            addEnch(pick, Enchantment.DAMAGE_ALL, json.getInt("sharpness"));
            addEnch(pick, Enchantment.DIG_SPEED, json.getInt("efficiency"));
            // Add custom haste effect
            if (json.getInt("haste") > 0) {
                ItemMeta meta = pick.getItemMeta();
                List<Component> loreLines = meta.lore();
                List<Component> newLore = new ArrayList<>();
                newLore.add(Component.text(ChatColor.GRAY + "Haste " + roman(json.getInt("haste"))));
                for (Component c : loreLines) {
                    newLore.add(c);
                }
                meta.lore(newLore);
                pick.setItemMeta(meta);
            }
            gear.add(pick);
            ItemStack boots = new ItemStack(Material.CHAINMAIL_BOOTS);
            addEnch(boots, Enchantment.PROTECTION_PROJECTILE, json.getInt("projprot") * 2);
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
    private void addEnch(ItemStack item, Enchantment ench, int lvl) {
        if (lvl <= 0) {
            return;
        }
        item.addUnsafeEnchantment(ench, lvl);
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
    public ItemStack createItem(String name, int level, Boolean missile) {
        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial((String) ConfigUtils.getItemValue(name, level, "item")));
        if (ConfigUtils.getItemValue(name, level, "amount") != null) {
            item.setAmount((int) ConfigUtils.getItemValue(name, level, "amount"));
        }
        // Don't bother with arrows
        if (name.equals("arrows")) {
            return item;
        }
        // Find item name and lore
        ItemMeta itemMeta = item.getItemMeta();
        String displayName = (String) ConfigUtils.getItemValue(name, level, "name");
        displayName = ChatColor.translateAlternateColorCodes('&', displayName.replace("%level%", roman(level)));
        itemMeta.displayName(Component.text(displayName));
        
        @SuppressWarnings("unchecked")
        List<String> lore = new ArrayList<>((ArrayList<String>) ConfigUtils.getItemValue(name, level, "lore"));
        setPlaceholders(lore, name, level, false, missile);
        List<Component> finalLore = new ArrayList<>();
        for (String s : lore) {
            finalLore.add(Component.text(s));
        }
        itemMeta.lore(finalLore);
        
        // Determine structure/utility
        String id = "item-structure";
        if (ConfigUtils.getItemValue(name, level, "file") == null) {
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
            List<String> stats = itemsConfig.getStringList("missilestats");
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
                String value = deckConfig.getString("text.level").replace("%1%", got1);
                if (showNext && level < getMaxLevel(name)) {
                    String got2 = ConfigUtils.getItemValue(name, level + 1, get) + "";
                    value = value + deckConfig.getString("text.nextlevel").replace("%2%", got2);
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
