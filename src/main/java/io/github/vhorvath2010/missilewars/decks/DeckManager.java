package io.github.vhorvath2010.missilewars.decks;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
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
        List<ItemStack> gear = new ArrayList<>();
        /*ItemStack bow = createUtilityItem("sentinel_bow");
        bow.addUnsafeEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
        bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        bow.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);
        bow.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 4);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.setUnbreakable(true);
        bow.setItemMeta(bowMeta);
        gear.add(bow);*/
        
        ItemStack bow = createUtilityItem("berserker_crossbow");
        bow.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 2);
        bow.addUnsafeEnchantment(Enchantment.MULTISHOT, 1);
        bow.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 7);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.setUnbreakable(true);
        bow.setItemMeta(bowMeta);
        gear.add(bow);

        // Setup pool items
        ItemStack arrows = new ItemStack(Material.ARROW, 3);
        ItemStack guardian = createSchematicItem("guardian");
        ItemStack elderguardian = createSchematicItem("elder_guardian");
        ItemStack thunderbolt = createSchematicItem("thunderbolt");
        ItemStack slingshot = createSchematicItem("slingshot");
        ItemStack tomatwo = createSchematicItem("tomatwo");
        ItemStack ant = createSchematicItem("ant");
        ItemStack piranha = createSchematicItem("piranha");
        ItemStack meganaut = createSchematicItem("meganaut");
        ItemStack auxiliary = createSchematicItem("auxiliary");
        ItemStack bullet = createSchematicItem("bullet");
        ItemStack dagger = createSchematicItem("dagger");
        ItemStack shark = createSchematicItem("shark");
        ItemStack buster = createSchematicItem("shieldbuster");
        ItemStack pisces = createSchematicItem("pisces");
        ItemStack tomahawk = createSchematicItem("tomahawk");
        ItemStack gemini_w = createSchematicItem("gemini_warrior");
        ItemStack gemini = createSchematicItem("gemini");
        ItemStack cruiser = createSchematicItem("cruiser");
        ItemStack juggernaut = createSchematicItem("juggernaut");
        ItemStack lightning = createSchematicItem("lightning");
        ItemStack sword = createSchematicItem("sword");
        ItemStack blade = createSchematicItem("blade");
        ItemStack shield = createSchematicItem("shield_2");
        ItemStack platform = createSchematicItem("platform_2");
        ItemStack torpedo = createSchematicItem("torpedo_1");
        ItemStack obsidianshield = createSchematicItem("obsidian_shield");
        ItemStack fireball = createUtilityItem("fireball");
        ItemStack splash = createUtilityItem("splash");
        ItemStack canopy = createUtilityItem("canopy");
        ItemStack creeper = createUtilityItem("spawn_creeper");
        List<ItemStack> pool = new ArrayList<>(List.of(new ItemStack[]{arrows, ant, piranha, thunderbolt, meganaut,
                tomatwo, bullet, gemini, slingshot, blade, torpedo, fireball, splash, canopy, creeper}));

        defaultDeck = new Deck("Default", gear, pool);
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
        }
        item.setItemMeta(itemMeta);
        return item;
    }

}
