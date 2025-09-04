package com.leomelonseeds.missilewars.decks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.decks.Ability.Stat;
import com.leomelonseeds.missilewars.decks.Ability.Type;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

/** A class to manage Deck generation and selection. */
public class DeckManager {
    
    private MissileWarsPlugin plugin;
    private final List<String> presets;
    private FileConfiguration itemsConfig;

    /** Set up the DeckManager with loaded decks. */
    public DeckManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.presets = new ArrayList<>(List.of(new String[]{"A", "B", "C"}));
        this.itemsConfig = ConfigUtils.getConfigFile("items.yml");
    }
    
    /**
     * Refresh item configs
     */
    public void reload() {
        itemsConfig = ConfigUtils.getConfigFile("items.yml");
    }
    
    /**
     * Gets a deck for a player
     * 
     * @param json
     * @param deck
     * @return
     */
    public Deck getPlayerDeck(MissileWarsPlayer mwp) {
        UUID uuid = mwp.getMCPlayerId();
        JSONObject basejson = plugin.getJSON().getPlayer(uuid);
        String deck = basejson.getString("Deck");
        JSONObject json = plugin.getJSON().getPlayerPreset(uuid);
        List<DeckItem> pool = Arrays.asList(new DeckItem[8]);
        List<ItemStack> gear = new ArrayList<>();
        
        // Figure out utility and missile multipliers
        Pair<Ability, Integer> jsonPassive = plugin.getJSON().getPassive(json, Type.GPASSIVE);
        Ability gpassive = jsonPassive.getLeft();
        int glevel = jsonPassive.getRight();
        double mmult = 1;
        double umult = 1;
        double maxmult = 1;
        if (glevel > 0) {
            if (gpassive != Ability.HOARDER) {
                double mperc = ConfigUtils.getAbilityStat(gpassive, glevel, Stat.MPERCENTAGE) / 100;
                double uperc = ConfigUtils.getAbilityStat(gpassive, glevel, Stat.UPERCENTAGE) / 100;
                if (gpassive == Ability.MISSILE_SPEC) {
                    mmult = 1 - mperc;
                    umult = uperc + 1;
                } else {
                    mmult = mperc + 1;
                    umult = 1 - uperc;
                }
            } else {
                double perc = ConfigUtils.getAbilityStat(gpassive, glevel, Stat.PERCENTAGE) / 100;
                mmult = perc + 1;
                umult = perc + 1;
                maxmult = ConfigUtils.getAbilityStat(gpassive, glevel, Stat.MAX);
            }
        }
        
        // Create pool items
        for (String s : new String[] {"missiles", "utility"}) {
            for (String key : json.getJSONObject(s).keySet()) {
                int level = json.getJSONObject(s).getInt(key);
                boolean isMissile = s.equals("missiles");
                ItemStack i = createItem(key, level, isMissile);
                i.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                
                // Set item limit. When using Gunslinger, max arrows is reduced by 1
                int max = (int) ((int) ConfigUtils.getItemValue(key, level, "max") * maxmult);
                if (i.getType() == Material.ARROW && plugin.getJSON().getLevel(json, Ability.GUNSLINGER) > 0) {
                    max = Math.max(max - 1, 1);
                }
                
                // Finalize item
                int cd = (int) ((int) ConfigUtils.getItemValue(key, level, "cooldown") * (isMissile ? mmult : umult));
                pool.set(itemsConfig.getInt(key + ".index"), new DeckItem(i, cd, max, mwp));
            }
        }
        
        // Create gear items
        DeckStorage ds = DeckStorage.fromString(deck);
        ItemStack weapon = createItem(deck + ".weapon", 0, false);
        for (Entry<String, Enchantment> e : ds.getWeaponEnchants().entrySet()) {
            addEnch(weapon, e.getKey(), e.getValue(), deck, json);
        }
        
        // For custom enchantments, remove unbreaking if the item has more than 2 enchantments
        if (weapon.getEnchantments().size() >= 2) {
            weapon.removeEnchantment(Enchantment.UNBREAKING);
        }
        gear.add(weapon);
        
        ItemStack boots = ds.getBoots();
        for (Entry<String, Enchantment> e : ds.getBootEnchants().entrySet()) {
            addEnch(boots, e.getKey(), e.getValue(), deck, json);
        }
        gear.add(boots);
        
        // Make gear items unbreakable
        for (ItemStack item : gear) {
            ItemMeta meta = item.getItemMeta();
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        
        return new Deck(ds, gear, pool);
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
    private void addEnch(ItemStack item, String enchID, Enchantment ench, String deck, JSONObject json) {
        // Swift sneak is added later
        if (ench == Enchantment.SWIFT_SNEAK) {
            return;
        }
        
        int lvl = itemsConfig.getInt(deck + ".enchants." + enchID + "." + 
                json.getJSONObject("enchants").getInt(enchID) + ".level");
        if (lvl <= 0) {
            return;
        }
        
        // Add custom effects
        String custom = null;
        if (ench == Enchantment.BLAST_PROTECTION && plugin.getJSON().getLevel(json, Ability.ROCKETEER) > 0) {
            custom = "Blast Protection";
        } else if (ench == Enchantment.SHARPNESS && (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW)) {
            custom = "Sharpness";
            
            // Newer versions of MC have changed how bows works with sharpness, the arrow
            // damage now scales according to the sharpness level of the bow. To combat this,
            // add sharpness as a custom enchant and adjust the attributes ourselves
            ItemMeta meta = item.getItemMeta();
            double extraDmg = 0.5 * lvl + 0.5;
            NamespacedKey key = new NamespacedKey(MissileWarsPlugin.getPlugin(), "umw-sharpness");
            AttributeModifier at = new AttributeModifier(key, extraDmg, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, at);
            item.setItemMeta(meta);
        }
        
        if (custom != null) {
            ItemMeta meta = item.getItemMeta();
            List<Component> newLore = new ArrayList<>();
            newLore.add(ConfigUtils.toComponent("&7" + custom + " " + roman(lvl)));
            if (meta.hasLore()) {
                List<Component> loreLines = meta.lore();
                for (Component c : loreLines) {
                    newLore.add(c);
                }
            }
            meta.lore(newLore);
            item.setItemMeta(meta);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, lvl); // To add glow
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
     * Same as createItem, but json is default null. Use for
     * creating a poolitem for in-game use
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
                    displayName = itemsConfig.getString("enchants.name").replace("%enchant%", getEnchName(realname));
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
            
            if (!intangible && level > 0) {
                lore.addAll(itemsConfig.getStringList("text.itemstats"));
            }
            
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
                        item = item.withType(Material.getMaterial(itemsConfig.getString("intangibles.locked")));
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
                            item = item.withType(Material.getMaterial(itemsConfig.getString("intangibles.selected")));
                            InventoryUtils.addGlow(itemMeta);
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
            pmeta.setBasePotionType(PotionType.WATER);
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
