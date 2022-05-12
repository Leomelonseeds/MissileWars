package io.github.vhorvath2010.missilewars.decks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

/** A class to manage Deck generation and selection. */
public class DeckManager {

    private final Deck vanguard;
    private final Deck sentinel;
    private final Deck berserker;
    private final Deck architect;
    
    private final Map<String, ItemStack> items;

    /** Set up the DeckManager with loaded decks. */
    public DeckManager() {
        
        // Setup gear items

        // Vanguard
        List<ItemStack> vanguardgear = new ArrayList<>();
        ItemStack vsword = createUtilityItem("vanguard_sword");
        vsword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 3);
        vsword.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
        ItemMeta swordMeta = vsword.getItemMeta();
        swordMeta.setUnbreakable(true);
        vsword.setItemMeta(swordMeta);
        vanguardgear.add(vsword);
        ItemStack vboots = new ItemStack(Material.GOLDEN_BOOTS);
        vboots.addUnsafeEnchantment(Enchantment.PROTECTION_FIRE, 2);
        ItemMeta vbootsMeta = vboots.getItemMeta();
        vbootsMeta.setUnbreakable(true);
        vboots.setItemMeta(vbootsMeta);
        vanguardgear.add(vboots);

        ItemStack hurricane = createSchematicItem("hurricane");
        ItemStack cruiser = createSchematicItem("cruiser");
        ItemStack thunderbolt = createSchematicItem("thunderbolt");
        ItemStack hilt = createSchematicItem("hilt");
        ItemStack dagger = createSchematicItem("dagger");
        ItemStack supersonic = createSchematicItem("supersonic");
        ItemStack hypersonic = createSchematicItem("hypersonic");
        ItemStack tomahawk = createSchematicItem("tomahawk");
        ItemStack tomatwo = createSchematicItem("tomatwo");

        ItemStack splash = createUtilityItem("splash");
        ItemStack canopy = createUtilityItem("canopy");
        ItemStack lingering = createUtilityItem("lingering_harming_2");

        List<ItemStack> vMissiles = new ArrayList<>(List.of(new ItemStack[]{tomahawk, thunderbolt, supersonic, dagger, tomatwo}));
        List<ItemStack> vUtility = new ArrayList<>(List.of(new ItemStack[]{splash, canopy, lingering}));
        vanguard = new Deck("Vanguard", vanguardgear, vMissiles, vUtility);


