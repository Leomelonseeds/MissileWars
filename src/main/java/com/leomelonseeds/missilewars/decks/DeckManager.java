package com.leomelonseeds.missilewars.decks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
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
        JSONObject json = plugin.getJSON().getPlayerPreset(uuid);
        
        // Custom deck orderings: array of 8 numbers
        // Index + 1 = slot, array[index] = index value defined in items.yml
        // Default: [0, 1, 2, 3, 4, 5, 6, 7]
        // If for example 0 is assigned to 7, then the item with index 7 will be placed in 0
        List<DeckItem> pool = Arrays.asList(new DeckItem[8]);
        List<ItemStack> gear = new ArrayList<>();
        Player player = Bukkit.getPlayer(uuid);
        @SuppressWarnings("unchecked")
        List<Integer> layout = (List<Integer>)(Object) json.getJSONArray("layout").toList();
        
        // Create pool items
        for (String s : new String[] {"missiles", "utility"}) {
            for (String key : json.getJSONObject(s).keySet()) {
                int level = json.getJSONObject(s).getInt(key);
                ItemStack i = createItem(key, level, s.equals("missiles"));
                // Change color of lava splash
                if (i.getType() == Material.SPLASH_POTION && plugin.getJSON().getAbility(uuid, "lavasplash") > 0) {
                    PotionMeta pmeta = (PotionMeta) i.getItemMeta();
                    String name = ConfigUtils.toPlain(pmeta.displayName());
                    name = name.replaceFirst("9", "6");  // Make name orange
                    pmeta.displayName(ConfigUtils.toComponent(name));
                    pmeta.setColor(Color.ORANGE);
                    i.setItemMeta(pmeta);
                }
                // Give slowness arrows in case of berserker
                int slowarrow = plugin.getJSON().getAbility(uuid, "slownessarrows");
                if (i.getType() == Material.ARROW && slowarrow > 0) {
                    int amplifier = (int) ConfigUtils.getAbilityStat("Berserker.passive.slownessarrows", slowarrow, "amplifier");
                    int duration = (int) ConfigUtils.getAbilityStat("Berserker.passive.slownessarrows", slowarrow, "duration") * 20;
                    i.setType(Material.TIPPED_ARROW);
                    PotionMeta pmeta = (PotionMeta) i.getItemMeta();
                    pmeta.setColor(Color.fromRGB(92, 110, 131));
                    pmeta.displayName(ConfigUtils.toComponent("&fArrow of Slowness"));
                    pmeta.addCustomEffect(new PotionEffect(PotionEffectType.SLOW, duration, amplifier), true);
                    i.setItemMeta(pmeta);
                }
                int max = (int) ConfigUtils.getItemValue(key, level, "max");
                int cd = (int) ConfigUtils.getItemValue(key, level, "cooldown");
                pool.set(layout.indexOf(itemsConfig.getInt(key + ".index")), new DeckItem(i, cd, max, player));
            }
        }
        
        // Create gear items
        ItemStack weapon = createItem(deck + ".weapon", 0, false);
        switch (deck) {
        case "Vanguard": 
        {
            addEnch(weapon, "sharpness", deck, json);
            addEnch(weapon, "fire_aspect", deck, json);
            ItemStack boots = new ItemStack(Material.GOLDEN_BOOTS);
            addEnch(boots, "feather_falling", deck, json);
            gear.add(boots);
            
            break;
        }
        case "Berserker":
        {
            addEnch(weapon, "sharpness", deck, json);
            addEnch(weapon, "multishot", deck, json);
            addEnch(weapon, "quick_charge", deck, json);
            ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
            addEnch(boots, "blast_protection", deck, json);
            gear.add(boots);
            
            break;
        }
        case "Sentinel":
        {
            addEnch(weapon, "sharpness", deck, json);
            addEnch(weapon, "flame", deck, json);
            if (plugin.getJSON().getAbility(uuid, "punch") > 0) {
                weapon.addUnsafeEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
            }
            ItemStack boots = new ItemStack(Material.IRON_BOOTS);
            addEnch(boots, "fire_protection", deck, json);
            gear.add(boots);
            
            break;
        }
        case "Architect":
        {
            addEnch(weapon, "sharpness", deck, json);
            addEnch(weapon, "efficiency", deck, json);
            addEnch(weapon, "haste", deck, json);
            ItemStack boots = new ItemStack(Material.CHAINMAIL_BOOTS);
            addEnch(boots, "projectile_protection", deck, json);
            gear.add(boots);
            
            break;
        }
        }
        
        gear.add(weapon);
        
        // Make gear items unbreakable
        for (ItemStack item : gear) {
            ItemMeta meta = item.getItemMeta();
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        
        return new Deck(deck, gear, pool);
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
    private void addEnch(ItemStack item, String ench, String deck, JSONObject json) {
        int lvl = itemsConfig.getInt(deck + ".enchants." + ench + "." + json.getInt(ench) + ".level");
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
     * Same as createItem, but json is default null
     * 
     * @param name
     * @param level
     * @param missile
     * @return
     */
    public ItemStack createItem(String name, int level, Boolean missile) {
        return createItem(name, level, missile, null, null, false, null);
    }
    
    /**
     * General purpose item creation function. Creates items for
     * decks, as well as for the deck GUIs.
     *
     * @param name
     * @param level
     * @return an ItemStack
     */
    public ItemStack createItem(String name, int level, Boolean missile, JSONObject playerjson, String deck, Boolean intangible, String preset) {
        String realname = name;
        // If the name is a config path, find its real name
        if (name.contains(".")) {
            String args[] = name.split("\\.");
            realname = args[args.length - 1];
        }
        
        // Find material
        String material;
        if (intangible) {
            material = itemsConfig.getString("intangibles.unlocked");
        } else {
            material = (String) ConfigUtils.getItemValue(name, level, "item");
        }
        
        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial(material));
        if (deck != null) {
            item.setAmount(Math.max(level, 1));  // Make item count reflect its level
        }
        
        // Find item name and lore
        ItemMeta itemMeta = item.getItemMeta();
        if (ConfigUtils.getItemValue(name, level, "name") != null || 
                (name.contains("enchants") && !name.contains("indicator"))) {
            String displayName = (String) ConfigUtils.getItemValue(name, level, "name");
            if (deck != null) {
                if (name.contains("enchants")) {
                    displayName = (itemsConfig.getString("enchants.name").replace("%enchant%", getEnchName(realname)));
                }
                displayName = displayName + itemsConfig.getString("text.actuallevel");
                displayName = displayName.replace("%current%", level + "").replace("%max%", getMaxLevel(name) + "");
            }
            itemMeta.displayName(ConfigUtils.toComponent(displayName.replace("%level%", roman(level))));
        } 
        
        Object templore = ConfigUtils.getItemValue(name, level, "lore");
        if (name.contains("enchants") && !name.contains("indicators")) {
            templore = ConfigUtils.getItemValue("enchants", level, "lore");
        }
        if (templore != null) {
            // Set lore
            @SuppressWarnings("unchecked")
            List<String> lore = new ArrayList<>((ArrayList<String>) templore);
            
            // Add missile stats for missiles, and max + cooldown
            if (missile) {
                lore.addAll(itemsConfig.getStringList("text.missilestats"));
            }
            lore.addAll(itemsConfig.getStringList("text.itemstats"));
            
            // Compile lore into single line
            String line = "";
            for (String s : lore) {
                line = line + " " + s;
            }
            
            // Match all instances of placeholders 
            Matcher matcher = Pattern.compile("%[^% ]+%").matcher(line);
            Set<String> matches = new HashSet<>();
            while (matcher.find()) {
                matches.add(matcher.group());
            }
            
            // Replace all placeholder matches with specific value
            for (int i = 0; i < lore.size(); i++) {
                String l = lore.get(i);
                for (String m : matches) {
                    if (!l.contains(m)) {
                        continue;
                    }
                    String get = m.replaceAll("%", "");
                    String got1 = ConfigUtils.getItemValue(name, Math.max(level, 1), get) + "";
                    String value = itemsConfig.getString("text.level").replace("%1%", got1);
                    if (deck != null && level < getMaxLevel(name) && level > 0) {
                        String got2 = ConfigUtils.getItemValue(name, level + 1, get) + "";
                        if (!got1.equals(got2)) {
                            value = value + itemsConfig.getString("text.nextlevel").replace("%2%", got2);
                        }
                    }
                    l = l.replaceAll(m, value);
                }
                lore.set(i, l);
            }
            
            // Add extra lore for GUIs
            if (deck != null) {
                lore.add("&f");
                JSONObject deckjson = playerjson.getJSONObject(deck);
                // Add lore of unlocking possibility
                if (((deckjson.has(realname) && !deckjson.getBoolean(realname)) ||
                    (playerjson.has(realname) && !playerjson.getBoolean(realname)))) {
                    int cost = (int) ConfigUtils.getItemValue(name, level, "cost");
                    lore.add(itemsConfig.getString("text.locked1").replace("%cost%", cost + ""));
                    lore.add(itemsConfig.getString("text.locked2").replace("%cost%", cost + ""));
                    if (intangible) {
                        item.setType(Material.getMaterial(itemsConfig.getString("intangibles.locked")));
                    }
                } else {
                    // Possibility of upgrading
                    if (level < getMaxLevel(name)) {
                        int spCost = (int) ConfigUtils.getItemValue(name, level + 1, "spcost");
                        lore.add(itemsConfig.getString("text.upgradable").replace("%spcost%", spCost + ""));
                    }
                    // Possibility of downgrading
                    if (getMinLevel(name, deckjson) < level) {
                        int spCost = (int) ConfigUtils.getItemValue(name, level, "spcost");
                        lore.add(itemsConfig.getString("text.downgradable").replace("%spcost%", spCost + ""));
                        if (intangible) {
                            item.setType(Material.getMaterial(itemsConfig.getString("intangibles.selected")));
                        }
                    }
                    
                    lore.add(itemsConfig.getString("text.currentsp").replace("%sp%", deckjson.getJSONObject(preset).getInt("skillpoints") + ""));
                }
            }
            itemMeta.lore(ConfigUtils.toComponent(lore));
        }
        
        // Inject NBT data
        String id = "structure";
        if (deck != null) {
            id = "gui";
        } else if (ConfigUtils.getItemValue(name, level, "file") == null) {
            id = "utility";
        }
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "item-" + id),
                PersistentDataType.STRING, name + "-" + level);
        
        // Setup extra item attributes for specific things
        if (name.equals("splash")) {
            PotionMeta pmeta = (PotionMeta) itemMeta;
            PotionData pdata = new PotionData(PotionType.WATER);
            pmeta.setBasePotionData(pdata);
        } else if (name.equals("lingering_harming")) {
            int amplifier = (int) ConfigUtils.getItemValue(name, level, "amplifier");
            int duration = (int) ConfigUtils.getItemValue(name, level, "duration");
            PotionMeta pmeta = (PotionMeta) itemMeta;
            pmeta.addCustomEffect(new PotionEffect(PotionEffectType.HARM, duration, amplifier), true);
            pmeta.setColor(Color.PURPLE);
        } else if (name.equals("torpedo")) {
            itemMeta.addEnchant(Enchantment.DURABILITY, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(itemMeta);
        return item;
    }
    
    /**
     * Gets the max level of an item
     * 
     * @param name
     * @return
     */
    public int getMaxLevel(String name) {
        Set<String> keys = itemsConfig.getConfigurationSection(name).getKeys(false);
        int result = 0;
        for (String key : keys) {
            try {
                int i = Integer.parseInt(key);
                if (i > result) {
                    result = i;
                }
            } catch (NumberFormatException e) {
                continue;
            }
        }
        return result;
    }
    
    /**
     * Get min level of an item
     * 
     * @param name
     * @param json
     * @return
     */
    public int getMinLevel(String name, JSONObject deckjson) {
        JSONObject pjson = deckjson.getJSONObject("A");
        if (pjson.getJSONObject("missiles").has(name) ||
            pjson.getJSONObject("utility").has(name)) {
            return 1;
        }
        return 0;
    }
    
    /**
     * Capitalizes enchantment names
     * 
     * @param ench
     */
    private String getEnchName(String ench) {
        String[] temp = ench.split("_");
        for (int i = 0; i < temp.length; i++) {
            temp[i] = StringUtils.capitalize(temp[i]);
        }
        return String.join(" ", temp);
    }
}
