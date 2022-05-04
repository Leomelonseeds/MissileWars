package io.github.vhorvath2010.missilewars.decks;

import java.util.ArrayList;
import java.util.List;

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

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

/** A class to manage Deck generation and selection. */
public class DeckManager {

    /** The default deck for players. */
    private final Deck defaultDeck;

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

        ItemStack lightning = createSchematicItem("lightning");
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
        ItemStack lingering = createSchematicItem("lingering_harming_1");
        
        
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

        ItemStack guardian = createSchematicItem("guardian");
        ItemStack elderguardian = createSchematicItem("elder_guardian");
        ItemStack gemini = createSchematicItem("gemini");
        ItemStack gemini_w = createSchematicItem("gemini_warrior");
        ItemStack chron = createSchematicItem("chronullifier");
        ItemStack aeon = createSchematicItem("aeonullifier");
        ItemStack sword = createSchematicItem("sword");
        ItemStack blade = createSchematicItem("blade");
        ItemStack piranha = createSchematicItem("piranha");
        ItemStack anglerfish = createSchematicItem("anglerfish");

        ItemStack torpedo = createSchematicItem("torpedo_2");
        ItemStack obsidianshield = createSchematicItem("obsidian_shield");
        ItemStack sentinelarrows = new ItemStack(Material.ARROW, 3);

        
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

        
        // Architect
        List<ItemStack> architectgear = new ArrayList<>();
        ItemStack pickaxe = createUtilityItem("architect_pickaxe");
        pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
        pickaxe.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
        ItemMeta pickaxeMeta = pickaxe.getItemMeta();
        pickaxeMeta.setUnbreakable(true);
        pickaxe.setItemMeta(pickaxeMeta);
        architectgear.add(pickaxe);
        
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
        
        List<ItemStack> pool = new ArrayList<>(List.of(new ItemStack[]{sentinelarrows, ant, piranha, thunderbolt, meganaut,
                tomatwo, bullet, gemini, slingshot, blade, torpedo, fireball, splash, canopy, creeper}));

        defaultDeck = new Deck("Default", berserkergear, pool);
    }

    /**
     * Obtain and return the default deck.
     *
     * @return the default deck
     */
    public Deck getDefaultDeck() {
        return defaultDeck;
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

        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial(itemsConfig.getString(type + ".item")));
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
        } else if (type.contains("lingering")) {
            PotionMeta pmeta = (PotionMeta) itemMeta;
            pmeta.addCustomEffect(new PotionEffect(PotionEffectType.HARM, 10, 1), true);
            pmeta.setColor(Color.PURPLE);
        }
        item.setItemMeta(itemMeta);
        return item;
    }

}