        // Sentinel
        List<ItemStack> sentinelgear = new ArrayList<>();
        ItemStack bow = createUtilityItem("sentinel_bow");
        bow.addUnsafeEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
        bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        bow.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);
        bow.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 4);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.setUnbreakable(true);
        bow.setItemMeta(bowMeta);
        sentinelgear.add(bow);
        ItemStack sboots = new ItemStack(Material.IRON_BOOTS);
        sboots.addUnsafeEnchantment(Enchantment.PROTECTION_PROJECTILE, 2);
        ItemMeta sbootsMeta = sboots.getItemMeta();
        sbootsMeta.setUnbreakable(true);
        sboots.setItemMeta(sbootsMeta);
        sentinelgear.add(sboots);

        ItemStack guardian = createSchematicItem("guardian");
        ItemStack elderguardian = createSchematicItem("elder_guardian");
        ItemStack gemini = createSchematicItem("gemini");
        ItemStack gemini_w = createSchematicItem("gemini_warrior");
        ItemStack chron = createSchematicItem("cronullifier");
        ItemStack aeon = createSchematicItem("aeonullifier");
        ItemStack sword = createSchematicItem("sword");
        ItemStack blade = createSchematicItem("blade");
        ItemStack piranha = createSchematicItem("piranha");
        ItemStack anglerfish = createSchematicItem("anglerfish");

        ItemStack torpedo = createSchematicItem("torpedo_2");
        ItemStack obsidianshield = createSchematicItem("obsidian_shield");
        ItemStack sentinelarrows = new ItemStack(Material.ARROW, 3);

        List<ItemStack> sMissiles = new ArrayList<>(List.of(new ItemStack[]{guardian, gemini_w, aeon, sword, piranha}));
        List<ItemStack> sUtility = new ArrayList<>(List.of(new ItemStack[] {torpedo, obsidianshield, sentinelarrows}));
        sentinel = new Deck("Sentinel", sentinelgear, sMissiles, sUtility);


        // Berserker
        List<ItemStack> berserkergear = new ArrayList<>();
        ItemStack crossbow = createUtilityItem("berserker_crossbow");
        crossbow.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 2);
        crossbow.addUnsafeEnchantment(Enchantment.MULTISHOT, 1);
        crossbow.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 7);
        ItemMeta crossbowMeta = crossbow.getItemMeta();
        crossbowMeta.setUnbreakable(true);
        crossbow.setItemMeta(crossbowMeta);
        berserkergear.add(crossbow);
        ItemStack bboots = new ItemStack(Material.DIAMOND_BOOTS);
        bboots.addUnsafeEnchantment(Enchantment.PROTECTION_EXPLOSIONS, 3);
        ItemMeta bbootsMeta = bboots.getItemMeta();
        bbootsMeta.setUnbreakable(true);
        bboots.setItemMeta(bbootsMeta);
        berserkergear.add(bboots);

        ItemStack supporter = createSchematicItem("supporter");
        ItemStack auxiliary = createSchematicItem("auxiliary");
        ItemStack warhead = createSchematicItem("warhead");
        ItemStack bullet = createSchematicItem("bullet");
        ItemStack juggernaut = createSchematicItem("juggernaut");
        ItemStack meganaut = createSchematicItem("meganaut");
        ItemStack breaker = createSchematicItem("breaker");
        ItemStack rifter = createSchematicItem("rifter");
        ItemStack buster = createSchematicItem("shieldbuster");
        ItemStack shark = createSchematicItem("shark");

        ItemStack fireball = createUtilityItem("fireball");
        ItemStack creeper = createUtilityItem("spawn_creeper");
        ItemStack berserkerarrows = new ItemStack(Material.ARROW, 3);

        List<ItemStack> bMissiles = new ArrayList<>(List.of(new ItemStack[]{supporter, bullet, juggernaut, breaker, shark}));
        List<ItemStack> bUtility = new ArrayList<>(List.of(new ItemStack[]{fireball, creeper, berserkerarrows}));
        berserker = new Deck("Berserker", berserkergear, bMissiles, bUtility);


        // Architect
        List<ItemStack> architectgear = new ArrayList<>();
        ItemStack pickaxe = createUtilityItem("architect_pickaxe");
        pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
        pickaxe.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
        ItemMeta pickaxeMeta = pickaxe.getItemMeta();
        pickaxeMeta.setUnbreakable(true);
        pickaxe.setItemMeta(pickaxeMeta);
        architectgear.add(pickaxe);
        ItemStack aboots = new ItemStack(Material.CHAINMAIL_BOOTS);
        aboots.addUnsafeEnchantment(Enchantment.PROTECTION_FALL, 1);
        ItemMeta abootsMeta = aboots.getItemMeta();
        abootsMeta.setUnbreakable(true);
        aboots.setItemMeta(abootsMeta);
        architectgear.add(aboots);

        ItemStack shipper = createSchematicItem("shipper");
        ItemStack slasher = createSchematicItem("slasher");
        ItemStack slingshot = createSchematicItem("slingshot");
        ItemStack catapult = createSchematicItem("catapult");
        ItemStack fortress = createSchematicItem("fortress");
        ItemStack citadel = createSchematicItem("citadel");
        ItemStack pisces = createSchematicItem("pisces");
        ItemStack aries = createSchematicItem("aries");
        ItemStack ant = createSchematicItem("ant");
        ItemStack lifter = createSchematicItem("lifter");

        ItemStack shield = createSchematicItem("shield_2");
        ItemStack platform = createSchematicItem("platform_2");
        ItemStack leaves = createUtilityItem("leaves");

        List<ItemStack> aMissiles = new ArrayList<>(List.of(new ItemStack[]{slasher, slingshot, fortress, aries, lifter}));
        List<ItemStack> aUtility = new ArrayList<>(List.of(new ItemStack[]{shield, platform, leaves}));
        architect = new Deck("Architect", architectgear, aMissiles, aUtility);
        

        
        items = new HashMap<>();
        
        // Vanguard things
        items.put("sword", createUtilityItem("vanguard_sword"));
        items.put("vboots", new ItemStack(Material.GOLDEN_BOOTS));
        
        items.put("tomahawk_1", createSchematicItem("tomahawk"));
        items.put("tomahawk_2", createSchematicItem("hurricane"));
        items.put("cruiser_1", createSchematicItem("cruiser"));
        items.put("cruiser_2", createSchematicItem("thunderbolt"));
        items.put("hilt_1", createSchematicItem("hilt"));
        items.put("hilt_2", createSchematicItem("dagger"));
        items.put("supersonic_1", createSchematicItem("supersonic"));
        items.put("supersonic_2", createSchematicItem("hypersonic"));
        items.put("tomahawk_1", createSchematicItem("tomahawk"));
        items.put("tomahawk_2", createSchematicItem("tomatwo"));

        items.put("splash", createSchematicItem("splash"));
        items.put("canopy", createSchematicItem("canopy"));
        items.put("lingering_harming_1", createUtilityItem("lingering_harming_1"));
        items.put("lingering_harming_2", createUtilityItem("lingering_harming_2"));
        items.put("lingering_harming_3", createUtilityItem("lingering_harming_3"));
        
        // Sentinel things
        items.put("bow", createUtilityItem("sentinel_bow"));
        items.put("sboots", new ItemStack(Material.IRON_BOOTS));
        
        items.put("guardian_1", createSchematicItem("guardian"));
        items.put("guardian_2", createSchematicItem("elder_guardian"));
        items.put("gemini_1", createSchematicItem("gemini"));
        items.put("gemini_2", createSchematicItem("gemini_warrior"));
        items.put("cron_1", createSchematicItem("cronullifier"));
        items.put("cron_2", createSchematicItem("aeonullifier"));
        items.put("sword_1", createSchematicItem("sword"));
        items.put("sword_2", createSchematicItem("blade"));
        items.put("piranha_1", createSchematicItem("piranha"));
        items.put("piranha_2", createSchematicItem("anglerfish"));
        
        items.put("torpedo_1", createSchematicItem("torpedo_1"));
        items.put("torpedo_2", createSchematicItem("torpedo_2"));
        items.put("torpedo_3", createSchematicItem("torpedo_3"));
        items.put("oshield", createSchematicItem("obsidian_shield"));
        items.put("arrows_1", new ItemStack(Material.ARROW, 2));
        items.put("arrows_2", new ItemStack(Material.ARROW, 3));
        items.put("arrows_3", new ItemStack(Material.ARROW, 4));
        
        // Berserker things
        items.put("crossbow", createUtilityItem("berserker_crossbow"));
        items.put("bboots", new ItemStack(Material.DIAMOND_BOOTS));
        
        items.put("supporter_1", createSchematicItem("supporter"));
        items.put("supporter_2", createSchematicItem("auxiliary"));
        items.put("warhead_1", createSchematicItem("warhead"));
        items.put("warhead_2", createSchematicItem("bullet"));
        items.put("juggernaut_1", createSchematicItem("juggernaut"));
        items.put("juggernaut_2", createSchematicItem("meganaut"));
        items.put("breaker_1", createSchematicItem("breaker"));
        items.put("breaker_2", createSchematicItem("rifter"));
        items.put("shieldbuster_1", createSchematicItem("shieldbuster"));
        items.put("shieldbuster_2", createSchematicItem("shark"));

        items.put("fireball", createUtilityItem("fireball"));
        items.put("creeper", createUtilityItem("spawn_creeper"));
        items.put("charged_creeper", createUtilityItem("spawn_creeper_charged"));
        
        // Architect things
        items.put("pickaxe", createUtilityItem("architect_pickaxe"));
        items.put("aboots", new ItemStack(Material.CHAINMAIL_BOOTS));
        
        items.put("shipper_1", createSchematicItem("shipper"));
        items.put("shipper_2", createSchematicItem("slasher"));
        items.put("slingshot_1", createSchematicItem("slingshot"));
        items.put("slingshot_2", createSchematicItem("catapult"));
        items.put("fortress_1", createSchematicItem("fortress"));
        items.put("fortress_2", createSchematicItem("citadel"));
        items.put("pisces_1", createSchematicItem("pisces"));
        items.put("pisces_2", createSchematicItem("aries"));
        items.put("ant_1", createSchematicItem("ant"));
        items.put("ant_2", createSchematicItem("lifter"));
        
        items.put("shield_1", createSchematicItem("shield_1"));
        items.put("shield_2", createSchematicItem("shield_2"));
        items.put("shield_3", createSchematicItem("shield_3"));
        items.put("platform_1", createSchematicItem("platform_1"));
        items.put("platform_2", createSchematicItem("platform_2"));
        items.put("platform_3", createSchematicItem("platform_3"));
        items.put("leaves_1", createUtilityItem("leaves_1"));
        items.put("leaves_2", createUtilityItem("leaves_2"));
        items.put("leaves_3", createUtilityItem("leaves_3"));
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
        switch (deck) {
        case "Vanguard": 
        {
            ArrayList<ItemStack> missiles = new ArrayList<>();
            
            missiles.add(items.get("lightning_" + json.getInt("lightning")));
            missiles.add(items.get("cruiser_" + json.getInt("cruiser")));
            missiles.add(items.get("hilt_" + json.getInt("hilt")));
            missiles.add(items.get("supersonic_" + json.getInt("supersonic")));
            missiles.add(items.get("tomahawk_" + json.getInt("tomahawk")));
            
            ArrayList<ItemStack> utility = new ArrayList<>();
            utility.add(addLevel("canopy", json));
            utility.add(addLevel("splash", json));
            utility.add(items.get("lingering_harming_" + json.getInt("harming")));
            
            ArrayList<ItemStack> gear = new ArrayList<>();
            ItemStack sword = items.get("sword");
            addEnch(sword, Enchantment.DAMAGE_ALL, json.getInt("sharp") * 2 - 1);
            addEnch(sword, Enchantment.KNOCKBACK, json.getInt("knockback"));
            addEnch(sword, Enchantment.FIRE_ASPECT, json.getInt("fireaspect"));
            gear.add(sword);
            ItemStack boots = items.get("vboots");
            addEnch(boots, Enchantment.PROTECTION_FIRE, json.getInt("fireprot") * 2);
            gear.add(boots);
            
            return new Deck(deck, gear, missiles, utility);
        }
        case "Berserker":
        {
            ArrayList<ItemStack> missiles = new ArrayList<>();
            
            missiles.add(items.get("supporter_" + json.getInt("supporter")));
            missiles.add(items.get("warhead_" + json.getInt("warhead")));
            missiles.add(items.get("juggernaut_" + json.getInt("juggernaut")));
            missiles.add(items.get("breaker_" + json.getInt("breaker")));
            missiles.add(items.get("shieldbuster_" + json.getInt("shieldbuster")));
            
            ArrayList<ItemStack> utility = new ArrayList<>();
            utility.add(addLevel("fireball", json));
            if (json.getInt("creeper") == 1) {
                utility.add(items.get("creeper"));
            } else {
                utility.add(items.get("charged_creeper"));
            }
            utility.add(items.get("arrows_" + json.getInt("arrows")));
            
            ArrayList<ItemStack> gear = new ArrayList<>();
            ItemStack crossbow = items.get("crossbow");
            addEnch(crossbow, Enchantment.DAMAGE_ALL, json.getInt("sharp") == 0 ? 0 : json.getInt("sharp") * 2 + 3);
            addEnch(crossbow, Enchantment.MULTISHOT, json.getInt("multishot"));
            addEnch(crossbow, Enchantment.QUICK_CHARGE, json.getInt("quickcharge"));
            gear.add(crossbow);
            ItemStack boots = items.get("bboots");
            addEnch(boots, Enchantment.PROTECTION_EXPLOSIONS, json.getInt("blastprot") * 3);
            gear.add(boots);
            
            return new Deck(deck, gear, missiles, utility);
        }
        case "Sentinel":
        {
            ArrayList<ItemStack> missiles = new ArrayList<>();
            
            missiles.add(items.get("piranha_" + json.getInt("piranha")));
            missiles.add(items.get("guardian_" + json.getInt("guardian")));
            missiles.add(items.get("cron_" + json.getInt("cron")));
            missiles.add(items.get("gemini_" + json.getInt("gemini")));
            missiles.add(items.get("sword_" + json.getInt("sword")));
            
            ArrayList<ItemStack> utility = new ArrayList<>();
            utility.add(addLevel("oshield", json));
            utility.add(items.get("torpedo_" + json.getInt("torpedo")));
            utility.add(items.get("arrows_" + json.getInt("arrows")));
            
            ArrayList<ItemStack> gear = new ArrayList<>();
            ItemStack bow = items.get("bow");
            addEnch(bow, Enchantment.DAMAGE_ALL, json.getInt("sharp") == 0 ? 0 : json.getInt("sharp") * 2 + 1);
            addEnch(bow, Enchantment.ARROW_DAMAGE, json.getInt("power"));
            addEnch(bow, Enchantment.ARROW_FIRE, json.getInt("flame"));
            addEnch(bow, Enchantment.ARROW_KNOCKBACK, json.getInt("punch"));
            gear.add(bow);
            ItemStack boots = items.get("sboots");
            addEnch(boots, Enchantment.PROTECTION_PROJECTILE, json.getInt("projprot") * 2);
            gear.add(boots);
            
            return new Deck(deck, gear, missiles, utility);
        }
        case "Architect":
        {
            ArrayList<ItemStack> missiles = new ArrayList<>();
            
            missiles.add(items.get("ant_" + json.getInt("ant")));
            missiles.add(items.get("shipper_" + json.getInt("shipper")));
            missiles.add(items.get("slingshot_" + json.getInt("slingshot")));
            missiles.add(items.get("citadel_" + json.getInt("citadel")));
            missiles.add(items.get("pisces_" + json.getInt("pisces")));
            
            ArrayList<ItemStack> utility = new ArrayList<>();
            utility.add(items.get("leaves_" + json.getInt("leaves")));
            utility.add(items.get("shield_" + json.getInt("shield")));
            utility.add(items.get("platform_" + json.getInt("platform")));
            
            ArrayList<ItemStack> gear = new ArrayList<>();
            ItemStack pick = items.get("pickaxe");
            addEnch(pick, Enchantment.DAMAGE_ALL, json.getInt("sharp"));
            addEnch(pick, Enchantment.DIG_SPEED, json.getInt("efficiency"));
            gear.add(pick);
            ItemStack boots = items.get("aboots");
            addEnch(boots, Enchantment.PROTECTION_FALL, json.getInt("featherfalling"));
            gear.add(boots);
            
            return new Deck(deck, gear, missiles, utility);
        }
        }
        
        return null;
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
     * Change %level% parameter of an itemstack
     * 
     * @param item
     * @return
     */
    private ItemStack addLevel(String itemname, JSONObject json) {
        ItemStack item = items.get(itemname);
        int itemlevel = json.getInt(itemname);
        ItemMeta itemmeta = item.getItemMeta();
        itemmeta.setDisplayName(itemmeta.getDisplayName().replace("%level%", roman(itemlevel)));
        item.setItemMeta(itemmeta);
        return item;
    }
    
    /**
     * Translate integer to roman, only for first 5 numbers lol
     * 
     * @param i
     * @return
     */
    private String roman(int i) {
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
        }
        return null;
    }

    /**
     * Obtain and return the various decks
     *
     * @return the decks
     */
    public Deck getDeck(String name) {
        switch (name) {
        case "Vanguard":
            return vanguard;
        case "Sentinel":
            return sentinel;
        case "Berserker":
            return berserker;
        case "Architect":
            return architect;
        }
        return null;
    }

    /**
     * Create an ItemStack that spawns the given schematic on use.
     *
     * @param schematicName the name of the schematic
     * @return an ItemStack that spawns the schematic on use
     */
    private ItemStack createSchematicItem(String schematicName) {
        // Load item data from config
        FileConfiguration itemsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "items.yml");

        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial(itemsConfig.getString(schematicName + ".item")));
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                itemsConfig.getString(schematicName + ".name")));
        List<String> lore = new ArrayList<>();
        for (String loreString : itemsConfig.getStringList(schematicName + ".lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', loreString));
        }
        itemMeta.setLore(lore);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"),
                PersistentDataType.STRING, schematicName);
        item.setItemMeta(itemMeta);
        return item;
    }

    /**
     * Create an ItemStack for a utility item with no schematic.
     *
     * @param type the type of utility
     * @return an ItemStack that spawns the schematic on use
     */
    private ItemStack createUtilityItem(String type) {
        // Load item data from config
        FileConfiguration itemsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "items.yml");

        // Setup item, with janky addition for special case of leaves
        ItemStack item = new ItemStack(Material.getMaterial(itemsConfig.getString(type + ".item")), 
                type.contains("leaves") ? itemsConfig.getInt(type + ".amount") : 1);
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                itemsConfig.getString(type + ".name")));
        List<String> lore = new ArrayList<>();
        for (String loreString : itemsConfig.getStringList(type + ".lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', loreString));
        }
        itemMeta.setLore(lore);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-utility"),
                PersistentDataType.STRING, type);
        if (type.equals("splash")) {
            PotionMeta pmeta = (PotionMeta) itemMeta;
            PotionData pdata = new PotionData(PotionType.WATER);
            pmeta.setBasePotionData(pdata);
            itemMeta = pmeta;
        } else if (type.contains("lingering")) {
            int amplifier = itemsConfig.getInt(type + ".amplifier");
            int duration = itemsConfig.getInt(type + ".duration");
            PotionMeta pmeta = (PotionMeta) itemMeta;
            pmeta.addCustomEffect(new PotionEffect(PotionEffectType.HARM, duration, amplifier), true);
            pmeta.setColor(Color.PURPLE);
            itemMeta = pmeta;
        }
        item.setItemMeta(itemMeta);
        return item;
    }

}
